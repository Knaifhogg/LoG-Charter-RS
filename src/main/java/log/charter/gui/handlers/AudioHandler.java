package log.charter.gui.handlers;

import static java.lang.System.nanoTime;
import static log.charter.data.config.Config.createDefaultStretchesInBackground;
import static log.charter.data.config.Config.stretchedMusicSpeed;
import static log.charter.song.notes.IPosition.findFirstAfter;
import static log.charter.sound.MusicData.generateSound;

import java.util.function.Supplier;

import log.charter.data.ChartData;
import log.charter.data.config.Config;
import log.charter.data.config.Localization.Label;
import log.charter.data.managers.ModeManager;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.toolbar.ChartToolbar;
import log.charter.gui.handlers.midiPlayer.MidiChartNotePlayer;
import log.charter.song.notes.IPosition;
import log.charter.sound.IPlayer;
import log.charter.sound.MusicData;
import log.charter.sound.RepeatingPlayer;
import log.charter.sound.RotatingRepeatingPlayer;
import log.charter.sound.SoundPlayer;
import log.charter.sound.SoundPlayer.Player;
import log.charter.util.CollectionUtils.ArrayList2;

public class AudioHandler {
	private static class TickPlayer {
		private final IPlayer tickPlayer;
		private final Supplier<ArrayList2<? extends IPosition>> positionsSupplier;

		public boolean on = false;
		private int nextTime = -1;

		public TickPlayer(final IPlayer tickPlayer, final Supplier<ArrayList2<? extends IPosition>> positionsSupplier) {
			this.tickPlayer = tickPlayer;
			this.positionsSupplier = positionsSupplier;
		}

		public void handleFrame(final int t) {
			if (nextTime != -1 && nextTime < t) {
				tickPlayer.play();
				nextTime = -1;
			}

			if (on && nextTime == -1) {
				final IPosition nextPosition = findFirstAfter(positionsSupplier.get(), t);
				if (nextPosition != null) {
					nextTime = nextPosition.position();
				}
			}
		}

		public void stop() {
			nextTime = -1;
		}
	}

	private ChartToolbar chartToolbar;
	private ChartData data;
	private CharterFrame frame;
	private ModeManager modeManager;

	private final StretchedAudioHandler stretchedAudioHandler = new StretchedAudioHandler();

	private MusicData slowedDownSong;
	private int currentlyLoadedSpecialSpeed = 100;

	private TickPlayer beatTickPlayer;
	private TickPlayer noteTickPlayer;
	private final MidiChartNotePlayer midiChartNotePlayer = new MidiChartNotePlayer();
	private Player songPlayer;

	private int speed = 100;
	private int songTimeOnStart = 0;
	private long playStartTime;

	private final boolean ignoreStops = false;
	public boolean midiNotesPlaying = false;

	public void init(final ChartToolbar chartToolbar, final ChartData data, final CharterFrame frame,
			final ModeManager modeManager) {
		this.chartToolbar = chartToolbar;
		this.data = data;
		this.frame = frame;
		this.modeManager = modeManager;

		beatTickPlayer = new TickPlayer(new RepeatingPlayer(generateSound(4000, 0.01, 1)), //
				() -> data.songChart.beatsMap.beats);
		noteTickPlayer = new TickPlayer(new RotatingRepeatingPlayer(generateSound(1000, 0.02, 0.8), 4), //
				this::getCurrentClapPositions);
		midiChartNotePlayer.init(data, modeManager);

		stretchedAudioHandler.init();
	}

	private ArrayList2<? extends IPosition> getCurrentClapPositions() {
		switch (modeManager.editMode) {
		case GUITAR:
			return data.getCurrentArrangementLevel().chordsAndNotes;
		case TEMPO_MAP:
			return data.songChart.beatsMap.beats;
		case VOCALS:
			return data.songChart.vocals.vocals;
		default:
			return new ArrayList2<>();
		}
	}

	public void toggleMidiNotes() {
		if (midiNotesPlaying) {
			if (songPlayer != null) {
				midiChartNotePlayer.stopPlaying();
			}
			midiNotesPlaying = false;
		} else {
			if (songPlayer != null) {
				midiChartNotePlayer.startPlaying(speed);
			}
			midiNotesPlaying = true;
		}

		chartToolbar.updateValues();
	}

	public void toggleClaps() {
		noteTickPlayer.on = !noteTickPlayer.on;

		chartToolbar.updateValues();
	}

	public boolean claps() {
		return noteTickPlayer.on;
	}

	public void toggleMetronome() {
		beatTickPlayer.on = !beatTickPlayer.on;

		chartToolbar.updateValues();
	}

	public boolean metronome() {
		return beatTickPlayer.on;
	}

	private void playMusic(final MusicData musicData, final int speed) {
		stop();

		this.speed = speed;
		songPlayer = SoundPlayer.play(musicData.volume(Config.volume), data.time * 100 / speed);
		songTimeOnStart = data.time;
		playStartTime = nanoTime() / 1_000_000L;

		if (midiNotesPlaying) {
			midiChartNotePlayer.startPlaying(speed);
		}
	}

	private void stop() {
		if (songPlayer == null) {
			return;
		}

		songPlayer.stop();
		songPlayer = null;
		beatTickPlayer.stop();
		noteTickPlayer.stop();

		if (midiNotesPlaying) {
			midiChartNotePlayer.stopPlaying();
		}
	}

	public void stopMusic() {
		if (ignoreStops) {
			return;
		}

		stop();
	}

	public void clear() {
		currentlyLoadedSpecialSpeed = 100;
		slowedDownSong = null;
		stopMusic();
	}

	public void setSong() {
		stretchedAudioHandler.clear();
		stretchedAudioHandler.setData(data.path, data.music);

		if (createDefaultStretchesInBackground) {
			stretchedAudioHandler.addSpeedToGenerate(stretchedMusicSpeed);
			stretchedAudioHandler.addSpeedToGenerate(50);
			stretchedAudioHandler.addSpeedToGenerate(25);
			stretchedAudioHandler.addSpeedToGenerate(75);
		}
	}

	public void addSpeedToStretch() {
		stretchedAudioHandler.addSpeedToGenerate(stretchedMusicSpeed);
	}

	public void togglePlayNormalSpeed() {
		if (data.isEmpty) {
			return;
		}

		if (songPlayer != null) {
			stopMusic();
			return;
		}

		playMusic(data.music, 100);
	}

	public void togglePlaySetSpeed() {
		if (data.isEmpty) {
			return;
		}

		if (songPlayer != null) {
			stopMusic();
			return;
		}

		if (currentlyLoadedSpecialSpeed == stretchedMusicSpeed && slowedDownSong != null) {
			playMusic(slowedDownSong, currentlyLoadedSpecialSpeed);
			return;
		}

		currentlyLoadedSpecialSpeed = stretchedMusicSpeed;
		slowedDownSong = stretchedAudioHandler.get(stretchedMusicSpeed);
		if (slowedDownSong != null) {
			playMusic(slowedDownSong, currentlyLoadedSpecialSpeed);
		} else {
			stretchedAudioHandler.addSpeedToGenerate(currentlyLoadedSpecialSpeed);
			frame.showPopup(Label.GENERATING_SLOWED_SOUND.label());
		}
	}

	public void frame() {
		if (songPlayer == null) {
			return;
		}
		if (songPlayer.isStopped()) {
			stopMusic();
		}

		final int timePassed = (int) ((nanoTime() / 1_000_000 - playStartTime) * speed / 100);
		final int nextTime = songTimeOnStart + timePassed;
		frame.setNextTime(nextTime);

		beatTickPlayer.handleFrame(nextTime);
		noteTickPlayer.handleFrame(nextTime);
		midiChartNotePlayer.frame();
	}
}
