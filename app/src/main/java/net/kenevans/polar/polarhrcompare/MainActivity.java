package net.kenevans.polar.polarhrcompare;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.annotation.NonNull;
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
    private boolean mBleSupported;
    private boolean mAllPermissionsAsked;

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
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
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception :", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });

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

        // Use this check to determine whether BLE is supported on the device.
        // Then you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = getString(R.string.ble_not_supported);
            Utils.warnMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            mBleSupported = false;
            return;
        } else {
            mBleSupported = true;
        }

        // Ask for needed permissions
        requestPermissions();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]
            permissions, @NonNull int[] grantResults) {
        Log.d(TAG, this.getClass().getSimpleName()
                + "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        if (requestCode == REQ_ACCESS_PERMISSIONS) {// All (Handle multiple)
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.
                        permission.ACCESS_COARSE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_SCAN)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_CONNECT)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "denied");
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.getClass().getSimpleName() + ": onBackPressed");
        finish();
        super.onBackPressed();
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
        mDeviceId1 = mSharedPreferences.getString(PREF_DEVICE_ID_1, "");
        mDeviceId2 = mSharedPreferences.getString(PREF_DEVICE_ID_2, "");
        Log.d(TAG,
                "mDeviceId1=" + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        // Do in reverse order as last will be on top
        if (mDeviceId2.equals("")) {
            showDeviceIdDialog(view, 2);
        }
        if (mDeviceId1.equals("")) {
            showDeviceIdDialog(view, 1);
        }

        // Don't use SDK if BT is not enabled or permissions are not granted.
        if (!mBleSupported) return;
        if (!isAllPermissionsGranted(this)) {
            if (!mAllPermissionsAsked) {
                mAllPermissionsAsked = true;
                Utils.warnMsg(this, getString(R.string.permission_not_granted));
            }
            return;
        }

        showToast(getString(R.string.connecting) + " " + mDeviceId1 + "\n"
                + getString(R.string.connecting) + " " + mDeviceId2);
        Intent intent = new Intent(this, HRActivity.class);
        intent.putExtra("id1", mDeviceId1);
        intent.putExtra("id2", mDeviceId2);
        startActivity(intent);
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
                (dialogInterface, which) -> {
                    if (which < mMruDevices.size()) {
                        DeviceInfo deviceInfo1 = mMruDevices.get(which);
                        setDeviceMruPref(deviceInfo1, whichPlot);
                    } else {
                        showDeviceIdDialog(null, whichPlot);
                    }
                    dialogInterface.dismiss();
                });
        dialog[0].setNegativeButton(R.string.cancel,
                (dialogInterface, which) -> dialogInterface.dismiss());
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

        dialog.setPositiveButton("OK", (dialogInterface, which) -> {
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
                (dialogInterface, which) -> dialogInterface.cancel());
        dialog.show();
    }

    public void setDevicesText() {
        Log.d(TAG, "setDevicesText: mDeviceId1=: "
                + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        String name1 = "Unknown";
        if (mDeviceId1 == null) {
            for (DeviceInfo deviceInfo : mMruDevices) {
                if (deviceInfo.id.equals(mDeviceId1)) {
                    name1 = deviceInfo.name;
                    break;
                }
            }
        } else {
            name1 = mDeviceId1;
        }
        String name2 = "Unknown";
        if (mDeviceId2 == null) {
            for (DeviceInfo deviceInfo : mMruDevices) {
                if (deviceInfo.id.equals(mDeviceId2)) {
                    name2 = deviceInfo.name;
                    break;
                }
            }
        } else {
            name2 = mDeviceId2;
        }
        String msg = getString(R.string.devices_string, name1,
                name2);
        Log.d(TAG, "Setting mTextViewDevices to: " + msg);
        mTextViewDevices.setText(msg);
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

    /**
     * Determines if either COARSE or FINE location permission is granted.
     *
     * @return If granted.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAllPermissionsGranted(Context ctx) {
        boolean granted;
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                            PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 6 (M)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED;
        }
        return granted;
    }

    public void requestPermissions() {
        Log.d(TAG, "requestPermissions");
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

        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            this.requestPermissions(new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_ACCESS_PERMISSIONS);
        } else {
            // Android 6 (M)
            this.requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_ACCESS_PERMISSIONS);
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
