package net.kenevans.polar.polarhrcompare;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarHrData;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class HRActivity extends AppCompatActivity implements PlotterListener,
        IConstants {
    private static final int DURATION = 60000;  // In ms
    private XYPlot mPlot;
    private TimePlotter mPlotter1, mPlotter2;

    TextView mTextViewHR1, mTextViewRR1, mTextViewInfo1;
    TextView mTextViewHR2, mTextViewRR2, mTextViewInfo2;
    public PolarBleApi mApi;
    private String mDeviceId1, mDeviceId2;
    private boolean mUsePpg1, mUsePpg2;
    Disposable mPpiDisposable1, mPpiDisposable2;
    private String mName1 = "";
    private String mFw1 = "";
    private String mBattery1 = "";
    private String mName2 = "";
    private String mFw2 = "";
    private String mBattery2 = "";
    SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);
        mSharedPreferences = getSharedPreferences("MainActivity", MODE_PRIVATE);
        mDeviceId1 = getIntent().getStringExtra("id1");
        mDeviceId2 = getIntent().getStringExtra("id2");
        Log.d(TAG, "HRActivity.onCreate(): "
                + "mDeviceId1=" + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        mTextViewHR1 = findViewById(R.id.hrinfo1);
        mTextViewRR1 = findViewById(R.id.rrinfo1);
        mTextViewInfo1 = findViewById(R.id.info1);
        mTextViewHR2 = findViewById(R.id.hrinfo2);
        mTextViewRR2 = findViewById(R.id.rrinfo2);
        mTextViewInfo2 = findViewById(R.id.info2);

        mPlot = findViewById(R.id.plot);

        // Get SDK version
        Log.d(TAG, "SDK Version: " + PolarBleApiDefaultImpl.versionInfo());

        mApi = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR |
                        PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING
        );
        mApi.setPolarFilter(false); // Allow BT addresses
        mApi.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                // true is powered
                Log.d(TAG, "BluetoothStateChanged 1 " + b);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                if (polarDeviceInfo.deviceId.equals((mDeviceId1))) {
                    Log.d(TAG,
                            "Device connected 1 " + polarDeviceInfo.deviceId);
                    mName1 =
                            polarDeviceInfo.name + "\n" + polarDeviceInfo.deviceId;
                    // Set the MRU preference here after we know the name
                    setDeviceMruPref(new MainActivity.DeviceInfo(polarDeviceInfo.name,
                            polarDeviceInfo.deviceId), 2);
                    // Reset the plot
                    resetInfo1();
                    mUsePpg1 =
                            polarDeviceInfo.name.contains("OH1") || polarDeviceInfo.name.contains("Sense");
                    Log.d(TAG, "  usePpg1=" + mUsePpg1);
                } else if (polarDeviceInfo.deviceId.equals(mDeviceId2)) {
                    Log.d(TAG,
                            "Device connected 2 " + polarDeviceInfo.deviceId);
                    mName2 =
                            polarDeviceInfo.name + "\n" + polarDeviceInfo.deviceId;
                    // Set the MRU preference here after we know the name
                    setDeviceMruPref(new MainActivity.DeviceInfo(polarDeviceInfo.name,
                            polarDeviceInfo.deviceId), 2);
                    // Reset the plot
                    resetInfo2();
                    mUsePpg2 =
                            polarDeviceInfo.name.contains("OH1") || polarDeviceInfo.name.contains("Sense");
                    Log.d(TAG, "  usePpg2=" + mUsePpg2);

                }
                showToast(getString(R.string.connected_string,
                        polarDeviceInfo.name));
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                if (polarDeviceInfo.deviceId.equals((mDeviceId1))) {
                    Log.d(TAG, "CONNECTING 1: " + polarDeviceInfo.deviceId);
                } else if (polarDeviceInfo.deviceId.equals(mDeviceId2)) {
                    Log.d(TAG, "CONNECTING 2: " + polarDeviceInfo.deviceId);
                }
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                if (polarDeviceInfo.deviceId.equals((mDeviceId1))) {
                    Log.d(TAG, "Device disconnected 1 " + polarDeviceInfo);
                    mPpiDisposable1 = null;
                } else if (polarDeviceInfo.deviceId.equals(mDeviceId2)) {
                    Log.d(TAG, "Device disconnected 2 " + polarDeviceInfo);
                    mPpiDisposable2 = null;
                }
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
                for (PolarBleApi.DeviceStreamingFeature feature : features) {
                    switch (feature) {
                        case PPI:
                            if (identifier.equals((mDeviceId1))) {
                                Log.d(TAG,
                                        "Streaming feature is ready for 1: " + feature);
                            } else if (identifier.equals(mDeviceId2)) {
                                Log.d(TAG,
                                        "Streaming feature is ready for 2: " + feature);
                            }
                            if (identifier.equals((mDeviceId1))) {
                                if (!mUsePpg1) return;
                                mPpiDisposable1 =
                                        mApi.startOhrPPIStreaming(mDeviceId1).observeOn(AndroidSchedulers.mainThread()).subscribe(
                                                ppiData -> {
                                                    mPlotter1.addValues(mPlot
                                                            , ppiData);
//                                                    for (PolarOhrPPIData
//                                                    .PolarOhrPPISample
//                                                    sample : ppiData
//                                                    .samples) {
//                                                        Log.d(TAG,
//                                                                "Device 1
//                                                                hr:" +
//                                                                        " "
//                                                                        +
//                                                                        sample.hr +
//                                                                        "
//                                                                        ppi" +
//                                                                        ":
//                                                                        " +
//                                                                        sample.ppi
//                                                                        + "
//                                                                        blocker: "
//                                                                        +
//                                                                        sample.blockerBit
//                                                                        + "
//                                                                        errorEstimate: "
//                                                                        +
//                                                                        sample.errorEstimate);
//                                                    }
                                                },
                                                throwable -> {
                                                    String msg = "PPI failed " +
                                                            "for device 1: " +
                                                            throwable.getLocalizedMessage();
                                                    Log.e(TAG, msg);
                                                    showToast(msg);
                                                    Utils.excMsg(HRActivity.this,
                                                            "PPI failed for " +
                                                                    "device " +
                                                                    "1",
                                                            throwable);
                                                },
                                                () -> Log.d(TAG, "PPI " +
                                                        "complete " +
                                                        "for device 1")
                                        );
                            } else if (identifier.equals(mDeviceId2)) {
                                if (!mUsePpg2) return;
                                mPpiDisposable2 =
                                        mApi.startOhrPPIStreaming(mDeviceId2).observeOn(AndroidSchedulers.mainThread()).subscribe(
                                                ppiData -> {
                                                    mPlotter2.addValues(mPlot
                                                            , ppiData);
//                                                    for (PolarOhrPPIData
//                                                    .PolarOhrPPISample
//                                                    sample : ppiData
//                                                    .samples) {
//                                                        Log.d(TAG,
//                                                                "Device 2
//                                                                hr:" +
//                                                                        " "
//                                                                        +
//                                                                        sample.hr +
//                                                                        "
//                                                                        ppi" +
//                                                                        ":
//                                                                        " +
//                                                                        sample.ppi
//                                                                        + "
//                                                                        blocker: "
//                                                                        +
//                                                                        sample.blockerBit
//                                                                        + "
//                                                                        errorEstimate: "
//                                                                        +
//                                                                        sample.errorEstimate);
//                                                    }
                                                },
                                                throwable -> {
                                                    String msg = "PPI failed " +
                                                            "for device 2: " +
                                                            throwable.getLocalizedMessage();
                                                    Log.e(TAG, msg);
                                                    showToast(msg);
                                                    Utils.excMsg(HRActivity.this,
                                                            "PPI failed for " +
                                                                    "device " +
                                                                    "2",
                                                            throwable);
                                                },
                                                () -> Log.d(TAG, "PPI " +
                                                        "complete " +
                                                        "for device 2")
                                        );
                            }
                            break;
                        case ECG:
                        case ACC:
                        case MAGNETOMETER:
                        case GYRO:
                        case PPG:
                        default:
//                            if (identifier.equals((mDeviceId1))) {
//                                Log.d(TAG,
//                                        "Streaming feature is ready for 1:
//                                        " + feature);
//                            } else if (identifier.equals(mDeviceId2)) {
//                                Log.d(TAG,
//                                        "Streaming feature is ready for 2:
//                                        " + feature);
//                            }
                            break;
                    }
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String identifier) {
                if (identifier.equals((mDeviceId1))) {
                    Log.d(TAG, "HR Feature ready 1 " + identifier);
                } else if (identifier.equals(mDeviceId2)) {
                    Log.d(TAG, "HR Feature ready 2 " + identifier);
                }
            }

            @Override
            public void disInformationReceived(@NonNull String identifier,
                                               @NonNull UUID u,
                                               @NonNull String s1) {
                if (u.equals(UUID.fromString("00002a28-0000-1000-8000" +
                        "-00805f9b34fb"))) {
                    String msg = "Firmware: " + s1.trim();
                    Log.d(TAG, "Firmware: " + identifier + " " + s1.trim());
                    if (identifier.equals((mDeviceId1))) {
                        mFw1 = msg;
                        resetInfo1();
                    } else if (identifier.equals(mDeviceId2)) {
                        mFw2 = msg;
                        resetInfo2();
                    }
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String identifier,
                                             int i) {
                if (identifier.equals((mDeviceId1))) {
                    Log.d(TAG, "Battery level 1 " + identifier + " " + i);
                    mBattery1 = "Battery level: " + i;
                    resetInfo1();
                } else if (identifier.equals(mDeviceId2)) {
                    Log.d(TAG, "Battery level 2 " + identifier + " " + i);
                    mBattery2 = "Battery level: " + i;
                    resetInfo2();
                }
            }

            @Override
            public void hrNotificationReceived(@NonNull String identifier,
                                               @NonNull PolarHrData polarHrData) {
//                Log.d(TAG, "HR1 " + polarHrData.hr);
//                // TODO
//                if (polarHrData.hr == 0) return;
                List<Integer> rrsMs = polarHrData.rrsMs;
                StringBuilder msg = new StringBuilder();
                for (int i : rrsMs) {
                    msg.append(i).append(",");
                }
                if (msg.toString().endsWith(",")) {
                    msg.deleteCharAt(msg.length() - 1);
                }
                if (identifier.equals((mDeviceId1))) {
                    mPlotter1.addValues(mPlot, polarHrData);
                    msg.append("\n").append(mPlotter1.getRrInfo());
                    mTextViewHR1.setText(String.valueOf(polarHrData.hr));
                    mTextViewRR1.setText(msg.toString());
                } else if (identifier.equals(mDeviceId2)) {
                    mPlotter2.addValues(mPlot, polarHrData);
                    msg.append("\n").append(mPlotter2.getRrInfo());
                    mTextViewHR2.setText(String.valueOf(polarHrData.hr));
                    mTextViewRR2.setText(msg.toString());
                }
            }
        });

        if (mDeviceId1 != null && !mDeviceId1.isEmpty()) {
            Log.d(TAG, "HRActivity.onCreate: connectToPolarDevice: DEVICE_ID_1="
                    + mDeviceId1);
            try {
                mApi.connectToDevice(mDeviceId1);
            } catch (PolarInvalidArgument ex) {
                String msg =
                        "connectToDevice 1: Bad argument: mDeviceId" + mDeviceId1;
                Utils.excMsg(this, msg, ex);
            }
        }
        if (mDeviceId2 != null && !mDeviceId2.isEmpty()) {
            Log.d(TAG, "HRActivity.onCreate: connectToPolarDevice: DEVICE_ID_2="
                    + mDeviceId2);
            try {
                mApi.connectToDevice(mDeviceId2);
            } catch (PolarInvalidArgument ex) {
                String msg =
                        "connectToDevice 2: Bad argument: mDeviceId" + mDeviceId2;
                Utils.excMsg(this, msg, ex);
            }
        }

        long now = new Date().getTime();

        mPlotter1 = new

                TimePlotter(this, DURATION, "HR1/RR1",
                Color.RED, Color.BLUE, true);
        mPlotter1.setmListener(this);
        mPlotter2 = new

                TimePlotter(this, DURATION, "HR2/RR2",
                Color.rgb(0xFF, 0x88, 0xAA),
                Color.rgb(0x88, 0, 0x88), true);
        mPlotter2.setmListener(this);

        mPlot.addSeries(mPlotter1.getmHrSeries(), mPlotter1.getmHrFormatter());
        mPlot.addSeries(mPlotter2.getmHrSeries(), mPlotter2.getmHrFormatter());
        mPlot.addSeries(mPlotter1.getmRrSeries(), mPlotter1.getmRrFormatter());
        mPlot.addSeries(mPlotter2.getmRrSeries(), mPlotter2.getmRrFormatter());
        mPlot.setRangeBoundaries(50, 100,
                BoundaryMode.AUTO);
        mPlot.setDomainBoundaries(now - DURATION, now, BoundaryMode.FIXED);
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10);
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, DURATION / 6.);
        // Make left labels be an integer (no decimal places)
        mPlot.getGraph().

                getLineLabelStyle(XYGraphWidget.Edge.LEFT).

                setFormat(new DecimalFormat("#"));
        mPlot.setLinesPerRangeLabel(2);
        mPlot.setTitle(

                getString(R.string.hr_title, DURATION / 60000));

//        PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom
// .STRETCH_HORIZONTAL);
    }

    @Override
    protected void onPause() {
        Log.v(TAG, this.getClass().getSimpleName() + " onPause: mAPi=" + mApi);
        super.onPause();
        if (mApi != null) mApi.backgroundEntered();
    }

    @Override
    public void onResume() {
        Log.v(TAG, this.getClass().getSimpleName() + " onResume: mAPi=" + mApi);
        super.onResume();
        if (mApi != null) mApi.foregroundEntered();
    }

    @Override
    public void onDestroy() {
        Log.v(TAG,
                this.getClass().getSimpleName() + " onDestroy: mAPi=" + mApi);
        super.onDestroy();
        if (mApi != null) {
            try {
                if (mDeviceId1 != null) mApi.disconnectFromDevice(mDeviceId1);
            } catch (Exception ex) {
                Log.e(TAG, "Error disconnecting from " + mDeviceId1);
            }
            try {
                if (mDeviceId2 != null) mApi.disconnectFromDevice(mDeviceId2);
            } catch (Exception ex) {
                Log.e(TAG, "Error disconnecting from " + mDeviceId2);
            }
            mApi.shutDown();
        }
    }

    /**
     * Redraws the plot.
     */
    public void update() {
        runOnUiThread(() -> mPlot.redraw());
    }

    /**
     * Utility routine to show Toast. Uses Toast.LENGTH_LONG.
     *
     * @param msg The message.
     */
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        Log.d(TAG, "HRActivity: Toast: " + msg);
    }

    public void resetInfo1() {
        String msg = mName1 + "\n" + mFw1 + "\n" + mBattery1;
        mTextViewInfo1.setText(msg);
    }

    public void resetInfo2() {
        String msg = mName2 + "\n" + mFw2 + "\n" + mBattery2;
        mTextViewInfo2.setText(msg);
    }

    public void setDeviceMruPref(MainActivity.DeviceInfo deviceInfo,
                                 int which) {
        Gson gson = new Gson();
        Type type = new TypeToken<LinkedList<MainActivity.DeviceInfo>>() {
        }.getType();
        String json = mSharedPreferences.getString(PREF_MRU_DEVICE_IDS, null);
        List<MainActivity.DeviceInfo> mruDevices = gson.fromJson(json, type);
        if (mruDevices == null) {
            mruDevices = new ArrayList<>();
        }

        Log.d(TAG, "HeartActivity: setDeviceMruPref: which=" + which
                + " name=" + deviceInfo.name + " id=" + deviceInfo.id);
        SharedPreferences.Editor editor =
                mSharedPreferences.edit();
        // Remove any found so the new one will be added at the beginning
        List<MainActivity.DeviceInfo> removeList = new ArrayList<>();
        for (MainActivity.DeviceInfo deviceInfo1 : mruDevices) {
            if (deviceInfo.name.equals(deviceInfo1.name) &&
                    deviceInfo.id.equals(deviceInfo1.id)) {
                removeList.add(deviceInfo1);
            }
        }
        for (MainActivity.DeviceInfo deviceInfo1 : removeList) {
            mruDevices.remove(deviceInfo1);
        }
        // Remove at end if size exceed max
        if (mruDevices.size() != 0 && mruDevices.size() == MainActivity.MAX_DEVICES) {
            mruDevices.remove(mruDevices.size() - 1);
        }
        // Add at the beginning
        mruDevices.add(0, deviceInfo);
        gson = new Gson();
        json = gson.toJson(mruDevices);
        editor.putString(PREF_MRU_DEVICE_IDS, json);
        editor.apply();
        Log.d(TAG, "HeartActivitysetDeviceMruPref done: mDeviceId1=: "
                + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
    }
}
