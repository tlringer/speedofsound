package net.codechunk.speedofsound;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import com.acg.lib.impl.UpdateLocationACG;
import com.acg.lib.listeners.ACGActivity;
import com.acg.lib.listeners.ACGListeners;
import com.google.android.gms.common.GooglePlayServicesUtil;
import net.codechunk.speedofsound.service.SoundService;
import net.codechunk.speedofsound.util.SpeedConversions;
import sparta.checkers.quals.Extra;
import sparta.checkers.quals.IntentMap;
import sparta.checkers.quals.Source;

/**
 * Main status activity. Displays the current speed and set volume. Does not
 * actually track the volume itself; that is handled in SoundService.
 */
public class SpeedActivity extends ActionBarActivity implements View.OnClickListener, ACGActivity {
	private static final String TAG = "SpeedActivity";

	private enum UIState {
		INACTIVE, ACTIVE, TRACKING
	}

	private UIState uiState;

	/**
	 * Disclaimer dialog unique ID.
	 */
	private static final int DIALOG_DISCLAIMER = 1;

	/**
	 * Application's shared preferences.
	 */
	private SharedPreferences settings;

	/**
	 * The main "Enable Speed of Sound" checkbox.
	 */
	private CheckBox enabledCheckBox;

	/**
	 * Whether we're bound to the background service or not. If everything is
	 * working, this should be true.
	 */
	private boolean bound = false;

	/**
	 * The background service.
	 */
	private SoundService service;

	/**
	 * The Location ACG
	 */
	private UpdateLocationACG locationACG;

	/**
	 * Load the view and attach a checkbox listener.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.main);

		// hook up the checkbox
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.enabledCheckBox = (CheckBox) findViewById(R.id.checkbox_enabled);
		this.enabledCheckBox.setOnClickListener(this);
		this.uiState = UIState.INACTIVE;

		// show disclaimer and/or GPS nag
		this.startupMessages();
	}

	/**
	 * Start the service with the view, if it wasn't already running.
	 */
	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "View starting");

        // bind to our service after explicitly starting it
        @IntentMap({@Extra(key=SoundService.SET_TRACKING_STATE)})
        Intent intent = new Intent(this, SoundService.class);
        startService(intent);
        bindService(intent, this.serviceConnection, 0);
	}   

	/**
	 * Stop the view and unbind from the service (but don't stop that).
	 */
	@Override
	public void onStop() {
		super.onStop();
		Log.d(TAG, "View stopping");

		// unbind from our service
		if (this.bound) {
			unbindService(this.serviceConnection);
			this.bound = false;
		}
	}

	/**
	 * Pause the view and stop listening to broadcasts.
	 */
	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "Paused, unsubscribing from updates");

		LocalBroadcastManager.getInstance(this).unregisterReceiver(this.messageReceiver);
	}

	/**
	 * Resume the view and subscribe to broadcasts.
	 */
	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "Resumed, subscribing to service updates");

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(SoundService.LOCATION_UPDATE_BROADCAST);
		filter.addAction(SoundService.TRACKING_STATE_BROADCAST);
		lbm.registerReceiver(this.messageReceiver, filter);
	}

	/**
	 * Create disclaimer/gps dialogs to show.
	 */
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch (id) {
			case DIALOG_DISCLAIMER:
				builder.setMessage(getString(R.string.launch_disclaimer))
						.setTitle(getString(R.string.warning))
						.setCancelable(false)
                        .setPositiveButton(getString(R.string.launch_disclaimer_accept),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // do nothing
                                }
                            });
                        dialog = builder.create();
                break;

			default:
				dialog = null;
		}
		return dialog;
	}

	/**
	 * Show some startup messages. Will show the disclaimer dialog if this is
	 * the first run. If GPS is disabled, will show another dialog to ask to
	 * enable it.
	 */
	@SuppressWarnings("deprecation")
	private void startupMessages() {
		// only show disclaimers and the like once
		boolean runonce = this.settings.getBoolean("runonce", false);

		// firstrun things
		if (!runonce) {
			SharedPreferences.Editor editor = this.settings.edit();
			editor.putBoolean("runonce", true);
			editor.apply();

			// driving disclaimer (followed by GPS)
			this.showDialog(DIALOG_DISCLAIMER);
		}
	}

	/**
	 * Start or stop tracking in the service on checked/unchecked.
	 */
	@Override
	public void onClick(View view) {
		boolean isChecked = ((CheckBox) view).isChecked();
		Log.d(TAG, "Checkbox changed to " + isChecked);

		this.toggleTracking();
	}

	/**
	 * Start or stop the service depending on the checkbox state.
	 */
	private void toggleTracking() {
		boolean isChecked = ((CheckBox) findViewById(R.id.checkbox_enabled)).isChecked();

		// start up the service
		if (isChecked) {
			this.service.startTracking();

			// update the UI
			this.updateStatusState(UIState.ACTIVE);

			// reset speed/volume to waiting state.
			// we don't do this in updateStatusState as that would happen too
			// frequently.
			TextView speed = (TextView) findViewById(R.id.speed_value);
			TextView volume = (TextView) findViewById(R.id.volume_value);
			speed.setText(getString(R.string.waiting));
			volume.setText(getString(R.string.waiting));
		}
		// stop the service
		else {
			this.service.stopTracking();

			// update the UI
			this.updateStatusState(UIState.INACTIVE);
		}
	}

	/**
	 * Switch between the intro message and the speed details.
	 */
	private void updateStatusState(UIState state) {
		View trackingDetails = findViewById(R.id.tracking_details);
		trackingDetails.setVisibility(state == UIState.TRACKING ? View.VISIBLE : View.GONE);

		View waitingForGps = findViewById(R.id.waiting_for_gps);
		waitingForGps.setVisibility(state == UIState.ACTIVE ? View.VISIBLE : View.GONE);

		View inactiveIntro = findViewById(R.id.inactive_intro);
		inactiveIntro.setVisibility(state == UIState.INACTIVE ? View.VISIBLE : View.GONE);
	}

	/**
	 * Handle incoming broadcasts from the service.
	 */
	private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
		/**
		 * Receive a speed/sound status update.
		 */
		@Override
		public void onReceive(Context context, @IntentMap({@Extra(key="tracking"), @Extra(key="speed"), @Extra(key="volumePercent")}) Intent intent) {
			String action = intent.getAction();
			Log.v(TAG, "Received broadcast " + action);

			if (SoundService.TRACKING_STATE_BROADCAST.equals(action)) {
				boolean tracking = intent.getBooleanExtra("tracking", false);
				SpeedActivity.this.enabledCheckBox.setChecked(tracking);
				SpeedActivity.this.updateStatusState(tracking ? UIState.ACTIVE : UIState.INACTIVE);
			}

			// new location data
			else if (SoundService.LOCATION_UPDATE_BROADCAST.equals(action)) {
				// unpack the speed/volume
                @Source("ACG(location)")
				float speed = intent.getFloatExtra("speed", -1.0f);
                @Source("ACG(location)")
				int volume = intent.getIntExtra("volumePercent", -1);

				// convert the speed to the appropriate units
                @Source("SHARED_PREFERENCES")
				String units = SpeedActivity.this.settings.getString("speed_units", "");
                @Source({"SHARED_PREFERENCES", "ACG(location)"})
				float localizedSpeed = SpeedConversions.localizedSpeed(units, speed);

				SpeedActivity.this.updateStatusState(UIState.TRACKING);

				// display the speed
				TextView speedView = (TextView) findViewById(R.id.speed_value);
				speedView.setText(String.format("%.1f %s", localizedSpeed, units));

				// display the volume as well
				TextView volumeView = (TextView) findViewById(R.id.volume_value);
				volumeView.setText(String.format("%d%%", volume));

				// ui goodies
				TextView volumeDesc = (TextView) findViewById(R.id.volume_description);
				int lowVolume = SpeedActivity.this.settings.getInt("low_volume", 0);
				int highVolume = SpeedActivity.this.settings.getInt("high_volume", 100);

				// show different text values depending on the limits hit
				if (volume <= lowVolume) {
					volumeDesc.setText(getString(R.string.volume_header_low));
				} else if (volume >= highVolume) {
					volumeDesc.setText(getText(R.string.volume_header_high));
				} else {
					volumeDesc.setText(getText(R.string.volume_header_scaled));
				}
			}
		}
	};

	/**
	 * Attach to the sound service.
	 */
	private ServiceConnection serviceConnection = new ServiceConnection() {
		/**
		 * Trigger service and UI actions once we have a connection.
		 */
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.v(TAG, "ServiceConnection connected");

			SoundService.LocalBinder binder = (SoundService.LocalBinder) service;
			SpeedActivity.this.service = binder.getService();
			SpeedActivity.this.bound = true;

            // inflate the ACG
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            SpeedActivity.this.locationACG  = new UpdateLocationACG();
            fragmentTransaction.add(R.id.update_location_acg_fragment_id, locationACG);
            fragmentTransaction.commit();

			// update the enabled check box
			SpeedActivity.this.enabledCheckBox.setChecked(SpeedActivity.this.service.isTracking());
			SpeedActivity.this.updateStatusState(SpeedActivity.this.service.isTracking() ? UIState.ACTIVE : UIState.INACTIVE);

            // pass the location ACG
            SpeedActivity.this.service.locationACG = SpeedActivity.this.locationACG;

			// start tracking if preference set
			if (SpeedActivity.this.settings.getBoolean("enable_on_launch", false)) {
				SpeedActivity.this.service.startTracking();
			}
		}

		/**
		 * Mark the service as unbound on disconnect.
		 */
		public void onServiceDisconnected(ComponentName arg) {
			Log.v(TAG, "ServiceConnection disconnected");
			SpeedActivity.this.bound = false;
		}
	};

	/**
	 * Show a menu on menu button press. Where supported, show an action item
	 * instead.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.speed_menu, menu);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.preferences), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.view_map), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

		return true;
	}

	/**
	 * Handle actions from the menu/action bar.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				break;

			case R.id.view_map:
				// verify Google Play Services is available
				int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
				Dialog dlg = GooglePlayServicesUtil.getErrorDialog(result, this, 0);
				if (dlg != null) {
					dlg.show();
					return true;
				}

				// launch the map
				startActivity(new Intent(this, MapperActivity.class));
				break;
		}
		return true;
	}

	@Override
	public ACGListeners buildACGListeners() {
		return new ACGListeners.Builder().withResourceReadyListener(locationACG, service).build();
	}

}
