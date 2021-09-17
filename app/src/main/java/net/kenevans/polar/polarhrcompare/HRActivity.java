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

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarOhrPPIData;

public class HRActivity extends AppCompatActivity implements PlotterListener,
        IConstants {
    private static final int DURATION = 60000;  // In ms
    private XYPlot mPlot;
    private TimePlotter mPlotter1, mPlotter2;

    TextView mTextViewHR1, mTextViewRR1, mTextViewFW1;
    TextView mTextViewHR2, mTextViewRR2, mTextViewFW2;
    public PolarBleApi mApi1, MApi2;
    private String mDeviceId1, mDeviceId2;
    private boolean mUsePpg1, mUsePpg2;
    Disposable mPpiDisposable1, mPpiDisposable2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);
        mDeviceId1 = getIntent().getStringExtra("id1");
        mDeviceId2 = getIntent().getStringExtra("id2");
        mTextViewHR1 = findViewById(R.id.hrinfo1);
        mTextViewRR1 = findViewById(R.id.rrinfo1);
        mTextViewFW1 = findViewById(R.id.fw1);
        mTextViewHR2 = findViewById(R.id.hrinfo2);
        mTextViewRR2 = findViewById(R.id.rrinfo2);
        mTextViewFW2 = findViewById(R.id.fw2);

        mPlot = findViewById(R.id.plot);

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
            public void polarDeviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device connected 1 " + s.deviceId);
                String msg = s.name + "\n" + s.deviceId;
                mTextViewFW1.append("\n" + msg);
                mUsePpg1 = s.name.contains("OH1") || s.name.contains("Sense");
                Log.d(TAG, "  usePpg1=" + mUsePpg1);

                Toast.makeText(HRActivity.this,
                        R.string.connected + " " + s.deviceId,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void polarDeviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTING 1: " + polarDeviceInfo.deviceId);
            }

            @Override
            public void polarDeviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected 1 " + s);
                mPpiDisposable1 = null;
            }

            @Override
            public void ecgFeatureReady(@NonNull String s) {
                Log.d(TAG, "ECG Feature ready 1 " + s);
            }

            @Override
            public void accelerometerFeatureReady(@NonNull String s) {
                Log.d(TAG, "ACC Feature ready 1" + s);
            }

            @Override
            public void ppgFeatureReady(@NonNull String s) {
                Log.d(TAG, "PPG Feature ready 1 " + s);
            }

            @Override
            public void ppiFeatureReady(@NonNull String s) {
                Log.d(TAG, "PPI Feature ready 1 " + s);
                if (!mUsePpg1) return;
                mPpiDisposable1 =
                        mApi1.startOhrPPIStreaming(mDeviceId1).observeOn(AndroidSchedulers.mainThread()).subscribe(
                                new Consumer<PolarOhrPPIData>() {
                                    @Override
                                    public void accept(PolarOhrPPIData ppiData) {
                                        mPlotter1.addValues(mPlot, ppiData);
                                        for (PolarOhrPPIData.PolarOhrPPISample sample : ppiData.samples) {
                                            Log.d(TAG,
                                                    "1 hr: " + sample.hr +
                                                            " ppi: " + sample.ppi
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
                                                "PPI failed for device 1: " + throwable.getLocalizedMessage());
                                    }
                                },
                                new Action() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "PPI complete for device 1");
                                    }
                                }
                        );
            }

            @Override
            public void biozFeatureReady(@NonNull String s) {

            }

            @Override
            public void hrFeatureReady(@NonNull String s) {
                Log.d(TAG, "HR Feature ready 1 " + s);
            }

            @Override
            public void fwInformationReceived(@NonNull String s,
                                              @NonNull String fw) {
                Log.d(TAG, "Firmware 1: " + s + " " + fw.trim());
                // Don't write if the information is empty
                if (!fw.isEmpty()) {
                    String msg = "Firmware: " + fw.trim();
                    mTextViewFW1.append("\n" + msg);
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                Log.d(TAG, "Battery level 1 " + s + " " + i);
                String msg = "Battery level: " + i;
//                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                mTextViewFW1.append("\n" + msg);
            }

            @Override
            public void hrNotificationReceived(@NonNull String s,
                                               @NonNull PolarHrData polarHrData) {
                Log.d(TAG, "HR1 " + polarHrData.hr);
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
            mApi1.connectToPolarDevice(mDeviceId1);
        }

        // Device 2
        MApi2 = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR |
                        PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING
        );
        MApi2.setPolarFilter(false);
        MApi2.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged 2 " + b);
            }

            @Override
            public void polarDeviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device connected 2 " + s.deviceId);
                String msg = s.name + "\n" + s.deviceId;
                mTextViewFW2.append("\n" + msg);
                mUsePpg2 = s.name.contains("OH1") || s.name.contains("Sense");
                Log.d(TAG, "  usePpg2=" + mUsePpg2);

                Toast.makeText(HRActivity.this,
                        R.string.connected + " " + s.deviceId,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void polarDeviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTING 2: " + polarDeviceInfo.deviceId);
            }

            @Override
            public void polarDeviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected 2 " + s);
                mPpiDisposable2 = null;
            }

            @Override
            public void ecgFeatureReady(@NonNull String s) {
                Log.d(TAG, "ECG Feature ready 2 " + s);
            }

            @Override
            public void accelerometerFeatureReady(@NonNull String s) {
                Log.d(TAG, "ACC Feature ready 2 " + s);
            }

            @Override
            public void ppgFeatureReady(@NonNull String s) {
                Log.d(TAG, "PPG Feature ready 2 " + s);
            }

            @Override
            public void ppiFeatureReady(@NonNull String s) {
                Log.d(TAG, "PPI Feature ready 2 " + s);
                if (!mUsePpg2) return;
                mPpiDisposable2 =
                        MApi2.startOhrPPIStreaming(mDeviceId2).observeOn(AndroidSchedulers.mainThread()).subscribe(
                                new Consumer<PolarOhrPPIData>() {
                                    @Override
                                    public void accept(PolarOhrPPIData ppiData) {
                                        mPlotter2.addValues(mPlot, ppiData);
                                        for (PolarOhrPPIData.PolarOhrPPISample sample : ppiData.samples) {
                                            Log.d(TAG,
                                                    "2 hr: " + sample.hr +
                                                            " ppi: " + sample.ppi
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
                                                "PPI failed for device 2: " + throwable.getLocalizedMessage());
                                    }
                                },
                                new Action() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "PPI complete for device 2");
                                    }
                                }
                        );
            }

            @Override
            public void biozFeatureReady(@NonNull String s) {

            }

            @Override
            public void hrFeatureReady(@NonNull String s) {
                Log.d(TAG, "HR Feature ready 2 " + s);
            }

            @Override
            public void fwInformationReceived(@NonNull String s,
                                              @NonNull String fw) {
                Log.d(TAG, "Firmware 2: " + s + " " + fw.trim());
                // Don't write if the information is empty
                if (!fw.isEmpty()) {
                    String msg = "Firmware: " + fw.trim();
                    mTextViewFW2.append("\n" + msg);
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                Log.d(TAG, "Battery level 2 " + s + " " + i);
                String msg = "Battery level: " + i;
//                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                mTextViewFW2.append("\n" + msg);
            }

            @Override
            public void hrNotificationReceived(@NonNull String s,
                                               @NonNull PolarHrData polarHrData) {
                Log.d(TAG, "HR2 " + polarHrData.hr);
                List<Integer> rrsMs = polarHrData.rrsMs;
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
            MApi2.connectToPolarDevice(mDeviceId2);
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
        MApi2.shutDown();
    }

    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPlot.redraw();
            }
        });
    }
}
