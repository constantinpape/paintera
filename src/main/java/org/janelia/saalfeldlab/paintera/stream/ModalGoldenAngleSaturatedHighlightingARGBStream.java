/**
 * License: GPL
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License 2 as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.paintera.stream;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.lock.FlaggedSegments;
import org.janelia.saalfeldlab.paintera.control.lock.FlaggedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;

/**
 * Generates a stream of saturated colors. Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by the golden angle,
 * making them reasonably distinct. Changing the seed of the stream makes a new
 * sequence.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class ModalGoldenAngleSaturatedHighlightingARGBStream extends GoldenAngleSaturatedHighlightingARGBStream
{
	final static public int NORMAL = 0;

	final static public int HIDE_CONFIRMED = 1;

	final static public int SELECTED_ONLY = 2;

	// TODO is there a better way of constructing stream wihtout dummy
	// assignment and id service?
	public ModalGoldenAngleSaturatedHighlightingARGBStream()
	{
		this(
				new SelectedSegments(new SelectedIds(), new FragmentSegmentAssignmentOnlyLocal((k, v) -> {})),
				new LockedSegmentsOnlyLocal(locked -> {}), new FlaggedSegmentsOnlyLocal(flagged -> {}));
	}

	public ModalGoldenAngleSaturatedHighlightingARGBStream(
			final SelectedSegments selectedSegments,
			final LockedSegments lockedSegments,
			final FlaggedSegments flaggedSegments)
	{
		super(selectedSegments, lockedSegments, flaggedSegments);
		seed = 1;
	}
}
