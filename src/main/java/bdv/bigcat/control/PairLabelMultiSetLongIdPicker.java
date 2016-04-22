/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.bigcat.control;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Pair;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class PairLabelMultiSetLongIdPicker implements IdPicker
{
	final protected ViewerPanel viewer;
	final protected RealRandomAccess< Pair< LabelMultisetType, LongType > > labelAccess;

	public PairLabelMultiSetLongIdPicker(
			final ViewerPanel viewer,
			final RealRandomAccessible< Pair< LabelMultisetType, LongType > > labels )
	{
		this.viewer = viewer;
		labelAccess = labels.realRandomAccess();
	}

	final private long getId()
	{
		final Pair< LabelMultisetType, LongType > ab = labelAccess.get();
		final LongType b = ab.getB();
		long id = b.get();
		if ( id == Label.TRANSPARENT )
			id = LabelMultiSetIdPicker.getMostSignificantId( ab.getA() );

		return id;
	}

	@Override
	public synchronized long getIdAtDisplayCoordinate( final int x, final int y )
	{
		labelAccess.setPosition( x, 0 );
		labelAccess.setPosition( y, 1 );
		labelAccess.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelAccess );

		return getId();
	}

	@Override
	public long getIdAtWorldCoordinate( final double x, final double y, final double z )
	{
		labelAccess.setPosition( x, 0 );
		labelAccess.setPosition( y, 1 );
		labelAccess.setPosition( z, 2 );

		return getId();
	}
}
