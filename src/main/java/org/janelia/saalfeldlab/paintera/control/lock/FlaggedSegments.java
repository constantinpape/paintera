package org.janelia.saalfeldlab.paintera.control.lock;

public interface FlaggedSegments {
	void flag(long segment);

	void unflag(long segment);

	boolean isFlagged(long segment);

	long[] flaggedSegmentsCopy();

}
