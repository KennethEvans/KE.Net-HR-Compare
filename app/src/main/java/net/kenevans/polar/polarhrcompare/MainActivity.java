package net.kenevans.polar.polarhrcompare;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements IConstants {

    private String mDeviceId1, mDeviceId2;
    SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSharedPreferences = getPreferences(MODE_PRIVATE);
        checkBT();
    }

    public void onClickConnect(View view) {
        checkBT();
        mDeviceId1 = mSharedPreferences.getString(PREF_DEVICE_ID_1, "");
        mDeviceId2 = mSharedPreferences.getString(PREF_DEVICE_ID_2, "");
        Log.d(TAG,
                "mDeviceId1=" + mDeviceId1 + " mDeviceId2=" + mDeviceId2);
        if (mDeviceId1.equals("")) {
            showDialog1(view);
        }
        if (mDeviceId2.equals("")) {
            showDialog2(view);
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
        showDialog1(view);
    }

    public void onClickChangeID2(View view) {
        showDialog2(view);
    }

    public void showDialog1(View view) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this,
                R.style.PolarTheme);
        dialog.setTitle("Enter device 1 ID");

        View viewInflated =
                LayoutInflater.from(getApplicationContext()).
                        inflate(R.layout.device_id_dialog_layout,
                                (ViewGroup) view.getRootView(),
                                false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        mDeviceId1 = mSharedPreferences.getString(PREF_DEVICE_ID_1, "");
        input.setText(mDeviceId1);
        dialog.setView(viewInflated);

        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mDeviceId1 = input.getText().toString();
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putString(PREF_DEVICE_ID_1, mDeviceId1);
                editor.apply();
            }
        });
        dialog.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        dialog.show();
    }

    public void showDialog2(View view) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this,
                R.style.PolarTheme);
        dialog.setTitle("Enter device 2 ID");

        View viewInflated =
                LayoutInflater.from(getApplicationContext()).
                        inflate(R.layout.device_id_dialog_layout,
                                (ViewGroup) view.getRootView(),
                                false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        mDeviceId2 = mSharedPreferences.getString(PREF_DEVICE_ID_2, "");
        input.setText(mDeviceId2);
        dialog.setView(viewInflated);

        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mDeviceId2 = input.getText().toString();
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putString(PREF_DEVICE_ID_2, mDeviceId2);
                editor.apply();
            }
        });
        dialog.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        dialog.show();
    }

    public void checkBT() {
        BluetoothAdapter mBluetoothAdapter =
                BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 2);
        }

        //requestPermissions() method needs to be called when the build SDK
        // version is 23 or above
        if (Build.VERSION.SDK_INT >= 23) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }
}
