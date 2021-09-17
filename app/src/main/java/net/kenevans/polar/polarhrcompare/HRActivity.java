package net.kenevans.polar.polarhrcompare;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarHrData;
import com.polar.sdk.api.model.PolarOhrPPIData;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;

public class HRActivity extends AppCompatActivity implements PlotterListener,
        IConstants {
    private static final int DURATION = 60000;  // In ms
    private XYPlot mPlot;
    private TimePlotter mPlotter1, mPlotter2;

    TextView mTextViewHR1, mTextViewRR1, mTextViewInfo1;
    TextView mTextViewHR2, mTextViewRR2, mTextViewInfo2;
    public PolarBleApi mApi1, mApi2;
    private String mDeviceId1, mDeviceId2;
    private boolean mUsePpg1, mUsePpg2;
    Disposable mPpiDisposable1, mPpiDisposable2;
    private String mName1 = "";
    private String mFw1 = "";
    private String mBattery1 = "";
    private String mName2 = "";
    private String mFw2 = "";
    private String mBattery2 = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);
        mDeviceId1 = getIntent().getStringExtra("id1");
        mDeviceId2 = getIntent().getStringExtra("id2");
        mTextViewHR1 = findViewById(R.id.hrinfo1);
        mTextViewRR1 = findViewById(R.id.rrinfo1);
        mTextViewInfo1 = findViewById(R.id.info1);
        mTextViewHR2 = findViewById(R.id.hrinfo2);
        mTextViewRR2 = findViewById(R.id.rrinfo2);
        mTextViewInfo2 = findViewById(R.id.info2);

        mPlot = findViewById(R.id.plot);

        // Get SDK version
        Log.d(TAG, "SDK Version: " + PolarBleApiDefaultImpl.versionInfo());

        // Device 1
        mApi1 = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR |
                        PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING
        );
        mApi1.setPolarFilter(false);
        mApi1.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged 1 " + b);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device connected 1 " + s.deviceId);
                mName1 = s.name + "\n" + s.deviceId;
                resetInfo1();
                mUsePpg1 = s.name.contains("OH1") || s.name.contains("Sense");
                Log.d(TAG, "  usePpg1=" + mUsePpg1);

                Toast.makeText(HRActivity.this,
                        R.string.connected + " " + s.deviceId,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTING 1: " + polarDeviceInfo.deviceId);
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected 1 " + s);
                mPpiDisposable1 = null;
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
                for (PolarBleApi.DeviceStreamingFeature feature : features) {
                    Log.d(TAG, "Streaming feature is ready for 1: " + feature);
                    switch (feature) {
                        case PPI:
                            if (!mUsePpg1) return;
                            mPpiDisposable1 =
                                    mApi1.startOhrPPIStreaming(mDeviceId1).observeOn(AndroidSchedulers.mainThread()).subscribe(
                                            new Consumer<PolarOhrPPIData>() {
                                                @Override
                                                public void accept(PolarOhrPPIData ppiData) {
                                                    mPlotter1.addValues(mPlot
                                                            , ppiData);
                                                    for (PolarOhrPPIData.PolarOhrPPISample sample : ppiData.samples) {
                                                        Log.d(TAG,
                                                                "1 hr: " + sample.hr +
                                                                        " ppi" +
                                                                        ": " + sample.ppi
                                                                        + " blocker: "
                                                                        + sample.blockerBit
                                                                        + " errorEstimate: "
                                                                        + sample.errorEstimate);
                                                    }
                                                }
                                            },
                                            new Consumer<Throwable>() {
                                                @Override
                                                public void accept(Throwable throwable) {
                                                    Log.e(TAG,
                                                            "PPI failed for " +
                                                                    "device " +
                                                                    "1: " + throwable.getLocalizedMessage());
                                                }
                                            },
                                            new Action() {
                                                @Override
                                                public void run() {
                                                    Log.d(TAG, "PPI complete " +
                                                            "for device 1");
                                                }
                                            }
                                    );
                            break;
                        case ECG:
                        case ACC:
                        case MAGNETOMETER:
                        case GYRO:
                        case PPG:
                        default:
                            break;
                    }
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String s) {
                Log.d(TAG, "HR Feature ready 1 " + s);
            }

            @Override
            public void disInformationReceived(@NonNull String s,
                                               @NonNull UUID u,
                                               @NonNull String s1) {
                if (u.equals(UUID.fromString("00002a28-0000-1000-8000" +
                        "-00805f9b34fb"))) {
                    String msg = "Firmware: " + s1.trim();
                    Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                    mFw1 = msg;
                    resetInfo1();
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                Log.d(TAG, "Battery level 1 " + s + " " + i);
                //                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                mBattery1 = "Battery level: " + i;
                resetInfo1();
            }

            @Override
            public void hrNotificationReceived(@NonNull String s,
                                               @NonNull PolarHrData polarHrData) {
                Log.d(TAG, "HR1 " + polarHrData.hr);
                // TODO
                if (polarHrData.hr == 0) return;
                List<Integer> rrsMs = polarHrData.rrsMs;
                StringBuilder msg = new StringBuilder();
                for (int i : rrsMs) {
                    msg.append(i).append(",");
                }
                if (msg.toString().endsWith(",")) {
                    msg.deleteCharAt(msg.length() - 1);
                }
                mPlotter1.addValues(mPlot, polarHrData);
                msg.append("\n").append(mPlotter1.getRrInfo());
                mTextViewHR1.setText(String.valueOf(polarHrData.hr));
                mTextViewRR1.setText(msg.toString());
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
                Log.d(TAG, "Polar FTP ready 1 " + s);
            }
        });
        if (mDeviceId1 != null && !mDeviceId1.isEmpty()) {
            Log.d(TAG, "onCreate: connectToPolarDevice: DEVICE_ID_1="
                    + mDeviceId1);
            try {
                mApi1.connectToDevice(mDeviceId1);
            } catch (PolarInvalidArgument ex) {
                String msg =
                        "connectToDevice 1: Bad argument: mDeviceId" + mDeviceId1;
                Utils.excMsg(this, msg, ex);
                Log.d(TAG, "    restart: " + msg);
            }
        }

        // Device 2
        mApi2 = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR |
                        PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING
        );
        mApi2.setPolarFilter(false);
        mApi2.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged 2 " + b);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device connected 2 " + s.deviceId);
                mName2 = s.name + "\n" + s.deviceId;
                resetInfo2();
                mUsePpg2 = s.name.contains("OH1") || s.name.contains("Sense");
                Log.d(TAG, "  usePpg2=" + mUsePpg2);

                Toast.makeText(HRActivity.this,
                        R.string.connected + " " + s.deviceId,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTING 2: " + polarDeviceInfo.deviceId);
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected 2 " + s);
                mPpiDisposable2 = null;
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
                for (PolarBleApi.DeviceStreamingFeature feature : features) {
                    Log.d(TAG, "Streaming feature is ready for 2: " + feature);
                    switch (feature) {
                        case PPI:
                            if (!mUsePpg2) return;
                            mPpiDisposable2 =
                                    mApi2.startOhrPPIStreaming(mDeviceId2).observeOn(AndroidSchedulers.mainThread()).subscribe(
                                            new Consumer<PolarOhrPPIData>() {
                                                @Override
                                                public void accept(PolarOhrPPIData ppiData) {
                                                    mPlotter2.addValues(mPlot
                                                            , ppiData);
                                                    for (PolarOhrPPIData.PolarOhrPPISample sample : ppiData.samples) {
                                                        Log.d(TAG,
                                                                "2 hr: " + sample.hr +
                                                                        " ppi" +
                                                                        ": " + sample.ppi
                                                                        + " blocker: "
                                                                        + sample.blockerBit
                                                                        + " errorEstimate: "
                                                                        + sample.errorEstimate);
                                                    }
                                                }
                                            },
                                            new Consumer<Throwable>() {
                                                @Override
                                                public void accept(Throwable throwable) {
                                                    Log.e(TAG,
                                                            "PPI failed for " +
                                                                    "device " +
                                                                    "2: " + throwable.getLocalizedMessage());
                                                }
                                            },
                                            new Action() {
                                                @Override
                                                public void run() {
                                                    Log.d(TAG, "PPI complete " +
                                                            "for device 2");
                                                }
                                            }
                                    );
                            break;
                        case ECG:
                        case ACC:
                        case MAGNETOMETER:
                        case GYRO:
                        case PPG:
                        default:
                            break;
                    }
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String s) {
                Log.d(TAG, "HR Feature ready 2 " + s);
            }

            @Override
            public void disInformationReceived(@NonNull String s,
                                               @NonNull UUID u,
                                               @NonNull String s1) {
                if (u.equals(UUID.fromString("00002a28-0000-1000-8000" +
                        "-00805f9b34fb"))) {
                    String msg = "Firmware: " + s1.trim();
                    Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                    mFw2 = msg;
                    resetInfo2();
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                Log.d(TAG, "Battery level 2 " + s + " " + i);
                //                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                mBattery2 = "Battery level: " + i;
                resetInfo2();
            }

            @Override
            public void hrNotificationReceived(@NonNull String s,
                                               @NonNull PolarHrData polarHrData) {
                Log.d(TAG, "HR2 " + polarHrData.hr);
                List<Integer> rrsMs = polarHrData.rrsMs;
                // TODO
                if (polarHrData.hr == 0) return;
                StringBuilder msg = new StringBuilder();
                for (int i : rrsMs) {
                    msg.append(i).append(",");
                }
                if (msg.toString().endsWith(",")) {
                    msg.deleteCharAt(msg.length() - 1);
                }
                mPlotter2.addValues(mPlot, polarHrData);
                msg.append("\n").append(mPlotter2.getRrInfo());
                mTextViewHR2.setText(String.valueOf(polarHrData.hr));
                mTextViewRR2.setText(msg.toString());
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        if (mDeviceId2 != null && !mDeviceId2.isEmpty()) {
            Log.d(TAG, "onCreate: connectToPolarDevice: DEVICE_ID_2="
                    + mDeviceId2);
            if (mDeviceId2 != null && !mDeviceId2.isEmpty()) {
                Log.d(TAG, "onCreate: connectToPolarDevice: DEVICE_ID_2="
                        + mDeviceId2);
                try {
                    mApi2.connectToDevice(mDeviceId2);
                } catch (PolarInvalidArgument ex) {
                    String msg =
                            "connectToDevice 2: Bad argument: mDeviceId" + mDeviceId2;
                    Utils.excMsg(this, msg, ex);
                    Log.d(TAG, "    restart: " + msg);
                }
            }
        }

        long now = new Date().getTime();

        mPlotter1 = new TimePlotter(this, DURATION, "HR1/RR1",
                Color.RED, Color.BLUE, true);
        mPlotter1.setmListener(this);
        mPlotter2 = new TimePlotter(this, DURATION, "HR2/RR2",
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
        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));
        mPlot.setLinesPerRangeLabel(2);
        mPlot.setTitle(getString(R.string.hr_title, DURATION / 60000));

//        PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom
// .STRETCH_HORIZONTAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mApi1.shutDown();
        mApi2.shutDown();
    }

    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPlot.redraw();
            }
        });
    }

    public void resetInfo1() {
        String msg = mName1 + "\n" + mFw1 + "\n" + mBattery1;
        mTextViewInfo1.setText(msg);
    }

    public void resetInfo2() {
        String msg = mName2 + "\n" + mFw2 + "\n" + mBattery2;
        mTextViewInfo2.setText(msg);
    }
}
