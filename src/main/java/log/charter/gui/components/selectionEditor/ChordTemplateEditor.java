package log.charter.gui.components.selectionEditor;

import static java.lang.Math.max;
import static log.charter.data.ChordTemplateFingerSetter.setSuggestedFingers;
import static log.charter.data.config.Config.maxStrings;
import static log.charter.gui.components.RowedPanel.setComponentBounds;
import static log.charter.gui.components.TextInputSelectAllOnFocus.addSelectTextOnFocus;
import static log.charter.gui.components.TextInputWithValidation.ValueValidator.createIntValidator;
import static log.charter.song.ChordTemplate.fingerIds;
import static log.charter.song.ChordTemplate.fingerNames;
import static log.charter.util.Utils.getStringPosition;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import log.charter.data.ChartData;
import log.charter.data.config.Config;
import log.charter.data.config.Localization.Label;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.AutocompleteInput;
import log.charter.gui.components.ChordNameAdviceButton;
import log.charter.gui.components.ChordTemplatePreview;
import log.charter.gui.components.RowedPanel;
import log.charter.gui.components.TextInputWithValidation;
import log.charter.gui.handlers.KeyboardHandler;
import log.charter.song.ChordTemplate;
import log.charter.util.CollectionUtils.ArrayList2;
import log.charter.util.CollectionUtils.HashMap2;

public class ChordTemplateEditor implements MouseListener {
	private static final int width = 440;

	protected final RowedPanel parent;
	private boolean parentListenerAdded = false;
	private Runnable onChange;

	private ChartData data;

	private Supplier<ChordTemplate> chordTemplateSupplier;
	private boolean editingChordName = false;
	private int chordTemplateEditorRow;

	private ChordNameAdviceButton chordNameAdviceButton;
	private JLabel chordNameLabel;
	private AutocompleteInput<ChordTemplate> chordNameInput;
	private JLabel fretsLabel;
	private JLabel fingersLabel;
	private final ArrayList2<TextInputWithValidation> fretInputs = new ArrayList2<>();
	private final ArrayList2<TextInputWithValidation> fingerInputs = new ArrayList2<>();
	private ChordTemplatePreview chordTemplatePreview;

	protected ChordTemplateEditor(final RowedPanel parent) {
		this.parent = parent;
	}

	public void init(final ChartData data, final CharterFrame frame, final KeyboardHandler keyboardHandler,
			final Supplier<ChordTemplate> chordTemplateSupplier, final Runnable onChange) {
		this.onChange = onChange;

		this.data = data;

		this.chordTemplateSupplier = chordTemplateSupplier;
		chordTemplatePreview = new ChordTemplatePreview(parent, this, data, frame, keyboardHandler,
				chordTemplateSupplier);
	}

	private int calculateHeight(final int strings) {
		return 22 + strings * parent.rowHeight;
	}

	private void setCorrectPositions() {
		final int strings = data.currentStrings();

		final int x = chordTemplatePreview.getX();
		int y = chordTemplatePreview.getY();
		setComponentBounds(chordTemplatePreview, x, y, width, calculateHeight(strings));

		for (int i = 0; i < strings; i++) {
			y = parent.getY(chordTemplateEditorRow + 1 + getStringPosition(i, strings));

			final TextInputWithValidation fretInput = fretInputs.get(i);
			setComponentBounds(fretInput, fretInput.getX(), y, fretInput.getWidth(), fretInput.getHeight());

			final TextInputWithValidation fingerInput = fingerInputs.get(i);
			setComponentBounds(fingerInput, fingerInput.getX(), y, fingerInput.getWidth(), fingerInput.getHeight());
		}
	}

	protected void addChordNameSuggestionButton(final int x, final int row) {
		chordNameAdviceButton = new ChordNameAdviceButton(Label.CHORD_NAME_ADVICE, parent,
				() -> data.getCurrentArrangement().tuning, () -> chordTemplateSupplier.get().frets,
				this::onChordNameSelect);
		parent.add(chordNameAdviceButton, x, parent.getY(row), 150, 20);
	}

	protected void onChordNameSelect(final String newName) {
		chordTemplateSupplier.get().chordName = newName;
		chordNameInput.setTextWithoutUpdate(newName);
		onChange.run();

		parent.repaint();
	}

	protected void addChordNameInput(final int x, final int row) {
		parent.addLabel(1, x - 80, Label.CHORD_NAME, 80);
		chordNameLabel = (JLabel) parent.components.getLast();

		final int strings = maxStrings;
		final Function<ChordTemplate, String> formatter = chordTemplate -> chordTemplate.getNameWithFrets(strings);
		chordNameInput = new AutocompleteInput<>(parent, 80, "", this::getPossibleChords, formatter,
				this::onChordTemplateChange);
		chordNameInput.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(final DocumentEvent e) {
				changedUpdate(e);
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				changedUpdate(e);
			}

			@Override
			public void changedUpdate(final DocumentEvent e) {
				if (chordNameInput.isDisableDocumentUpdateHandling()) {
					return;
				}

				editingChordName = true;
				final String newName = chordNameInput.getText();
				chordTemplateSupplier.get().chordName = newName;
				onChange.run();
				editingChordName = false;
			}
		});

		parent.add(chordNameInput, x, parent.getY(row), 150, 20);
	}

	private ArrayList2<ChordTemplate> getPossibleChords(final String filter) {
		return data.getCurrentArrangement().chordTemplates.stream()//
				.filter(chordTemplate -> !chordTemplate.equals(chordTemplateSupplier.get())//
						&& chordTemplate.getNameWithFrets(data.currentStrings()).toLowerCase()
								.contains(filter.toLowerCase()))//
				.collect(Collectors.toCollection(ArrayList2::new));
	}

	protected void setCurrentValuesInInputs() {
		final ChordTemplate chordTemplate = chordTemplateSupplier.get();
		chordNameInput.setTextWithoutUpdate(chordTemplate.chordName);
		chordNameInput.removePopup();

		for (int i = 0; i < data.currentStrings(); i++) {
			final Integer fret = chordTemplate.frets.get(i);
			final TextInputWithValidation input = fretInputs.get(i);
			input.setTextWithoutEvent(fret == null ? "" : fret.toString());
			input.repaint();

			final Integer finger = chordTemplate.fingers.get(i);
			final TextInputWithValidation fingerInput = fingerInputs.get(i);
			fingerInput.setTextWithoutEvent(fingerNames.get(finger));
			fingerInput.repaint();
		}

		chordTemplatePreview.changeStringsAmount();
		setCorrectPositions();
		chordTemplatePreview.repaint();
	}

	private void onChordTemplateChange(final ChordTemplate newChordTemplate) {
		final ChordTemplate chordTemplate = chordTemplateSupplier.get();

		chordTemplate.chordName = newChordTemplate.chordName;
		chordTemplate.arpeggio = newChordTemplate.arpeggio;
		chordTemplate.fingers = new HashMap2<>(newChordTemplate.fingers);
		chordTemplate.frets = new HashMap2<>(newChordTemplate.frets);

		setCurrentValuesInInputs();

		onChange.run();

		parent.repaint();
	}

	public void addChordTemplateEditor(final int baseX, final int row) {
		chordTemplateEditorRow = row;
		int x = baseX;

		final int fretLabelWidth = parent.addLabel(row, x, Label.FRET, 50);
		fretsLabel = (JLabel) parent.components.getLast();
		fretsLabel.setHorizontalAlignment(JLabel.CENTER);
		final int fretInputX = x + fretLabelWidth / 2 - 15;
		for (int i = 0; i < maxStrings; i++) {
			final int string = i;

			final TextInputWithValidation input = new TextInputWithValidation(null, 40,
					createIntValidator(0, Config.frets, true), (final Integer val) -> updateFretValue(string, val),
					false);
			input.setHorizontalAlignment(JTextField.CENTER);
			addSelectTextOnFocus(input);

			fretInputs.add(input);
			parent.add(input, fretInputX, parent.getY(row + 1 + getStringPosition(i, maxStrings)), 30, 20);
		}

		x += 5 + max(fretLabelWidth, 40);

		final int fingerLabelWidth = parent.addLabel(row, x, Label.CHORD_TEMPLATE_FINGER, 50);
		fingersLabel = (JLabel) parent.components.getLast();
		fingersLabel.setHorizontalAlignment(JLabel.CENTER);

		final int fingerInputX = x + fingerLabelWidth / 2 - 10;
		for (int i = 0; i < maxStrings; i++) {
			final int string = i;

			final TextInputWithValidation input = new TextInputWithValidation("", 40, this::validateFinger,
					val -> updateFingerValue(string, val), false);
			input.setHorizontalAlignment(JTextField.CENTER);
			addSelectTextOnFocus(input);

			fingerInputs.add(input);
			parent.add(input, fingerInputX, parent.getY(row + 1 + getStringPosition(string, maxStrings)), 20, 20);
		}
		x += 5 + max(fingerLabelWidth, 20);

		final int y = parent.getY(row);
		parent.add(chordTemplatePreview, x, y, width, calculateHeight(maxStrings));
	}

	private String validateFinger(final String val) {
		if (val == null || val.isEmpty()) {
			return null;
		}
		if (fingerIds.containsKey(val)) {
			return null;
		}

		return Label.WRONG_FINGER_VALUE.label();
	}

	private void updateFretValue(final int string, final Integer fret) {
		if (fret == null) {
			chordTemplateSupplier.get().frets.remove(string);
		} else {
			chordTemplateSupplier.get().frets.put(string, Integer.valueOf(fret));
		}

		updateFingers();
		onChange.run();

		chordTemplatePreview.repaint();
	}

	private void updateFingerValue(final int string, final String finger) {
		if (finger == null || finger.isEmpty()) {
			chordTemplateSupplier.get().fingers.remove(string);
		} else {
			chordTemplateSupplier.get().fingers.put(string, fingerIds.get(finger));
		}

		onChange.run();

		chordTemplatePreview.repaint();
	}

	private void updateFingers() {
		setSuggestedFingers(chordTemplateSupplier.get());
		for (int i = 0; i < Config.maxStrings; i++) {
			fingerUpdated(i, chordTemplateSupplier.get().fingers.get(i));
		}
	}

	public void fretUpdated(final int string, final Integer fret) {
		final TextInputWithValidation input = fretInputs.get(string);
		input.setTextWithoutEvent(fret == null ? "" : fret.toString());
		input.repaint();
		updateFingers();

		onChange.run();

		chordTemplatePreview.repaint();
	}

	public void fingerUpdated(final int string, final Integer finger) {
		final TextInputWithValidation input = fingerInputs.get(string);
		input.setTextWithoutEvent(fingerNames.get(finger));
		input.repaint();

		onChange.run();

		chordTemplatePreview.repaint();
	}

	@Override
	public void mouseClicked(final MouseEvent e) {
		if (e.getComponent() != null) {
			e.getComponent().requestFocus();
		} else {
			parent.requestFocus();
		}
	}

	@Override
	public void mousePressed(final MouseEvent e) {
	}

	@Override
	public void mouseReleased(final MouseEvent e) {
	}

	@Override
	public void mouseEntered(final MouseEvent e) {
	}

	@Override
	public void mouseExited(final MouseEvent e) {
	}

	public void showFields() {
		chordNameAdviceButton.setVisible(true);
		chordNameLabel.setVisible(true);
		chordNameInput.setVisible(true);
		fretsLabel.setVisible(true);
		fingersLabel.setVisible(true);

		for (int i = 0; i < data.getCurrentArrangement().tuning.strings; i++) {
			fretInputs.get(i).setVisible(true);
			fingerInputs.get(i).setVisible(true);
		}
		for (int i = data.getCurrentArrangement().tuning.strings; i < maxStrings; i++) {
			fretInputs.get(i).setVisible(false);
			fingerInputs.get(i).setVisible(false);
		}

		if (!parentListenerAdded) {
			parent.addMouseListener(this);
			parentListenerAdded = true;
		}

		chordTemplatePreview.showFields();
	}

	public void hideFields() {
		chordNameAdviceButton.setVisible(false);
		chordNameAdviceButton.removePopup();
		chordNameLabel.setVisible(false);
		chordNameInput.setVisible(false);
		chordNameInput.removePopup();
		fretsLabel.setVisible(false);
		fingersLabel.setVisible(false);

		for (final TextInputWithValidation fretInput : fretInputs) {
			fretInput.setVisible(false);
		}
		for (final TextInputWithValidation fingerInput : fingerInputs) {
			fingerInput.setVisible(false);
		}

		if (parentListenerAdded) {
			parent.removeMouseListener(this);
			parentListenerAdded = false;
		}

		chordTemplatePreview.hideFields();
	}

	public void clearChordName() {
		if (editingChordName) {
			return;
		}

		chordNameInput.setTextWithoutUpdate("");
		chordNameInput.removePopup();
	}
}
