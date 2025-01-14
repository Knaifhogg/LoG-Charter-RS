package log.charter.gui.components;

import static java.lang.Math.max;
import static log.charter.data.config.Config.chartMapHeightMultiplier;
import static log.charter.data.config.Config.maxStrings;
import static log.charter.gui.ChartPanelColors.getStringBasedColor;
import static log.charter.util.ScalingUtils.xToTimeLength;
import static log.charter.util.Utils.getStringPosition;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.List;
import java.util.function.Predicate;

import log.charter.data.ChartData;
import log.charter.data.config.Config;
import log.charter.data.managers.ModeManager;
import log.charter.gui.ChartPanel;
import log.charter.gui.ChartPanelColors.ColorLabel;
import log.charter.gui.ChartPanelColors.StringColorLabelType;
import log.charter.gui.CharterFrame;
import log.charter.song.EventPoint;
import log.charter.song.notes.ChordOrNote;
import log.charter.song.vocals.Vocal;

public class ChartMap extends Component implements MouseListener, MouseMotionListener {
	private static final long serialVersionUID = 1L;

	public static int getPreferredHeight() {
		return 2 * chartMapHeightMultiplier + 1 + maxStrings * chartMapHeightMultiplier;
	}

	private ChartPanel chartPanel;
	private ChartData data;
	private CharterFrame frame;
	private ModeManager modeManager;

	public void init(final ChartPanel chartPanel, final ChartData data, final CharterFrame frame,
			final ModeManager modeManager) {
		this.chartPanel = chartPanel;
		this.data = data;
		this.frame = frame;
		this.modeManager = modeManager;

		setSize(frame.getWidth(), getPreferredHeight());

		setFocusable(false);
		addMouseListener(this);
		addMouseMotionListener(this);
	}

	private int positionToTime(final int p) {
		int time = p * data.songChart.beatsMap.songLengthMs / (getWidth() - 1);
		if (time < 0) {
			time = 0;
		}
		if (time > data.songChart.beatsMap.songLengthMs) {
			time = data.songChart.beatsMap.songLengthMs;
		}

		return time;
	}

	private int timeToPosition(final int t) {
		return t * (getWidth() - 1) / data.songChart.beatsMap.songLengthMs;
	}

	private void drawBars(final Graphics g) {
		g.setColor(ColorLabel.MAIN_BEAT.color());

		data.songChart.beatsMap.beats.stream()//
				.filter(beat -> beat.firstInMeasure)//
				.map(beat -> beat.position())//
				.forEach(position -> {
					final int x = timeToPosition(position);
					g.drawLine(x, 0, x, getHeight());
				});
	}

	private void drawVocalLines(final Graphics g) {
		g.setColor(ColorLabel.VOCAL_NOTE.color());

		final int y0 = 2 * chartMapHeightMultiplier;
		final int y2 = getHeight() - 2 * chartMapHeightMultiplier;
		final int y1 = (y0 + y2) / 2;
		boolean started = false;
		int x = 0;

		for (final Vocal vocal : data.songChart.vocals.vocals) {
			if (!started) {
				started = true;
				x = timeToPosition(vocal.position());
			}

			if (vocal.isPhraseEnd()) {
				final int x1 = timeToPosition(vocal.position() + vocal.length());

				g.fillRect(x, y1 - chartMapHeightMultiplier, x1 - x, 2 * chartMapHeightMultiplier + 1);
				g.drawLine(x, y0, x, y2);
				g.drawLine(x1, y0, x1, y2);
				started = false;
			}
		}
	}

	private void drawEventPoints(final Graphics g, final int y, final ColorLabel color,
			final Predicate<EventPoint> filter) {
		final List<EventPoint> points = data.getCurrentArrangement().getFilteredEventPoints(filter);

		for (int i = 0; i < points.size(); i++) {
			final EventPoint point = points.get(i);
			final EventPoint nextPoint = i + 1 < points.size() ? points.get(i + 1) : null;

			final int x0 = timeToPosition(point.position());
			final int x1 = timeToPosition(
					nextPoint == null ? data.songChart.beatsMap.songLengthMs : nextPoint.position());
			final int width = max(1, x1 - x0 - 2);

			g.setColor(color.color());
			g.fillRect(x0, y, width, chartMapHeightMultiplier);
			g.setColor(color.color().darker());
			g.fillRect(x1 - 2, y, 2, chartMapHeightMultiplier);
		}
	}

	private void drawPhrases(final Graphics g) {
		drawEventPoints(g, 0, ColorLabel.PHRASE_COLOR, ep -> ep.phrase != null);
	}

	private void drawSections(final Graphics g) {
		drawEventPoints(g, chartMapHeightMultiplier, ColorLabel.SECTION_COLOR, ep -> ep.section != null);
	}

	private void drawNote(final Graphics g, final int string, final int position, final int length) {
		g.setColor(getStringBasedColor(StringColorLabelType.NOTE, string, data.currentStrings()));

		final int x0 = timeToPosition(position);
		final int x1 = timeToPosition(position + length);
		final int y0 = 2 * chartMapHeightMultiplier + 1
				+ getStringPosition(string, data.currentStrings()) * chartMapHeightMultiplier;
		final int y1 = y0 + chartMapHeightMultiplier - 1;
		g.drawLine(x0, y0, x0, y1);
		if (x1 > x0) {
			g.drawLine(x0, y1, x1, y1);
		}
	}

	private void drawNotes(final Graphics g) {
		for (final ChordOrNote sound : data.getCurrentArrangementLevel().chordsAndNotes) {
			if (sound.isNote()) {
				drawNote(g, sound.note.string, sound.position(), sound.length());
			} else {
				sound.chord.chordNotes.forEach((string, chordNote) -> {
					drawNote(g, string, sound.position(), chordNote.length);
				});
			}
		}
	}

	private void drawBookmarks(final Graphics g) {
		g.setColor(ColorLabel.BOOKMARK.color());

		data.songChart.bookmarks.forEach((number, position) -> {
			final int x = timeToPosition(position);
			g.drawLine(x, 0, x, getHeight());
			g.drawString(number + "", x + 2, 10);
		});
	}

	private void drawMarkerAndViewArea(final Graphics g) {
		final int markerPosition = timeToPosition(data.time);

		final int x0 = markerPosition - timeToPosition(xToTimeLength(Config.markerOffset));
		final int x1 = markerPosition + timeToPosition(xToTimeLength(chartPanel.getWidth() - Config.markerOffset));
		g.setColor(ColorLabel.SELECT.color());
		g.drawRect(x0, 0, x1 - x0, getPreferredHeight() - 1);

		g.setColor(ColorLabel.MARKER.color());
		g.drawLine(markerPosition, 0, markerPosition, getHeight() - 1);
		g.setColor(ColorLabel.MARKER.color().darker());
		g.drawLine(markerPosition + 1, 0, markerPosition + 1, getHeight() - 1);
	}

	@Override
	public void paint(final Graphics g) {
		g.setColor(ColorLabel.BASE_BG_4.color());
		g.fillRect(0, 0, getWidth(), getHeight());
		if (data.isEmpty) {
			return;
		}

		switch (modeManager.editMode) {
		case TEMPO_MAP:
			drawBars(g);
			break;
		case VOCALS:
			drawVocalLines(g);
			break;
		case GUITAR:
			drawPhrases(g);
			drawSections(g);
			drawNotes(g);
			break;
		default:
			break;
		}

		drawBookmarks(g);
		drawMarkerAndViewArea(g);
	}

	@Override
	public void mouseClicked(final MouseEvent e) {
	}

	@Override
	public void mousePressed(final MouseEvent e) {
		data.nextTime = positionToTime(e.getX());
	}

	@Override
	public void mouseReleased(final MouseEvent e) {
	}

	@Override
	public void mouseEntered(final MouseEvent e) {
		this.requestFocusInWindow();
	}

	@Override
	public void mouseExited(final MouseEvent e) {
		frame.requestFocusInWindow();
	}

	@Override
	public void mouseDragged(final MouseEvent e) {
		data.setNextTime(positionToTime(e.getX()));
	}

	@Override
	public void mouseMoved(final MouseEvent e) {
	}
}
