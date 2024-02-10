package log.charter.gui.components.preview3D;

import log.charter.data.config.Config;
import log.charter.data.config.GraphicalConfig;

public class Preview3DUtils {
	public static final double topStringPosition = 0;
	public static final double fretThickness = 0.025;
	public static final double firstFretDistance = 1.2;
	public static final double noteHalfWidth = firstFretDistance / 3;
	public static final double tailHalfWidth = noteHalfWidth * 0.33;
	public static final double stringDistance = 0.35;
	public static final double bendHalfstepDistance = stringDistance * 0.8;
	public static final int closeDistance = 250;
	public static final float closeDistanceZ = (float) getTimePosition(closeDistance);
	public static final int fadedDistance = 50;
	public static final float fadedDistanceZ = (float) getTimePosition(fadedDistance);

	public static double fretLengthMultiplier = 1;// Math.pow(0.5, 1 / 12.0);

	private static double[] fretPositions = new double[Config.frets + 1];
	static {
		double fretLength = firstFretDistance;
		fretPositions[0] = 0;
		for (int fret = 1; fret <= Config.frets; fret++) {
			fretPositions[fret] = fretPositions[fret - 1] + fretLength;
			fretLength *= fretLengthMultiplier;
		}
	}

	public static double getFretPosition(final int fret) {
		if (fret < 0) {
			return fretPositions[0];
		}
		if (fret > Config.frets) {
			return fretPositions[Config.frets];
		}

		if (Config.leftHanded) {
			return fretPositions[Config.frets] / 2 - fretPositions[fret];
		}
		return fretPositions[fret];
	}

	// Gives similar visibility to Rocksmith, 1.3 -> 4 bars at 120 bpm, 1.0 -> 3 bars at 120 bpm
	public static int getVisibility() {
		return (int)(1_600 * GraphicalConfig.previewWindowScrollSpeed);
	}

	public static double getVisibilityZ() {
		return getTimePosition(getVisibility());
	}

	public static double getFretMiddlePosition(final int fret) {
		return (getFretPosition(fret - 1) + getFretPosition(fret)) / 2;
	}

	public static double getChartboardYPosition(final int strings) {
		return -stringDistance * strings;
	}

	private static int invertStrings(final int string, final int strings) {
		return Config.invertStrings ? strings - string - 1 : string;
	}

	public static double getStringPosition(final int string, final int strings) {
		return topStringPosition - invertStrings(string, strings) * stringDistance;
	}

	public static double getStringPositionWithBend(final int string, final int strings, final double bendValue) {
		return getStringPosition(string, strings) + bendValue * bendHalfstepDistance;
	}

	public static double getTimePosition(final int time) {
		// 0.02 is subjective and might need adjustments if camera perspective is edited
		return (time * 0.02) / GraphicalConfig.previewWindowScrollSpeed;
	}
}
