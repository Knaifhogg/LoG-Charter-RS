package log.charter.data.copySystem.data.positions;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

import log.charter.song.BeatsMap;
import log.charter.song.ToneChange;

@XStreamAlias("copiedToneChange")
public class CopiedToneChangePosition extends CopiedPosition<ToneChange> {
	@XStreamAsAttribute
	public final String toneName;

	public CopiedToneChangePosition(final BeatsMap beatsMap, final double baseBeatPosition,
			final ToneChange toneChange) {
		super(beatsMap, baseBeatPosition, toneChange);
		toneName = toneChange.toneName;
	}

	@Override
	protected ToneChange prepareValue() {
		final ToneChange toneChange = new ToneChange();
		toneChange.toneName = toneName;

		return toneChange;
	}
}
