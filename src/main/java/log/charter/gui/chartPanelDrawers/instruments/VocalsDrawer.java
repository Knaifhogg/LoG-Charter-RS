package log.charter.gui.chartPanelDrawers.instruments;

import static java.lang.Math.max;
import static log.charter.gui.chartPanelDrawers.common.DrawerUtils.lanesBottom;
import static log.charter.gui.chartPanelDrawers.common.DrawerUtils.lanesTop;
import static log.charter.gui.chartPanelDrawers.drawableShapes.DrawableShape.filledRectangle;
import static log.charter.gui.chartPanelDrawers.drawableShapes.DrawableShape.strokedRectangle;
import static log.charter.gui.chartPanelDrawers.drawableShapes.DrawableShape.text;
import static log.charter.util.ScalingUtils.timeToX;
import static log.charter.util.ScalingUtils.timeToXLength;

import java.awt.FontMetrics;
import java.awt.Graphics;

import log.charter.data.ChartData;
import log.charter.data.managers.selection.SelectionManager;
import log.charter.data.types.PositionType;
import log.charter.gui.ChartPanel;
import log.charter.gui.ChartPanelColors.ColorLabel;
import log.charter.gui.chartPanelDrawers.common.AudioDrawer;
import log.charter.gui.chartPanelDrawers.common.BeatsDrawer;
import log.charter.gui.chartPanelDrawers.common.LyricLinesDrawer;
import log.charter.gui.chartPanelDrawers.drawableShapes.DrawableShapeList;
import log.charter.gui.chartPanelDrawers.drawableShapes.ShapePositionWithSize;
import log.charter.song.vocals.Vocal;
import log.charter.util.CollectionUtils.ArrayList2;
import log.charter.util.CollectionUtils.HashSet2;
import log.charter.util.Position2D;

public class VocalsDrawer {
	private static final int vocalNoteY = (lanesTop + lanesBottom) / 2;

	private static ShapePositionWithSize getVocalNotePosition(final int x, final int length) {
		return new ShapePositionWithSize(x, vocalNoteY - 4, length, 8);
	}

	public static ShapePositionWithSize getVocalNotePosition(final int position, final int length, final int time) {
		return getVocalNotePosition(timeToX(position, time), timeToXLength(length));
	}

	private class VocalNotesDrawingData {
		private final DrawableShapeList texts = new DrawableShapeList();
		private final DrawableShapeList notes = new DrawableShapeList();
		private final DrawableShapeList wordConnections = new DrawableShapeList();

		private final FontMetrics fontMetrics;
		private final int time;

		public VocalNotesDrawingData(final FontMetrics fontMetrics, final int time) {
			this.fontMetrics = fontMetrics;
			this.time = time;
		}

		public void addVocal(final Vocal vocal, final Vocal next, final int x, final int lengthPx,
				final boolean selected) {
			if ((x + lengthPx) > 0) {
				final ShapePositionWithSize position = getVocalNotePosition(x, lengthPx);
				notes.add(filledRectangle(position, ColorLabel.VOCAL_NOTE.color()));
				if (selected) {
					notes.add(strokedRectangle(position.resized(-1, -1, 1, 1), ColorLabel.VOCAL_SELECT.color()));
				}

				final String text = vocal.getText() + (vocal.isWordPart() ? "-" : "");
				if ((x + fontMetrics.stringWidth(text)) > 0) {
					texts.add(text(new Position2D(x + 2, vocalNoteY - 10), text, ColorLabel.VOCAL_TEXT.color()));
				}
			}

			if (vocal.isWordPart() && next != null) {
				final int nextStart = timeToX(next.position(), time);
				final ShapePositionWithSize position = new ShapePositionWithSize(x + lengthPx, vocalNoteY,
						nextStart - x - lengthPx, 4)//
						.centeredY();
				wordConnections.add(filledRectangle(position, ColorLabel.VOCAL_NOTE_WORD_PART.color()));
			}
		}

		public void draw(final Graphics g) {
			wordConnections.draw(g);
			notes.draw(g);
			texts.draw(g);
		}
	}

	private boolean initiated = false;

	private AudioDrawer audioDrawer;
	private BeatsDrawer beatsDrawer;
	private ChartData data;
	private ChartPanel chartPanel;
	private LyricLinesDrawer lyricLinesDrawer;
	private SelectionManager selectionManager;

	public void init(final AudioDrawer audioDrawer, final BeatsDrawer beatsDrawer, final ChartData data,
			final ChartPanel chartPanel, final LyricLinesDrawer lyricLinesDrawer,
			final SelectionManager selectionManager) {
		this.audioDrawer = audioDrawer;
		this.beatsDrawer = beatsDrawer;
		this.data = data;
		this.chartPanel = chartPanel;
		this.lyricLinesDrawer = lyricLinesDrawer;
		this.selectionManager = selectionManager;

		initiated = true;
	}

	private void drawVocals(final Graphics g) {
		final VocalNotesDrawingData drawingData = new VocalNotesDrawingData(g.getFontMetrics(), data.time);

		final ArrayList2<Vocal> vocals = data.songChart.vocals.vocals;
		final int width = chartPanel.getWidth();
		final HashSet2<Integer> selectedVocalIds = selectionManager.getSelectedAccessor(PositionType.VOCAL)//
				.getSelectedSet().map(selection -> selection.id);

		for (int i = 0; i < vocals.size(); i++) {
			final Vocal vocal = vocals.get(i);
			final int x = timeToX(vocal.position(), data.time);
			if (x > width) {
				break;
			}

			final Vocal next = vocals.size() > i + 1 ? vocals.get(i + 1) : null;
			final int length = max(1, timeToXLength(vocal.length()));
			final boolean selected = selectedVocalIds.contains(i);
			drawingData.addVocal(vocal, next, x, length, selected);
		}

		drawingData.draw(g);
	}

	public void draw(final Graphics g) {
		if (!initiated || data.isEmpty) {
			return;
		}

		audioDrawer.draw(g);
		beatsDrawer.draw(g);
		drawVocals(g);
		lyricLinesDrawer.draw(g);
	}
}
