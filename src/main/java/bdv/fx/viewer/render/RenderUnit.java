package bdv.fx.viewer.render;

import bdv.cache.CacheControl;
import bdv.fx.viewer.ImagePane;
import bdv.fx.viewer.PriorityExecutorService;
import bdv.fx.viewer.ViewerState;
import bdv.fx.viewer.project.AccumulateProjectorFactory;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.LongStream;

public class RenderUnit implements TransformListener<AffineTransform3D> {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int NUM_RENDERING_THREADS = 1;

	private final int[] blockSize = {250, 250};

	private final long[] dimensions = {1, 1};

	private double[] screenScales = ScreenScalesConfig.defaultScreenScalesCopy();

	private CellGrid grid;

	private MultiResolutionRendererFX[] renderers = new MultiResolutionRendererFX[0];

	private ImagePane[] displays = new ImagePane[0];

	private PainterThread[] painterThreads = new PainterThread[0];

	private TransformAwareBufferedImageOverlayRendererFX[] renderTargets = new TransformAwareBufferedImageOverlayRendererFX[0];

	private final ThreadGroup threadGroup;

	private final Supplier<ViewerState> viewerState;

	private final Function<Source<?>, AxisOrder> axisOrder;

	private final Function<Source<?>, Interpolation> interpolation;

	private final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory;

	private final CacheControl cacheControl;

	private final long targetRenderNanos;

	private final PriorityExecutorService renderingExecutorService;

	private final List<Runnable> updateListeners = new ArrayList<>();

	private long[][] offsets = new long[0][];

	public RenderUnit(
			final ThreadGroup threadGroup,
			final Supplier<ViewerState> viewerState,
			final Function<Source<?>, AxisOrder> axisOrder,
			final Function<Source<?>, Interpolation> interpolation,
			final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory,
			final CacheControl cacheControl,
			final long targetRenderNanos,
			final PriorityExecutorService renderingExecutorService) {
		this.threadGroup = threadGroup;
		this.viewerState = viewerState;
		this.axisOrder = axisOrder;
		this.interpolation = interpolation;
		this.accumulateProjectorFactory = accumulateProjectorFactory;
		this.cacheControl = cacheControl;
		this.targetRenderNanos = targetRenderNanos;
		this.renderingExecutorService = renderingExecutorService;
		update();
	}

	public void setBlockSize(final int blockX, final int blockY)
	{
		blockSize[0] = Math.max(blockX, 1);
		blockSize[1] = Math.max(blockY, 1);
		update();
	}

	public void setDimensions(final long dimX, final long dimY)
	{
		dimensions[0] = Math.max(dimX, 0);
		dimensions[1] = Math.max(dimY, 0);
		update();
	}

	public synchronized void requestRepaintSingleTile(final int screenScaleIndex, final int tileIndex)
	{
		renderers[tileIndex].requestRepaint(screenScaleIndex);
	}

	public synchronized void requestRepaintSingleTile(final int tileIndex)
	{
		renderers[tileIndex].requestRepaint();
	}

	public synchronized void requestRepaint(final int screenScaleIndex, final int[] tileIndices)
	{
		for (final int b : tileIndices)
			renderers[b].requestRepaint(screenScaleIndex);
	}

	public synchronized void requestRepaint(final int[] tileIndices)
	{
		for (final int b : tileIndices)
			renderers[b].requestRepaint();
	}

	public synchronized void requestRepaint(final int screenScaleIndex)
	{
		for (int b = 0; b < renderers.length; ++b)
			renderers[b].requestRepaint(screenScaleIndex);
	}

	public synchronized void requestRepaint()
	{
		for (int b = 0; b < renderers.length; ++b)
			renderers[b].requestRepaint();
	}

	public synchronized void requestRepaint(final int screenScaleIndex, final long[] min, final long[] max)
	{

		long[] relevantBlocks = org.janelia.saalfeldlab.util.grids.Grids.getIntersectingBlocks(min, max, this.grid);
		for (final long b : relevantBlocks)
			renderers[(int)b].requestRepaint(screenScaleIndex);
	}

	public synchronized void requestRepaint(final long[] min, final long[] max)
	{

		long[] relevantBlocks = org.janelia.saalfeldlab.util.grids.Grids.getIntersectingBlocks(min, max, this.grid);
		for (final long b : relevantBlocks)
			renderers[(int)b].requestRepaint();
	}

	public synchronized void setScreenScales(final double[] screenScales)
	{
		this.screenScales = screenScales.clone();
		for (int index = 0; index < renderers.length; ++index)
			if (renderers[index] != null)
				renderers[index].setScreenScales(this.screenScales);
	}

	private synchronized void update()
	{

		for (PainterThread p : painterThreads) {
			if (p == null)
				continue;
			p.stopRendering();
			p.interrupt();
		}

		this.grid = new CellGrid(dimensions, blockSize);

		int numBlocks = (int) LongStream.of(this.grid.getGridDimensions()).reduce(1, (l1, l2) -> l1 * l2);
		renderers = new MultiResolutionRendererFX[numBlocks];
		displays = new ImagePane[numBlocks];
		renderTargets = new TransformAwareBufferedImageOverlayRendererFX[numBlocks];
		painterThreads = new PainterThread[numBlocks];
		offsets = new long[numBlocks][];
		LOG.debug("Updating render unit");
		final long[] cellPos = new long[2];
		final long[] min = new long[2];
		final long[] max = new long[2];
		final int[] cellDims = new int[2];
		for (int index = 0; index < renderers.length; ++index) {
			this.grid.getCellGridPositionFlat(index, cellPos);
			min[0] = this.grid.getCellMin(0, cellPos[0]);
			min[1] = this.grid.getCellMin(1, cellPos[1]);
			this.grid.getCellDimensions(cellPos, min, cellDims);
			Arrays.setAll(max, d -> min[d] + cellDims[d] - 1);
			final TransformAwareBufferedImageOverlayRendererFX renderTarget = new TransformAwareBufferedImageOverlayRendererFX();
			final PainterThread.Paintable paintable = new Paintable(index);
			final PainterThread painterThread = new PainterThread(threadGroup, "painter-thread-" + index, paintable);
			final MultiResolutionRendererFX renderer = new MultiResolutionRendererFX(
					renderTarget,
					painterThread,
					this.screenScales,
					targetRenderNanos,
					true,
					NUM_RENDERING_THREADS,
					renderingExecutorService,
					true,
					accumulateProjectorFactory,
					cacheControl
			);
			LOG.trace("Creating new renderer for block ({}) ({})", min, max);
			final ImagePane display = new ImagePane(cellDims[0], cellDims[1]);
			renderTarget.setCanvasSize(cellDims[0], cellDims[1]);
			renderers[index] = renderer;
			displays[index] = display;
			renderTargets[index] = renderTarget;
			painterThreads[index] = painterThread;
			offsets[index] = min.clone();
			painterThread.setDaemon(true);
			painterThread.start();
		}
		notifyUpdated();
	}

	@Override
	public void transformChanged(AffineTransform3D transform) {
		// TODO
	}

	public synchronized ImageDisplayGrid getImageDisplayGrid()
	{
		return new ImageDisplayGrid(displays, grid);
	}

	private class Paintable implements PainterThread.Paintable
	{

		final int index;

		private Paintable(int index) {
			this.index = index;
		}

		@Override
		public void paint() {
			MultiResolutionRendererFX renderer;
			ImagePane display;
			TransformAwareBufferedImageOverlayRendererFX renderTarget;
			long[] offset;
			ViewerState viewerState = null;
			final List<SourceAndConverter<?>> sacs = new ArrayList<>();
			synchronized (RenderUnit.this)
			{
				renderer = index < renderers.length ? renderers[index] : null;
				display = index < displays.length ? displays[index] : null;
				renderTarget = index < renderTargets.length ? renderTargets[index] : null;
				offset = index < offsets.length ? offsets[index] : null;
				if (renderer != null && display != null && renderTarget != null && offset != null) {
					viewerState = RenderUnit.this.viewerState.get().copy();
					sacs.addAll(viewerState.getSources());
				}
			}
			if (renderer == null || display == null || renderTarget == null || offset == null)
				return;

			final AffineTransform3D viewerTransform = new AffineTransform3D();
			viewerState.getViewerTransform(viewerTransform);
			viewerTransform.translate(-offset[0], -offset[1], 0);

			renderer.paint(
					sacs,
					axisOrder,
					viewerState.timepointProperty().get(),
					viewerTransform,
					interpolation,
					null
			);

			renderTarget.drawOverlays(display::setImage);
		}
	}

	public void addUpdateListener(final Runnable listener)
	{
		this.updateListeners.add(listener);
		listener.run();
	}

	private void notifyUpdated()
	{
		this.updateListeners.forEach(Runnable::run);
	}

	public static class ImageDisplayGrid
	{
		private final ImagePane[] images;

		private final CellGrid grid;

		private final GridPane pane;

		private ImageDisplayGrid(final ImagePane[] images, final CellGrid grid) {
			this.images = images;
			this.grid = grid;
			this.pane = makeGrid();
		}

		public Node getGridPane()
		{
			return this.pane;
		}

		public CellGrid getGrid()
		{
			return this.grid;
		}

		public ObjectProperty<Image> imagePropertyAt(final int linearGridIndex)
		{
			return images[linearGridIndex].imageProperty();
		}

		private GridPane makeGrid()
		{
			final GridPane pane = new GridPane();
			pane.setMinWidth(1);
			pane.setMinHeight(1);
			pane.setPrefWidth(this.grid.getImgDimensions()[0]);
			pane.setPrefHeight(this.grid.getImgDimensions()[0]);
			long[] gridPos = new long[2];
			for (int i = 0; i < images.length; ++i)
			{
				this.grid.getCellGridPositionFlat(i, gridPos);
				LOG.debug("Putting render block {} into cell at position {}", i, gridPos);
				pane.add(images[i], (int)gridPos[0], (int)gridPos[1]);
			}
			return pane;
		}
	}

}
