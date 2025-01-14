package log.charter.song;

import static log.charter.song.notes.IPosition.findLastBefore;

import log.charter.song.notes.IPosition;
import log.charter.util.CollectionUtils.ArrayList2;

public class OnBeat implements IPosition {
	public Beat beat;

	public OnBeat(final Beat beat) {
		this.beat = beat;
	}

	protected OnBeat(final ArrayList2<Beat> beats, final int position) {
		beat = findLastBefore(beats, position + 1);
	}

	public OnBeat(final ArrayList2<Beat> beats, final OnBeat other) {
		this(beats, other.beat.position());
	}

	@Override
	public int position() {
		return beat.position();
	}

	@Override
	public void position(final int newPosition) {
		throw new RuntimeException("can't set position of onBeat");
	}
}
