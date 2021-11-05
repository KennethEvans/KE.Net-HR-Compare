package net.kenevans.polar.polarhrcompare;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements IConstants {

    private String mDeviceId1, mDeviceId2;
    TextView mTextViewDevices;
    SharedPreferences mSharedPreferences;
    public static final int MAX_DEVICES = 6;
    List<DeviceInfo> mMruDevices;
    Button mButtonId1;
    Button mButtonId2;
    Button mButtonConnect;

    private ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "enableBluetoothLauncher: result" +
                                ".getResultCode()=" + result.getResultCode());
                        if (result.getResultCode() != RESULT_OK) {
                            Utils.warnMsg(this, "This app will not work with " +
                                    "Bluetooth disabled");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextViewDevices = findViewById(R.id.devices);
        mButtonId1 = findViewById(R.id.buttonSetID1);
        mButtonId1.setOnClickListener(v -> selectDeviceId(1));
        mButtonId2 = findViewById(R.id.buttonSetID2);
        mButtonId2.setOnClickListener(v -> selectDeviceId(2));
        mButtonConnect = findViewById(R.id.buttonConnect);
        mButtonConnect.setOnClickListener(this::connect);
        mSharedPreferences = getSharedPreferences("MainActivity",
                MODE_PRIVATE);

//        // Reset mMruDevices in case they get clobbered
//        mMruDevices = new ArrayList<>();
//        gson = new Gson();
//        json = gson.toJson(mMruDevices);
//        SharedPreferences.Editor editor =
//                mSharedPreferences1.edit();
//        editor.putString(PREF_MRU_DEVICE_IDS, json);
//        editor.apply();;

        checkBT();
    }

    @Override
    public void onResume() {
        super.onResume();
        mDeviceId1 = mSharedPreferences.getString(PREF_DEVICE_ID_1, "");
        mDeviceId2 = mSharedPreferences.getString(PREF_DEVICE_ID_2, "");
        Gson gson = new Gson();
        Type type = new TypeToken<LinkedList<DeviceInfo>>() {
        }.getType();
        String json = mSharedPreferences.getString(PREF_MRU_DEVICE_IDS, null);
        mMruDevices = gson.fromJson(json, type);
        if (mMruDevices == null) {
            mMruDevices = new ArrayList<>();
        }
        setDevicesText();
        Log.d(TAG,
                "MainActivity.onResume: mMruDevices.size()=" + mMruDevices.size());
        Log.d(TAG, "mDeviceId1=" + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        for (DeviceInfo deviceInfo : mMruDevices) {
            Log.d(TAG, "    name=" + deviceInfo.name + " id=" + deviceInfo.id);
        }
    }

    /**
     * Utility routine to show Toast. Uses Toast.LENGTH_LONG.
     *
     * @param msg The message.
     */
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "MainActivity: Toast: " + msg);
    }

    public void connect(View view) {
        checkBT();
        mDeviceId1 = mSharedPreferences.getString(PREF_DEVICE_ID_1, "");
        mDeviceId2 = mSharedPreferences.getString(PREF_DEVICE_ID_2, "");
        Log.d(TAG,
                "mDeviceId1=" + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        if (mDeviceId1.equals("")) {
            showDeviceIdDialog(view, 1);
        }
        if (mDeviceId2.equals("")) {
            showDeviceIdDialog(view, 2);
        }
        Toast.makeText(this,
                getString(R.string.connecting) + " " + mDeviceId1 + "\n"
                        + getString(R.string.connecting) + " " + mDeviceId2,
                Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, HRActivity.class);
        intent.putExtra("id1", mDeviceId1);
        intent.putExtra("id2", mDeviceId2);
        startActivity(intent);
    }

    public void onClickChangeID1(View view) {
        selectDeviceId(1);
    }

    public void onClickChangeID2(View view) {
        selectDeviceId(2);
    }

    public void selectDeviceId(int whichPlot) {
        Log.d(TAG, "selectDeviceId: whichPlot=" + whichPlot
                + " mMruDevices.size()=" + mMruDevices.size()
                + " mDeviceId1=: " + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        if (mMruDevices.size() == 0) {
            showDeviceIdDialog(null, whichPlot);
            return;
        }
        final AlertDialog.Builder[] dialog =
                {new AlertDialog.Builder(MainActivity.this,
                        R.style.PolarTheme)};
        if (whichPlot == 1) {
            dialog[0].setTitle(R.string.set_id1_button);
        } else {
            dialog[0].setTitle(R.string.set_id2_button);
        }
        String[] items = new String[mMruDevices.size() + 1];
        DeviceInfo deviceInfo;
        for (int i = 0; i < mMruDevices.size(); i++) {
            deviceInfo = mMruDevices.get(i);
            items[i] = deviceInfo.name;
        }
        items[mMruDevices.size()] = "New";
        int checkedItem = 0;
        for (int i = 0; i < mMruDevices.size(); i++) {
            deviceInfo = mMruDevices.get(i);
            if (whichPlot == 1) {
                if (deviceInfo.id.equals(mDeviceId1)) {
                    checkedItem = i;
                    break;
                }
            } else {
                if (deviceInfo.id.equals(mDeviceId2)) {
                    checkedItem = i;
                    break;
                }
            }
        }
        dialog[0].setSingleChoiceItems(items, checkedItem,
                (dialog12, which) -> {
                    if (which < mMruDevices.size()) {
                        DeviceInfo deviceInfo1 = mMruDevices.get(which);
                        setDeviceMruPref(deviceInfo1, whichPlot);
                    } else {
                        showDeviceIdDialog(null, whichPlot);
                    }
                    dialog12.dismiss();
                });
        dialog[0].setNegativeButton(R.string.cancel,
                (dialog1, which) -> dialog1.dismiss());
        AlertDialog alert = dialog[0].create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
    }

    public void showDeviceIdDialog(View view, int whichPlot) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this,
                R.style.PolarTheme);
        dialog.setTitle("Enter device " + whichPlot + " ID");

        View viewInflated = LayoutInflater.from(getApplicationContext()).
                inflate(R.layout.device_id_dialog_layout,
                        view == null ? null : (ViewGroup) view.getRootView(),
                        false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        Log.d(TAG, "Before: mDeviceId1=" + mDeviceId1
                + " mDeviceId2=" + mDeviceId2);
        if (whichPlot == 1) {
            input.setText(mDeviceId1);
        } else {
            input.setText(mDeviceId2);
        }
        dialog.setView(viewInflated);

        dialog.setPositiveButton("OK", (dialog1, which) -> {
            if (whichPlot == 1) {
                mDeviceId1 = input.getText().toString();
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putString(PREF_DEVICE_ID_1, mDeviceId1);
                editor.apply();
            } else {
                mDeviceId2 = input.getText().toString();
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putString(PREF_DEVICE_ID_2, mDeviceId2);
                editor.apply();
            }
            setDevicesText();
            Log.d(TAG, "After: mDeviceId1=" + mDeviceId1
                    + " mDeviceId2=" + mDeviceId2);
        });
        dialog.setNegativeButton("Cancel",
                (dialog12, which) -> dialog12.cancel());
        dialog.show();
    }

    public void setDevicesText() {
        Log.d(TAG, "setDevicesText: mDeviceId1=: "
                + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        String name1 = "Unknown";
        if (mDeviceId1 != null) {
            for (DeviceInfo deviceInfo : mMruDevices) {
                if (deviceInfo.id.equals(mDeviceId1)) {
                    name1 = deviceInfo.name;
                    break;
                }
            }
        }
        String name2 = "Unknown";
        if (mDeviceId2 != null) {
            for (DeviceInfo deviceInfo : mMruDevices) {
                if (deviceInfo.id.equals(mDeviceId2)) {
                    name2 = deviceInfo.name;
                    break;
                }
            }
        }
        mTextViewDevices.setText(getString(R.string.devices_string, name1,
                name2));
    }

    public void setDeviceMruPref(DeviceInfo deviceInfo, int which) {
        Log.d(TAG, "MainActivity: setDeviceMruPref: which=" + which
                + " name=" + deviceInfo.name + " id=" + deviceInfo.id);
        SharedPreferences.Editor editor =
                mSharedPreferences.edit();
        // Remove any found so the new one will be added at the beginning
        List<DeviceInfo> removeList = new ArrayList<>();
        for (DeviceInfo deviceInfo1 : mMruDevices) {
            if (deviceInfo.name.equals(deviceInfo1.name) &&
                    deviceInfo.id.equals(deviceInfo1.id)) {
                removeList.add(deviceInfo1);
            }
        }
        for (DeviceInfo deviceInfo1 : removeList) {
            mMruDevices.remove(deviceInfo1);
        }
        // Remove at end if size exceed max
        if (mMruDevices.size() != 0 && mMruDevices.size() == MAX_DEVICES) {
            mMruDevices.remove(mMruDevices.size() - 1);
        }
        // Add at the beginning
        mMruDevices.add(0, deviceInfo);
        Gson gson = new Gson();
        String json = gson.toJson(mMruDevices);
        editor.putString(PREF_MRU_DEVICE_IDS, json);
        if (which == 1) {
            mDeviceId1 = deviceInfo.id;
            editor.putString(PREF_DEVICE_ID_1, deviceInfo.id);
        } else {
            mDeviceId2 = deviceInfo.id;
            editor.putString(PREF_DEVICE_ID_2, deviceInfo.id);
        }
        editor.apply();
        setDevicesText();
        Log.d(TAG, "MainActivity: setDeviceMruPref done: mDeviceId1=: "
                + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
    }

    public void checkBT() {
        Log.d(TAG, "checkBT");
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
            if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent =
                        new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBluetoothLauncher.launch(enableBtIntent);
            }
        }

        //requestPermissions() method needs to be called when the build SDK
        // version is 23 or above
        if (Build.VERSION.SDK_INT >= 23) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_ACCESS_LOCATION);
        }
    }

    public static class DeviceInfo {
        public String name;
        public String id;

        public DeviceInfo(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }
}
