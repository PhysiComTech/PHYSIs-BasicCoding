package com.physicomtech.kit.physislibrary;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.physicomtech.kit.physislibrary.ble.BluetoothLEManager;
import com.physicomtech.kit.physislibrary.ble.data.Beacon;

import java.lang.ref.WeakReference;
import java.util.List;

public class PHYSIsBLEActivity extends AppCompatActivity {

    private static final String ADDRESS_REGEX = ":";
    private static final String ADDRESS_REPLACE = "";
    public static final String HM_10_CONF = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String HM_RX_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";

    public static final int CONNECTED = 200;
    public static final int DISCONNECTED = 201;
    public static final int NO_DISCOVERY = 202;

    private BluetoothLEManager bleManager = null;
    private List<Beacon> beacons = null;
    private String serialNumber = null;

    private BluetoothDevice connectDevice = null;
    private boolean isDiscovery = false;
    private boolean isConnecting = false;

    private static class BluetoothLHandle extends Handler {
        private final WeakReference<PHYSIsBLEActivity> mActivity;
        BluetoothLHandle(PHYSIsBLEActivity activity) {
            mActivity = new WeakReference<PHYSIsBLEActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            PHYSIsBLEActivity activity = mActivity.get();
            if (activity != null) {
                activity.handleMessage(msg);
            }
        }
    }
    protected final BluetoothLHandle physisHandle = new BluetoothLHandle(this);

    protected void handleMessage(Message msg){
        switch (msg.what) {
            case BluetoothLEManager.BLE_SCAN_START:
                break;
            case BluetoothLEManager.BLE_SCAN_STOP:
                if(!isDiscovery)
                    onBLEConnectedStatus(NO_DISCOVERY);
                else
                    connectBLE();
                break;
            case BluetoothLEManager.BLE_SCAN_DEVICE:
//                BluetoothDevice device = (BluetoothDevice) msg.obj;
                targetDiscovery((BluetoothDevice) msg.obj);
                break;
            case BluetoothLEManager.BLE_SCAN_BEACON:
                Beacon beacon = (Beacon) msg.obj;
                break;
            case BluetoothLEManager.BLE_CONNECT_DEVICE:
                break;
            case BluetoothLEManager.BLE_SERVICES_DISCOVERED:
                if (!bleManager.notifyCharacteristic(HM_10_CONF, HM_RX_TX)) {
                    bleManager.disconnectDevice();
                }
                break;
            case BluetoothLEManager.BLE_DATA_AVAILABLE:
                onBLEReceiveMsg((String) msg.obj);
                break;
            case BluetoothLEManager.BLE_READ_CHARACTERISTIC:
                onBLEConnectedStatus(CONNECTED);
                break;
            case BluetoothLEManager.BLE_DISCONNECT_DEVICE:
                onBLEConnectedStatus(DISCONNECTED);
                break;
        }
    }

    private void targetDiscovery(BluetoothDevice device){
        if(device.getAddress()
                .replaceAll(ADDRESS_REGEX, ADDRESS_REPLACE).equals(serialNumber)){
            bleManager.scan(false, false);
            isDiscovery = true;
            connectDevice = device;
        }
    }

    private void connectBLE(){
        if(connectDevice == null)
            return;
        bleManager.connectDevice(connectDevice);
    }

    // region Bluetooth LE Setting - Service/Broadcast
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bleManager = BluetoothLEManager.getInstance(getApplicationContext());

        if(!bleManager.checkBleStatus()){
            Toast.makeText(getApplicationContext(), R.string.err_support_ble, Toast.LENGTH_SHORT).show();
            finish();
        }

        if(!bleManager.getEnable()){
            Toast.makeText(getApplicationContext(), R.string.err_disable_ble, Toast.LENGTH_SHORT).show();
            finish();
        }

        bleManager.bindService();
        bleManager.setHandler(physisHandle);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bleManager.registerReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        bleManager.unregisterReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.disconnectDevice();
        bleManager.unBindService();
    }
    // endregion

    protected void connectDevice(String serialNumber){
        if(isConnecting)
            return;
        this.serialNumber = serialNumber;
        this.isDiscovery = false;
        isConnecting = true;
        bleManager.scan(true, true);
    }

    protected void disconnectDevice(){
        bleManager.disconnectDevice();
    }

    protected void onBLEConnectedStatus(int result){
        isConnecting = false;
    }

    protected void onBLEReceiveMsg(String msg){

    }

    protected void sendMessage(String msg){
        bleManager.writeCharacteristic(msg);
    }

}
