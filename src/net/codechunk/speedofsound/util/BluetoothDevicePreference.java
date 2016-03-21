package net.codechunk.speedofsound.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import net.codechunk.speedofsound.R;
import sparta.checkers.quals.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static sparta.checkers.quals.FlowPermissionString.BLUETOOTH;
import static sparta.checkers.quals.FlowPermissionString.SHARED_PREFERENCES;

/**
 * A preference dialog listing saved and paired Bluetooth devices.
 */
public class BluetoothDevicePreference extends DialogPreference {
    private static final String TAG = "BluetoothPreference";

    public static final String KEY = "enable_bluetooth_devices";

    @Source({SHARED_PREFERENCES, BLUETOOTH}) protected Set</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String> value;

    private View view;
    private List<PrettyBluetoothDevice> adapterDevices = new ArrayList<>();

    public BluetoothDevicePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            this.value = getPersistedDevices();
        } else {
            this.value = new HashSet<>();
            persistDevices(this.value);
        }
    }

    @Override
    protected View onCreateDialogView() {
        super.onCreateDialogView();

        // reload persisted values (onSetInitialValue only called for first init)
        this.value = getPersistedDevices();

        // inflate it all
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.view = inflater.inflate(R.layout.bluetooth_preference_dialog, null);

        // get our paired music bluetooth devices
        this.adapterDevices.clear();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            Set<BluetoothDevice> deviceSet = adapter.getBondedDevices();
            for (BluetoothDevice bluetoothDevice : deviceSet) {
                BluetoothClass bluetoothClass = bluetoothDevice.getBluetoothClass();
                if (bluetoothClass.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    this.adapterDevices.add(new PrettyBluetoothDevice(bluetoothDevice));
                } else {
                    Log.d(TAG, "Skipping " + bluetoothDevice.getName() + " with class " + bluetoothClass.getDeviceClass());
                }
            }
        }

        // add them to the list
        ListView list = (ListView) this.view.findViewById(R.id.bluetooth_preference_listview);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<PrettyBluetoothDevice> listAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, this.adapterDevices);
        list.setAdapter(listAdapter);
        setCheckedDevices(this.value);

        return this.view;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (!positiveResult) {
            return;
        }

        if (shouldPersist()) {
            persistDevices(getCheckedDevices());
        }

        notifyChanged();
    }

    private @Source({SHARED_PREFERENCES}) Set</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String> getPersistedDevices() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return (Set</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String>) sharedPreferences.getStringSet(getKey(), new HashSet<String>());
    }

    private void persistDevices(@Sink(SHARED_PREFERENCES) Set</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String> items) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(getKey(), (Set</*@Sink("SHARED_PREFERENCES")*/ String>) items);
        editor.apply();
    }

    /**
     * Get a set of checked devices from this dialog's ListView.
     */
    private @Source(BLUETOOTH) Set</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String> getCheckedDevices() {
        ListView list = (ListView) this.view.findViewById(R.id.bluetooth_preference_listview);
        @Source(BLUETOOTH) Set</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String> devices = new HashSet</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String>();

        SparseBooleanArray checked = list.getCheckedItemPositions();
        int size = list.getAdapter().getCount();
        for (int i = 0; i < size; i++) {
            if (!checked.get(i)) {
                continue;
            }

            PrettyBluetoothDevice device = this.adapterDevices.get(i);
            devices.add(device.getAddress());
        }

        return devices;
    }

    /**
     * Set the checked devices in the displayed ListView.
     */
    private void setCheckedDevices(@Sink("DISPLAY") Set</*@Source({"SHARED_PREFERENCES", "BLUETOOTH"})*/ String> devices) {
        ListView list = (ListView) this.view.findViewById(R.id.bluetooth_preference_listview);

        int size = list.getAdapter().getCount();
        for (int i = 0; i < size; i++) {
            if (devices.contains(this.adapterDevices.get(i).getAddress())) {
                list.setItemChecked(i, true);
            }
        }
    }

    /**
     * Bluetooth device with a nice toString().
     */
    private class PrettyBluetoothDevice {
        @Source(BLUETOOTH)
        private BluetoothDevice device;

        PrettyBluetoothDevice(@Source(BLUETOOTH) BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public @Source(BLUETOOTH) String toString() {
            return this.device.getName();
        }

        public @Source(BLUETOOTH) String getAddress() {
            return this.device.getAddress();
        }
    }

}
