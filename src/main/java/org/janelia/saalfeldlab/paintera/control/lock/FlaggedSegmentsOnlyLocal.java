package org.janelia.saalfeldlab.paintera.control.lock;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.util.function.Consumer;

public class FlaggedSegmentsOnlyLocal extends FlaggedSegmentsState {
	private final TLongSet flaggedSegments = new TLongHashSet();

	private final Consumer<long[]> persister;

	public FlaggedSegmentsOnlyLocal(final Consumer<long[]> persister, final long... flaggedSegments)
	{
		super();
		this.flaggedSegments.addAll(flaggedSegments);
		this.persister = persister;
	}

	@Override
	public long[] flaggedSegmentsCopy()
	{
		return this.flaggedSegments.toArray();
	}

	@Override
	public void persist()
	{
		persister.accept(flaggedSegments.toArray());
	}

	@Override
	protected void flagImpl(final long segment)
	{
		this.flaggedSegments.add(segment);
	}

	@Override
	protected void unflagImpl(final long segment)
	{
		this.flaggedSegments.remove(segment);
	}

	@Override
	public boolean isFlagged(final long segment)
	{
		return this.flaggedSegments.contains(segment);
	}

}
