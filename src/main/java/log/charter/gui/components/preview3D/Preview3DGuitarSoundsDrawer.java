package log.charter.gui.components.preview3D;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static log.charter.gui.ChartPanelColors.getStringBasedColor;
import static log.charter.gui.components.preview3D.Matrix4.moveMatrix;
import static log.charter.gui.components.preview3D.Matrix4.scaleMatrix;
import static log.charter.gui.components.preview3D.Preview3DUtils.getChartboardYPosition;
import static log.charter.gui.components.preview3D.Preview3DUtils.getFretMiddlePosition;
import static log.charter.gui.components.preview3D.Preview3DUtils.getFretPosition;
import static log.charter.gui.components.preview3D.Preview3DUtils.getStringPositionWithBend;
import static log.charter.gui.components.preview3D.Preview3DUtils.getTimePosition;
import static log.charter.gui.components.preview3D.Preview3DUtils.getTopStringYPosition;
import static log.charter.gui.components.preview3D.Preview3DUtils.visibility;
import static log.charter.song.notes.IPosition.findLastBeforeEqual;
import static log.charter.song.notes.IPosition.findLastIdBeforeEqual;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL30;

import log.charter.data.ChartData;
import log.charter.data.config.Config;
import log.charter.gui.ChartPanelColors.ColorLabel;
import log.charter.gui.ChartPanelColors.StringColorLabelType;
import log.charter.gui.components.preview3D.BaseShader.BaseShaderDrawData;
import log.charter.gui.components.preview3D.data.Preview3DChordDrawingData;
import log.charter.gui.components.preview3D.data.Preview3DNoteData;
import log.charter.gui.components.preview3D.data.Preview3DNotesData;
import log.charter.gui.components.preview3D.shapes.CompositeModel;
import log.charter.gui.components.preview3D.shapes.FrettedNoteModel;
import log.charter.gui.components.preview3D.shapes.Model;
import log.charter.gui.components.preview3D.shapes.NoteStatusModels;
import log.charter.gui.components.preview3D.shapes.OpenNoteModel;
import log.charter.song.Anchor;
import log.charter.song.BendValue;
import log.charter.song.enums.HOPO;
import log.charter.song.enums.Harmonic;
import log.charter.song.enums.Mute;
import log.charter.util.CollectionUtils.Pair;

public class Preview3DGuitarSoundsDrawer {
	private static final int anticipationWindow = 1_000;
	private static final int highlightWindow = 50;

	private ChartData data;

	private final static Map<Integer, Map<Integer, CompositeModel>> openNoteModels = new HashMap<>();
	private final static Map<Integer, Map<Integer, CompositeModel>> openNoteModelsLeftHanded = new HashMap<>();

	private static CompositeModel getOpenNoteModel(final int fret0, final int fret1) {
		final Map<Integer, Map<Integer, CompositeModel>> currentMap = Config.leftHanded ? openNoteModelsLeftHanded
				: openNoteModels;

		if (currentMap.get(fret0) == null) {
			currentMap.put(fret0, new HashMap<>());
		}
		if (currentMap.get(fret0).get(fret1) == null) {
			final double width = getFretPosition(fret1) - getFretPosition(fret0);
			currentMap.get(fret0).put(fret1, new OpenNoteModel(width));
		}

		return currentMap.get(fret0).get(fret1);
	}

	public void init(final ChartData data) {
		this.data = data;
	}

	private boolean invertBend(final int string) {
		return string < data.currentStrings() - 2 && (string <= 2 || string > data.currentStrings() / 2);
	}

	private Anchor findAnchorForPosition(final int position) {
		final Anchor anchor = findLastBeforeEqual(data.getCurrentArrangementLevel().anchors, position);
		return anchor == null ? new Anchor(0, 0) : anchor;
	}

//
//	private void drawNoteAnticipation(final Drawable3DShapesListForScene shapesList, final NoteData note) {
//		final Color color = ColorLabel.valueOf("LANE_" + note.string).color();
//		final int dt = data.time + anticipationWindow - note.position;
//		final double y = getStringPositionWithBend(note.string, note.prebend);
//		double scale = 1.0 * dt / anticipationWindow;
//		scale *= scale;
//
//		if (note.fretNumber == 0) {
//			final Anchor anchor = findAnchorForPosition(note.position);
//			final double x0 = getFretPosition(anchor.fret - 1);
//			final double x1 = getFretPosition(anchor.fret + anchor.width - 1);
//			final double y0 = y - 0.05 * scale;
//			final double y1 = y + 0.05 * scale;
//			shapesList.addRectangleZ(color, -1.1, x0, x1, y0, y1, fretboardZ);
//		} else {
//			final double x = getFretMiddlePosition(note.fretNumber);
//			final double x0 = x - 0.3 * scale;
//			final double x1 = x + 0.3 * scale;
//			final double y0 = y - 0.15 * scale;
//			final double y1 = y + 0.15 * scale;
//			final double z0 = fretboardZ - 0.15 * scale;
//			final double z1 = fretboardZ + 0.15 * scale;
//			shapesList.addRectangleX(color, -1.1, x0, y0, y1, z0, z1);
//			shapesList.addRectangleX(color, -1.1, x1, y0, y1, z0, z1);
//			shapesList.addRectangleY(color, -1.1, x0, x1, y0, z0, z1);
//			shapesList.addRectangleY(color, -1.1, x0, x1, y1, z0, z1);
//		}
//	}
//
	private double getNoteHeightAtTime(final Preview3DNoteData note, final int t, final boolean invertBend) {
		final int dt = t - note.position;

		double bendValue = 0;
		if (!note.bendValues.isEmpty()) {
			final int lastBendId = findLastIdBeforeEqual(note.bendValues, dt);
			double bendAValue = 0;
			int bendAPosition = 0;
			if (lastBendId != -1) {
				final BendValue bend = note.bendValues.get(lastBendId);
				bendAValue = bend.bendValue.doubleValue();
				bendAPosition = bend.position();
			}
			if (lastBendId < note.bendValues.size() - 1) {
				final BendValue nextBend = note.bendValues.get(lastBendId + 1);
				final double bendBValue = nextBend.bendValue.doubleValue();
				final int bendBPosition = nextBend.position();
				final double scale = 1.0 * (dt - bendAPosition) / (bendBPosition - bendAPosition);
				bendValue = bendAValue * (1 - scale) + (bendBValue) * scale;
			} else {
				bendValue = bendAValue;
			}
		}
		if (invertBend) {
			bendValue = -bendValue;
		}

		if (note.vibrato) {
			bendValue += sin(dt * Math.PI / 100) * 0.2;
		}
		final double value = getStringPositionWithBend(note.string, data.currentStrings(), bendValue);

		return value;
	}

//
//	private void drawNoteHold(final Drawable3DShapesListForScene shapesList, final NoteData note) {
//		final Color color = ColorLabel.valueOf("LANE_" + note.string).color();
//
//		final double y = getNotePositionAtTime(note, data.time);
//
//		if (note.fretNumber == 0) {
//			final Anchor anchor = findAnchorForPosition(note.position);
//			final double x0 = getFretPosition(anchor.fret - 1);
//			final double x1 = getFretPosition(anchor.fret + anchor.width - 1);
//			final double y0 = y - 0.05;
//			final double y1 = y + 0.05;
//			shapesList.addRectangleZ(color, -1.1, x0, x1, y0, y1, fretboardZ);
//		} else {
//			final double x = getFretMiddlePosition(note.fretNumber);
//			final double x0 = x - 0.3;
//			final double x1 = x + 0.3;
//			final double y0 = y - 0.15;
//			final double y1 = y + 0.15;
//			final double z0 = fretboardZ - 0.15;
//			final double z1 = fretboardZ + 0.15;
//			shapesList.addRectangleX(color, -1.1, x0, y0, y1, z0, z1);
//			shapesList.addRectangleX(color, -1.1, x1, y0, y1, z0, z1);
//			shapesList.addRectangleY(color, -1.1, x0, x1, y0, z0, z1);
//			shapesList.addRectangleY(color, -1.1, x0, x1, y1, z0, z1);
//		}
//	}
//

	private void drawFullChordMute(final BaseShader baseShader, final double x0, final double x1, final double y0,
			final double y1, double z) {
		final double x = (x0 + x1) / 2;
		final double y = (y0 + y1) / 2;
		final double d0y = 0.8 * (y1 - y);
		final double d1y = 0.9 * (y1 - y);
		final double d0x = d0y / 10;
		final double d1x = d1y / 10;
		z -= 0.001;

		final Color color = ColorLabel.PREVIEW_3D_CHORD_FULL_MUTE.color();
		final BaseShaderDrawData shadowDrawData = baseShader.new BaseShaderDrawData();
		shadowDrawData.addVertex(new Point3D(x - d1x, y + d0y, z), color)//
				.addVertex(new Point3D(x - d0x, y + d1y, z), color)//
				.addVertex(new Point3D(x + d1x, y - d0y, z), color)//
				.addVertex(new Point3D(x + d0x, y - d1y, z), color)//

				.addVertex(new Point3D(x + d1x, y + d0y, z), color)//
				.addVertex(new Point3D(x - d0x, y - d1y, z), color)//
				.addVertex(new Point3D(x - d1x, y - d0y, z), color)//
				.addVertex(new Point3D(x + d0x, y + d1y, z), color)//
				.draw(GL30.GL_QUADS);
	}

	private void drawPalmChordMute(final BaseShader baseShader, final double x0, final double x1, final double y0,
			final double y1, double z) {
		final double x = (x0 + x1) / 2;
		final double y = (y0 + y1) / 2;
		final double d0x = 0.8 * (x1 - x);
		final double d1x = 0.9 * (x1 - x);
		final double d0y = 0.8 * (y1 - y);
		final double d1y = 0.9 * (y1 - y);
		z -= 0.001;

		final Color color = ColorLabel.PREVIEW_3D_CHORD_FULL_MUTE.color();
		final BaseShaderDrawData shadowDrawData = baseShader.new BaseShaderDrawData();
		shadowDrawData.addVertex(new Point3D(x - d1x, y + d0y, z), color)//
				.addVertex(new Point3D(x - d0x, y + d1y, z), color)//
				.addVertex(new Point3D(x + d1x, y - d0y, z), color)//
				.addVertex(new Point3D(x + d0x, y - d1y, z), color)//

				.addVertex(new Point3D(x + d1x, y + d0y, z), color)//
				.addVertex(new Point3D(x - d0x, y - d1y, z), color)//
				.addVertex(new Point3D(x - d1x, y - d0y, z), color)//
				.addVertex(new Point3D(x + d0x, y + d1y, z), color)//
				.draw(GL30.GL_QUADS);
	}

	private void drawChordShadow(final BaseShader baseShader, final int position, final Mute mute) {
		final Anchor anchor = findAnchorForPosition(position);
		final double x0 = getFretPosition(anchor.fret - 1);
		final double x1 = getFretPosition(anchor.topFret());
		final double y0 = getChartboardYPosition(data.currentStrings());
		final double y1 = getTopStringYPosition();
		final double z = max(0, getTimePosition(position - data.time));

		final Point3D p00 = new Point3D(x0, y0, z);
		final Point3D p01 = new Point3D((x1 + x0) / 2, y0, z);
		final Point3D p02 = new Point3D(x1, y0, z);
		final Point3D p10 = new Point3D(x0, y1, z);
		final Point3D p12 = new Point3D(x1, y1, z);
		final Color color = ColorLabel.PREVIEW_3D_CHORD_BOX.color();
		final Color shadowInvisibleColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);

		baseShader.clearModelMatrix();
		final BaseShaderDrawData shadowDrawData = baseShader.new BaseShaderDrawData();
		shadowDrawData.addVertex(p00, color)//
				.addVertex(p01, shadowInvisibleColor)//
				.addVertex(p10, shadowInvisibleColor)//
				.addVertex(p02, color)//
				.addVertex(p01, shadowInvisibleColor)//
				.addVertex(p12, shadowInvisibleColor)//
				.draw(GL30.GL_TRIANGLES);

		if (mute == Mute.FULL) {
			drawFullChordMute(baseShader, x0, x1, y0, y1, z);
		} else if (mute == Mute.PALM) {
			drawPalmChordMute(baseShader, x0, x1, y0, y1, z);
		}
	}

	private void drawNoteShadow(final BaseShader baseShader, final double x, final double y, final double z,
			final Color color) {
		final double shadowBaseY = getChartboardYPosition(data.currentStrings());
		final Point3D shadowBaseP0 = new Point3D(x - 0.02, shadowBaseY, z);
		final Point3D shadowBaseP1 = new Point3D(x, shadowBaseY, z);
		final Point3D shadowBaseP2 = new Point3D(x + 0.02, shadowBaseY, z);
		final Point3D shadowP3 = new Point3D(x, y - 0.3, z);
		final Color shadowInvisibleColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);

		baseShader.clearModelMatrix();
		final BaseShaderDrawData shadowDrawData = baseShader.new BaseShaderDrawData();
		shadowDrawData.addVertex(shadowBaseP1, color)//
				.addVertex(shadowBaseP0, shadowInvisibleColor)//
				.addVertex(shadowP3, shadowInvisibleColor)//
				.addVertex(shadowBaseP2, shadowInvisibleColor)//
				.draw(GL30.GL_TRIANGLE_FAN);
	}

	private void drawMuteForSingleNote(final BaseShader baseShader, final double x, final double y, final double z,
			final Mute mute) {
		baseShader.setModelMatrix(moveMatrix(x, y, z));
		final Model model = NoteStatusModels.mutesModels.get(mute);
		final Color color = NoteStatusModels.mutesColors.get(mute).color();
		baseShader.drawModel(model, color);
	}

	private void drawHOPO(final BaseShader baseShader, final double x, final double y, final double z,
			final HOPO hopo) {
		baseShader.setModelMatrix(moveMatrix(x, y, z));
		final Model model = NoteStatusModels.hoposModels.get(hopo);
		final Color color = NoteStatusModels.hoposColors.get(hopo).color();
		baseShader.drawModel(model, color);
	}

	private void drawHarmonic(final BaseShader baseShader, final double x, final double y, final double z,
			final Harmonic harmonic) {
		baseShader.setModelMatrix(moveMatrix(x, y, z));
		final Model model = NoteStatusModels.harmonicsModels.get(harmonic);
		final Color color = NoteStatusModels.harmonicsColors.get(harmonic).color();
		baseShader.drawModel(model, color);
	}

	private void drawOpenStringNoteHead(final BaseShader baseShader, final int position, final Preview3DNoteData note,
			final boolean hit) {
		Color color = getStringBasedColor(StringColorLabelType.NOTE, note.string, data.currentStrings());
		if (hit) {
			color = color.brighter();
		}

		final double y = getStringPositionWithBend(note.string, data.currentStrings(), note.prebend);
		final double z = getTimePosition(position - data.time);
		if (note.fret == 0) {
			final Anchor anchor = findAnchorForPosition(position);
			final double x = getFretPosition(anchor.fret - 1);
			baseShader.setModelMatrix(moveMatrix(x, y, z));
			for (final Pair<Integer, List<Point3D>> points : getOpenNoteModel(anchor.fret - 1, anchor.topFret())
					.getPointsForModes()) {
				final BaseShaderDrawData drawData = baseShader.new BaseShaderDrawData();
				for (final Point3D point : points.b) {
					drawData.addVertex(point, color);
				}
				drawData.draw(points.a);
			}

			final double middleX = (getFretPosition(anchor.topFret()) + x) / 2;
			if (note.mute != Mute.NONE) {
				drawMuteForSingleNote(baseShader, middleX, y, z, note.mute);
			}
			if (note.hopo != HOPO.NONE) {
				drawHOPO(baseShader, middleX, y, z, note.hopo);
			}
			if (note.harmonic != Harmonic.NONE) {
				drawHarmonic(baseShader, middleX, y, z, note.harmonic);
			}

			return;
		}
	}

	private void drawNoteHead(final BaseShader baseShader, final int position, final Preview3DNoteData note,
			final boolean hit) {
		if (note.linkPrevious) {
			return;
		}
		if (note.fret == 0) {
			drawOpenStringNoteHead(baseShader, position, note, hit);
			return;
		}

		Color color = getStringBasedColor(StringColorLabelType.NOTE, note.string, data.currentStrings());
		if (hit) {
			color = color.brighter();
		}

		final double x = getFretMiddlePosition(note.fret);
		final double y = getStringPositionWithBend(note.string, data.currentStrings(), note.prebend);
		final double z = getTimePosition(position - data.time);

		if (!note.accent) {
			baseShader.setModelMatrix(Matrix4.moveMatrix(x, y, z));
			final BaseShaderDrawData drawData = baseShader.new BaseShaderDrawData();
			drawData.addModel(FrettedNoteModel.instance, color);
			drawData.draw(FrettedNoteModel.instance.getDrawMode());
		} else {
			final Color accentColor = getStringBasedColor(StringColorLabelType.NOTE_ACCENT, note.string,
					data.currentStrings());
			baseShader.setModelMatrix(moveMatrix(x, y, z).multiply(scaleMatrix(1.1, 1.1, 1)));
			final BaseShaderDrawData drawData = baseShader.new BaseShaderDrawData();
			drawData.addModel(FrettedNoteModel.instance, accentColor);
			drawData.draw(FrettedNoteModel.instance.getDrawMode());
		}

		if (note.mute != Mute.NONE) {
			drawMuteForSingleNote(baseShader, x, y, z, note.mute);
		}
		if (note.hopo != HOPO.NONE) {
			drawHOPO(baseShader, x, y, z, note.hopo);
		}
		if (note.harmonic != Harmonic.NONE) {
			drawHarmonic(baseShader, x, y, z, note.harmonic);
		}

		if (!hit && !note.isChordNote) {
			drawNoteShadow(baseShader, x, y, z, color);
		}
	}

	private double getNoteSlideOffsetAtTime(final Preview3DNoteData note, final double progress) {
		if (note.slideTo == null) {
			return 0;
		}

		final double startPosition = getFretMiddlePosition(note.fret);
		final double endPosition = getFretMiddlePosition(note.slideTo);

		final double weight = note.unpitchedSlide//
				? 1 - sin((1 - progress) * Math.PI / 2)//
				: pow(sin(progress * Math.PI / 2), 3);
		return (endPosition - startPosition) * weight;
	}

	private void drawNoteTail(final BaseShader baseShader, final Preview3DNoteData note, final boolean invertBend) {
		if (note.length < 10) {
			return;
		}

		final double x0;
		final double x1;

		if (note.fret == 0) {
			final Anchor anchor = findAnchorForPosition(note.position);
			x0 = getFretPosition(anchor.fret - 1);
			x1 = getFretPosition(anchor.fret + anchor.width - 1);
		} else {
			final double x = getFretMiddlePosition(note.fret);
			x0 = x - FrettedNoteModel.width / 3;
			x1 = x + FrettedNoteModel.width / 3;
		}

		final BaseShaderDrawData drawData = baseShader.new BaseShaderDrawData();

		final Color color = getStringBasedColor(StringColorLabelType.NOTE_TAIL, note.string, data.currentStrings());

		final int tremoloSize = 100;
		for (int t = max(data.time, note.position); t <= note.position + note.length
				&& t < data.time + visibility; t += 1) {
			final double xSlideOffset = getNoteSlideOffsetAtTime(note, (double) (t - note.position) / note.length);
			final double y = getNoteHeightAtTime(note, t, invertBend);
			final double z = getTimePosition(t - data.time);

			if (note.tremolo) {
				final double xOffset = -0.05 * abs((double) (t % tremoloSize) / tremoloSize - 0.5);
				drawData.addVertex(new Point3D(x0 + xOffset + xSlideOffset, y, z), color)//
						.addVertex(new Point3D(x1 + xOffset + xSlideOffset, y, z), color);
			} else {
				drawData.addVertex(new Point3D(x0 + xSlideOffset, y, z), color)//
						.addVertex(new Point3D(x1 + xSlideOffset, y, z), color);
			}
		}

		baseShader.clearModelMatrix();
		drawData.draw(GL30.GL_TRIANGLE_STRIP);
	}

	private void drawNote(final BaseShader baseShader, final Preview3DNoteData note, final boolean invertBend) {
		if (!note.linkPrevious && note.position <= data.time + anticipationWindow && note.position >= data.time) {
			// drawNoteAnticipation(baseShader, note);
		}

		if (note.position >= data.time) {
			drawNoteHead(baseShader, note.position, note, false);
		} else if (!note.linkPrevious && note.position >= data.time - highlightWindow
				&& note.length < highlightWindow) {
			drawNoteHead(baseShader, data.time, note, true);
		} else if (note.position + note.length >= data.time) {
			// drawNoteHold(baseShader, note);
		}

		drawNoteTail(baseShader, note, invertBend);
	}

	public void draw(final BaseShader baseShader) {
		final Preview3DNotesData notesData = new Preview3DNotesData(data.getCurrentArrangement(),
				data.getCurrentArrangementLevel(), data.time - highlightWindow, data.time + visibility);

		for (int string = 0; string < data.currentStrings(); string++) {
			final boolean shouldBendDownwards = invertBend(string);

			for (final Preview3DNoteData note : notesData.notes.get(string)) {
				drawNote(baseShader, note, shouldBendDownwards);
			}
		}
		for (int i = notesData.chords.size() - 1; i >= 0; i--) {
			final Preview3DChordDrawingData chord = notesData.chords.get(i);
			if (chord.position() < data.time - highlightWindow) {
				continue;
			}

			drawChordShadow(baseShader, chord.position(), chord.mute);
		}
	}

}
