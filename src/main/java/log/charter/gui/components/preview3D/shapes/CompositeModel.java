package log.charter.gui.components.preview3D.shapes;

import java.util.List;

import log.charter.gui.components.preview3D.Point3D;
import log.charter.util.CollectionUtils.Pair;

public interface CompositeModel {
	List<Pair<Integer, List<Point3D>>> getPointsForModes();
}
