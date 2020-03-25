package org.janelia.saalfeldlab.paintera.control.lock;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;

public abstract class FlaggedSegmentsState extends ObservableWithListenersList implements FlaggedSegments {
	public abstract void persist();

	protected abstract void flagImpl(long segment);

	protected abstract void unflagImpl(long segment);

	@Override
	public void flag(final long segment)
	{
		flagImpl(segment);
		stateChanged();
	}

	@Override
	public void unflag(final long segment)
	{
		unflagImpl(segment);
		stateChanged();
	}

}
