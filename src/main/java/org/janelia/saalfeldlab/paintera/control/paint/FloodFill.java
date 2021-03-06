package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.state.HasFloodFillState;
import org.janelia.saalfeldlab.paintera.state.HasFloodFillState.FloodFillState;
import org.janelia.saalfeldlab.paintera.state.HasFragmentSegmentAssignments;
import org.janelia.saalfeldlab.paintera.state.HasMaskForLabel;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessible;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class FloodFill
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Runnable requestRepaint;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private static final class ForegroundCheck implements Predicate<UnsignedLongType>
	{

		@Override
		public boolean test(final UnsignedLongType t)
		{
			return t.getIntegerLong() == 1;
		}

	}

	private static final ForegroundCheck FOREGROUND_CHECK = new ForegroundCheck();

	public FloodFill(final ViewerPanelFX viewer, final SourceInfo sourceInfo, final Runnable requestRepaint)
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.requestRepaint = requestRepaint;
		viewer.addTransformListener(t -> viewerTransform.set(t));
	}

	public void fillAt(final double x, final double y, final Supplier<Long> fillSupplier)
	{
		if (sourceInfo.currentSourceProperty().get() == null)
		{
			LOG.info("No current source selected -- will not fill");
			return;
		}
		final Long fill = fillSupplier.get();
		if (fill == null)
		{
			LOG.info("Received invalid label {} -- will not fill.", fill);
			return;
		}
		fillAt(x, y, fill);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void fillAt(final double x, final double y, final long fill)
	{
		final Source<?>   currentSource = sourceInfo.currentSourceProperty().get();
		final ViewerState viewerState   = viewer.getState();
		if (currentSource == null)
		{
			LOG.info("No current source selected -- will not fill");
			return;
		}

		final SourceState<?, ?> currentSourceState = sourceInfo.getState(currentSource);

		if (!(currentSourceState instanceof HasMaskForLabel<?>))
		{
			LOG.info("Selected source cannot provide mask for label -- will not fill");
			return;
		}

		final HasMaskForLabel<?> hasMaskForLabel = (HasMaskForLabel<?>) currentSourceState;

		if (!currentSourceState.isVisibleProperty().get())
		{
			LOG.info("Selected source is not visible -- will not fill");
			return;
		}

		if (!(currentSource instanceof MaskedSource<?, ?>))
		{
			LOG.info("Selected source is not painting-enabled -- will not fill");
			return;
		}

		final LongFunction<?> maskForLabel = hasMaskForLabel.maskForLabel();
		if (maskForLabel == null)
		{
			LOG.info("Cannot generate boolean mask for this source -- will not fill");
			return;
		}

		final FragmentSegmentAssignment assignment;
		if (currentSourceState instanceof HasFragmentSegmentAssignments)
		{
			LOG.info("Selected source has a fragment-segment assignment that will be used for filling");
			assignment = ((HasFragmentSegmentAssignments) currentSourceState).assignment();
		}
		else
		{
			assignment = null;
		}

		final MaskedSource<?, ?> source = (MaskedSource<?, ?>) currentSource;

		final Type<?> t = source.getDataType();

		if (!(t instanceof LabelMultisetType) && !(t instanceof IntegerType<?>))
		{
			LOG.info("Data type is not integer or LabelMultisetType type -- will not fill");
			return;
		}

		final int               level          = 0;
		final AffineTransform3D labelTransform = new AffineTransform3D();
		final int               time           = viewerState.getTimepoint();
		source.getSourceTransform(time, level, labelTransform);

		final RealPoint rp = setCoordinates(x, y, viewer, labelTransform);
		final Point     p  = new Point(rp.numDimensions());
		for (int d = 0; d < p.numDimensions(); ++d)
		{
			p.setPosition(Math.round(rp.getDoublePosition(d)), d);
		}

		LOG.debug("Filling source {} with label {} at {}", source, fill, p);
		try
		{
			fill(
				(MaskedSource) source,
				time,
				level,
				fill,
				p,
				assignment
			);
		} catch (final MaskInUse e)
		{
			LOG.info(e.getMessage());
			return;
		}

	}

	private static RealPoint setCoordinates(
			final double x,
			final double y,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform)
	{
		return setCoordinates(x, y, new RealPoint(labelTransform.numDimensions()), viewer, labelTransform);
	}

	private static <P extends RealLocalizable & RealPositionable> P setCoordinates(
			final double x,
			final double y,
			final P location,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform)
	{
		location.setPosition(x, 0);
		location.setPosition(y, 1);
		location.setPosition(0, 2);

		viewer.displayToGlobalCoordinates(location);
		labelTransform.applyInverse(location, location);

		return location;
	}

	private <T extends IntegerType<T>> void fill(
			final MaskedSource<T, ?> source,
			final int time,
			final int level,
			final long fill,
			final Localizable seed,
			final FragmentSegmentAssignment assignment) throws MaskInUse
	{
		final RandomAccessibleInterval<T> data = source.getDataSource(time, level);
		final RandomAccess<T> dataAccess = data.randomAccess();
		dataAccess.setPosition(seed);
		final T seedValue = dataAccess.get();
		final long seedLabel = assignment != null ? assignment.getSegment(seedValue.getIntegerLong()) : seedValue.getIntegerLong();
		if (!Label.regular(seedLabel))
		{
			LOG.info("Trying to fill at irregular label: {} ({})", seedLabel, new Point(seed));
			return;
		}

		final MaskInfo<UnsignedLongType>                  maskInfo      = new MaskInfo<>(
				time,
				level,
				new UnsignedLongType(fill)
		);
		final Mask<UnsignedLongType>  mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
		final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views
				.extendValue(mask.mask, new UnsignedLongType(1)));

		@SuppressWarnings("unchecked")
		final Thread floodFillThread = new Thread(() -> {
			try {
				if (seedValue instanceof LabelMultisetType) {
					fillMultisetType((RandomAccessibleInterval<LabelMultisetType>) data, accessTracker, seed, seedLabel, assignment);
				} else {
					fillPrimitiveType(data, accessTracker, seed, seedLabel, assignment);
				}
			} catch (final Exception e) {
				// got an exception, ignore it if the operation has been canceled, or re-throw otherwise
				if (!Thread.currentThread().isInterrupted())
					throw e;
			}
			LOG.debug(Thread.currentThread().isInterrupted() ? "FloodFill has been interrupted" : "FloodFill has been completed");
		});

		final Thread floodFillResultCheckerThread = new Thread(() -> {
			while (floodFillThread.isAlive())
			{
				try
				{
					Thread.sleep(100);
				}
				catch (final InterruptedException e)
				{
					Thread.currentThread().interrupt(); // restore interrupted status
				}

				if (Thread.currentThread().isInterrupted())
					break;

				LOG.debug("Updating current view!");
				requestRepaint.run();
			}

			resetFloodFillState(source);

			if (Thread.interrupted())
			{
				floodFillThread.interrupt();
				try {
					source.resetMasks();
				} catch (final MaskInUse e) {
					e.printStackTrace();
				}
			}
			else
			{
				final Interval interval = accessTracker.createAccessInterval();
				LOG.debug(
						"Applying mask for interval {} {}",
						Arrays.toString(Intervals.minAsLongArray(interval)),
						Arrays.toString(Intervals.maxAsLongArray(interval))
				         );
				source.applyMask(mask, interval, FOREGROUND_CHECK);
			}

			requestRepaint.run();
		});

		setFloodFillState(source, new FloodFillState(fill, floodFillResultCheckerThread::interrupt));

		floodFillThread.start();
		floodFillResultCheckerThread.start();
	}

	private static void fillMultisetType(
			final RandomAccessibleInterval<LabelMultisetType> input,
			final RandomAccessible<UnsignedLongType> output,
			final Localizable seed,
			final long seedLabel,
			final FragmentSegmentAssignment assignment)
	{
		net.imglib2.algorithm.fill.FloodFill.fill(
				Views.extendValue(input, new LabelMultisetType()),
				output,
				seed,
				new UnsignedLongType(1),
				new DiamondShape(1),
				makePredicate(seedLabel, assignment)
			);
	}

	private static <T extends IntegerType<T>> void fillPrimitiveType(
			final RandomAccessibleInterval<T> input,
			final RandomAccessible<UnsignedLongType> output,
			final Localizable seed,
			final long seedLabel,
			final FragmentSegmentAssignment assignment)
	{
		final T extension = Util.getTypeFromInterval(input).createVariable();
		extension.setInteger(Label.OUTSIDE);

		net.imglib2.algorithm.fill.FloodFill.fill(
				Views.extendValue(input, extension),
				output,
				seed,
				new UnsignedLongType(1),
				new DiamondShape(1),
				makePredicate(seedLabel, assignment)
			);
	}

	private void setFloodFillState(final Source<?> source, final FloodFillState state)
	{
		final SourceState<?, ?> sourceState = this.sourceInfo.getState(source);
		if (sourceState instanceof HasFloodFillState)
			((HasFloodFillState) sourceState).floodFillState().set(state);
	}

	private void resetFloodFillState(final Source<?> source)
	{
		setFloodFillState(source, null);
	}

	private static <T extends IntegerType<T>> BiPredicate<T, UnsignedLongType> makePredicate(final long id, final FragmentSegmentAssignment assignment)
	{
		return (t, u) -> !Thread.currentThread().isInterrupted() && u.getInteger() == 0 && (assignment != null ? assignment.getSegment(t.getIntegerLong()) : t.getIntegerLong()) == id;
	}

	public static class RunAll implements Runnable
	{

		private final List<Runnable> runnables;

		public RunAll(final Runnable... runnables)
		{
			this(Arrays.asList(runnables));
		}

		public RunAll(final Collection<Runnable> runnables)
		{
			super();
			this.runnables = new ArrayList<>(runnables);
		}

		@Override
		public void run()
		{
			this.runnables.forEach(Runnable::run);
		}

	}
}
