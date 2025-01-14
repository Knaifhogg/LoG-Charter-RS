package log.charter.song.notes;

import static java.lang.Math.max;
import static log.charter.data.ArrangementFixer.fixNoteLength;
import static log.charter.data.config.Config.minNoteDistance;

import java.util.Collection;
import java.util.List;

import log.charter.data.managers.selection.Selection;
import log.charter.song.BeatsMap;
import log.charter.util.CollectionUtils.ArrayList2;

public interface IPositionWithLength extends IPosition {
	public static class EndPosition implements IPosition {
		private final IPositionWithLength position;

		public EndPosition(final IPositionWithLength position) {
			this.position = position;
		}

		@Override
		public int position() {
			return position.endPosition();
		}

		@Override
		public void position(final int newPosition) {
			position.endPosition(newPosition);
		}
	}

	public static List<IPosition> getAsEndPositions(final Collection<? extends IPositionWithLength> positions) {
		return positions.stream().map(IPositionWithLength::asEndPosition).toList();
	}

	public static <PwL extends IPositionWithLength, P extends IPosition> void changePositionsWithLengthsLength(
			final BeatsMap beatsMap, final ArrayList2<Selection<PwL>> toChange, final ArrayList2<P> allPositions,
			final int change) {
		for (final Selection<PwL> selected : toChange) {
			final IPositionWithLength positionWithLength = selected.selectable;
			int endPosition = positionWithLength.endPosition();
			endPosition = change > 0 ? beatsMap.getPositionWithAddedGrid(endPosition, change)
					: beatsMap.getPositionWithRemovedGrid(endPosition, -change);

			if (selected.id + 1 < allPositions.size()) {
				final IPosition next = allPositions.get(selected.id + 1);
				if (next.position() - endPosition < minNoteDistance) {
					endPosition = next.position() - minNoteDistance;
				}
			}

			final int length = max(0, endPosition - positionWithLength.position());
			positionWithLength.length(length);
		}
	}

	private static boolean soundHasStringFromSelected(final List<Integer> selectedStrings, final ChordOrNote sound) {
		if (sound.isNote()) {
			return selectedStrings.contains(sound.note.string);
		}

		for (final int string : sound.chord.chordNotes.keySet()) {
			if (selectedStrings.contains(string)) {
				return true;
			}
		}

		return false;
	}

	private static void changeNoteLength(final BeatsMap beatsMap, final ArrayList2<ChordOrNote> allPositions,
			final CommonNote note, final int id, final int change) {
		if (note.linkNext()) {
			fixNoteLength(note, id, allPositions);
			return;
		}

		final int newEndPosition = change > 0 ? beatsMap.getPositionWithAddedGrid(note.endPosition(), change)
				: beatsMap.getPositionWithRemovedGrid(note.endPosition(), -change);
		note.endPosition(newEndPosition);

		fixNoteLength(note, id, allPositions);
	}

	public static void changeSoundsLength(final BeatsMap beatsMap, final ArrayList2<Selection<ChordOrNote>> toChange,
			final ArrayList2<ChordOrNote> allPositions, final int change, final boolean cutBeforeNext,
			final List<Integer> selectedStrings) {
		for (final Selection<ChordOrNote> selected : toChange) {
			final ChordOrNote sound = selected.selectable;
			if (!soundHasStringFromSelected(selectedStrings, sound)) {
				continue;
			}

			if (sound.isNote()) {
				changeNoteLength(beatsMap, allPositions, CommonNote.create(sound.note), selected.id, change);
			} else {
				sound.chord.chordNotes.forEach((string, chordNote) -> {
					if (!selectedStrings.contains(string)) {
						return;
					}

					changeNoteLength(beatsMap, allPositions, CommonNote.create(sound.chord, string, chordNote),
							selected.id, change);
				});
			}
		}
	}

	public static <T extends IPositionWithLength> int findFirstIdAfterEqual(final ArrayList2<T> list,
			final int position) {
		if (list.isEmpty()) {
			return -1;
		}
		if (position > list.getLast().endPosition()) {
			return -1;
		}

		int minId = 0;
		int maxId = list.size() - 1;
		while (maxId - minId > 1) {
			final int id = (minId + maxId) / 2;
			if (list.get(id).endPosition() < position) {
				minId = id + 1;
			} else {
				maxId = id;
			}
		}

		return list.get(minId).endPosition() < position ? maxId : minId;
	}

	public static <T extends IPositionWithLength> int findLastIdBefore(final List<T> list, final int position) {
		if (list.isEmpty()) {
			return -1;
		}

		if (position <= list.get(0).position()) {
			return -1;
		}

		int minId = 0;
		int maxId = list.size() - 1;
		while (maxId - minId > 1) {
			final int id = (minId + maxId) / 2;
			if (list.get(id).endPosition() >= position) {
				maxId = id - 1;
			} else {
				minId = id;
			}
		}

		return list.get(maxId).endPosition() >= position ? minId : maxId;
	}

	int length();

	void length(int newLength);

	default int endPosition() {
		return position() + length();
	}

	default void endPosition(final int newEndPosition) {
		length(newEndPosition - position());
	}

	default IPosition asEndPosition() {
		return new EndPosition(this);
	}
}
