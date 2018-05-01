package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Map;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;

public interface MeshManager
{
	public void generateMesh( final long id );

	public void removeMesh( final long id );

	public void removeAllMeshes();

	public IntegerProperty scaleLevelProperty();

	public IntegerProperty meshSimplificationIterationsProperty();

	public DoubleProperty smoothingLambdaProperty();

	public IntegerProperty smoothingIterationsProperty();

	public Map< Long, MeshGenerator > unmodifiableMeshMap();

}
