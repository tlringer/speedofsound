package net.codechunk.speedofsound.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import net.codechunk.speedofsound.R;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;

import static sparta.checkers.quals.FlowPermissionString.DISPLAY;
import static sparta.checkers.quals.FlowPermissionString.SHARED_PREFERENCES;

/**
 * A preference that is displayed as a seek bar.
 */
public class SliderPreference extends DialogPreference implements OnSeekBarChangeListener {
	/**
	 * Our custom namespace for this preference.
	 */
	protected static final String LOCAL_NS = "http://schemas.android.com/apk/res/net.codechunk.speedofsound";

	/**
	 * Current context.
	 */
    @Sink({})
	protected Context context;

	/**
	 * Seek bar widget to control preference value.
	 */
	protected SeekBar seekBar;

	/**
	 * Text view displaying the value of the seek bar.
	 */
	protected TextView valueDisplay;

	/**
	 * Dialog view.
	 */
	protected View view;

	/**
	 * Minimum value.
	 */
    @Source({})
	protected final int minValue;

	/**
	 * Maximum value.
	 */
	protected final int maxValue;

	/**
	 * Units of this preference.
	 */
    @Source(SHARED_PREFERENCES)
	protected String units;

	/**
	 * Current value.
	 */
    @Sink({DISPLAY, SHARED_PREFERENCES})
	protected int value;

	/**
	 * Create a new slider preference.
	 *
	 * @param context Context to use
	 * @param attrs   XML attributes to load
	 */
	public SliderPreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		this.context = context;
		this.minValue = attrs.getAttributeIntValue(LOCAL_NS, "minValue", 0);
		this.maxValue = attrs.getAttributeIntValue(LOCAL_NS, "maxValue", 0);
		this.units = attrs.getAttributeValue(LOCAL_NS, "units");
	}

	/**
	 * Set the initial value.
	 * Needed to properly load the default value.
	 */
	@Override
	protected void onSetInitialValue(@Sink(SHARED_PREFERENCES) boolean restorePersistedValue, @Sink({SHARED_PREFERENCES}) Object defaultValue) {
		if (restorePersistedValue) {
			// Restore existing state
			this.value = this.getPersistedInt(-1);
		} else {
			// Set default state from the XML attribute
			this.value = (/*@Sink({"SHARED_PREFERENCES", "DISPLAY"})*/ Integer) defaultValue;
			persistInt(this.value);
		}
	}

	/**
	 * Support loading a default value.
	 */
	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInteger(index, -1);
	}

	/**
	 * Set up the preference display.
	 */
	@Override
	protected View onCreateDialogView() {
		// reload the persisted value since onSetInitialValue is only performed once
		// for the activity, not each time the preference is opened
		this.value = getPersistedInt(-1);

		// load the layout
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.view = inflater.inflate(R.layout.slider_preference_dialog, null);

		// setup the slider
		this.seekBar = (SeekBar) view.findViewById(R.id.slider_preference_seekbar);
		this.seekBar.setMax(this.maxValue - this.minValue);
		this.seekBar.setProgress(this.value - this.minValue);
		this.seekBar.setOnSeekBarChangeListener(this);

		this.valueDisplay = (TextView) this.view.findViewById(R.id.slider_preference_value);
		this.updateDisplay();

		return this.view;
	}

	/**
	 * Save on dialog close.
	 */
	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		if (!positiveResult) {
			return;
		}

		if (shouldPersist()) {
			this.persistInt(this.value);
		}

		this.notifyChanged();
	}

	protected void updateDisplay() {
		String text = Integer.toString(this.value);
		if (this.units != null) {
			text += this.units;
		}
		this.valueDisplay.setText(text);
	}

	/**
	 * Updated the displayed value on change.
	 */
	public void onProgressChanged(SeekBar seekBar, @Sink(DISPLAY) int value, boolean fromTouch) {
		this.value = (/*@Sink({"SHARED_PREFERENCES", "DISPLAY"})*/ int) value + this.minValue;
		this.updateDisplay();
	}

	public void onStartTrackingTouch(SeekBar sb) {
	}

	public void onStopTrackingTouch(SeekBar sb) {
	}

}
