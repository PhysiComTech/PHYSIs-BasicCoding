package com.physicomtech.kit.physislibrary.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.physicomtech.kit.physislibrary.ble.data.Beacon;

import java.util.List;

public class BluetoothLEManager {
    private static final String TAG = "BluetoothLEManager";

    public static final int BLE_SCAN_START = 101;
    public static final int BLE_SCAN_STOP = 102;
    public static final int BLE_SCAN_DEVICE = 103;
    public static final int BLE_SCAN_BEACON = 104;
    public static final int BLE_CONNECT_DEVICE = 105;
    public static final int BLE_DISCONNECT_DEVICE = 106;
    public static final int BLE_SERVICES_DISCOVERED = 107;
    public static final int BLE_DATA_AVAILABLE = 108;
    public static final int BLE_READ_CHARACTERISTIC = 109;

    private static BluetoothLEManager bluetoothLEManager = null;
    private Context context;
    private Handler bleHandler = null;

    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothLeScanner bluetoothLeScanner = null;
    private BluetoothLEService bluetoothLEService = null;

    private long scanTime = 5000;
    private boolean isScanning = false;
    private boolean isBind = false;
    private boolean isRegister = false;

    private BluetoothLEManager(Context context){
        this.context = context;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public synchronized static BluetoothLEManager getInstance(Context context){
        if(bluetoothLEManager == null)
            bluetoothLEManager = new BluetoothLEManager(context);
        return bluetoothLEManager;
    }

    //  Check Bluetooth LE Support
    public boolean checkBleStatus() {
        return bluetoothAdapter != null && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public boolean getEnable(){
        return bluetoothAdapter.isEnabled();
    }

    //  Setup Scan Time
    public void setScanTime(long time){
        scanTime = time;
    }

    //  Setup Bluetooth LE Handler
    public void setHandler(Handler handler){
        bleHandler = handler;
    }


    //region    Bluetooth LE Scanner
    private void startBLEScan(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner.startScan(scanCallback);
        }else{
            bluetoothAdapter.startLeScan(bleScanCallback);
        }
        isScanning = true;
        bleHandler.obtainMessage(BLE_SCAN_START).sendToTarget();
        Log.e(TAG, "# Start Bluetooth LE Scan..");
    }

    private void stopBLEScan(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner.stopScan(scanCallback);
        }else{
            bluetoothAdapter.stopLeScan(bleScanCallback);
        }
        isScanning = false;
        bleHandler.removeCallbacks(scanStopRunnable);
        bleHandler.obtainMessage(BLE_SCAN_STOP).sendToTarget();
        Log.e(TAG, "# Stop Bluetooth LE Scan..");
    }

    private BluetoothAdapter.LeScanCallback bleScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
            bleScanResult(bluetoothDevice, rssi, scanRecord);
        }
    };

    @SuppressLint("NewApi")
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            bleScanResult(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results){
                Log.e(TAG, (new StringBuilder("onBatchScanResults : ").append(result.getDevice().getAddress())).toString());
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, (new StringBuilder("# BLE Scan Error..[ErrorCode] : ").append(errorCode)).toString());
        }
    };

    private void bleScanResult(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord){
        if(bluetoothDevice != null && scanRecord != null){
            Beacon ibeacon = Beacon.fromScanData(scanRecord, rssi, bluetoothDevice);
            if(ibeacon !=null) {
                Log.e(TAG, (new StringBuilder("# Beacon : ").append(bluetoothDevice.getName())
                        .append("-").append(bluetoothDevice.getAddress())).toString());
                bleHandler.obtainMessage(BLE_SCAN_BEACON, ibeacon).sendToTarget();
            }else{
                Log.e(TAG, (new StringBuilder("# Bluetooth LE Device : ").append(bluetoothDevice.getName())
                        .append("-").append(bluetoothDevice.getAddress())).toString());
                bleHandler.obtainMessage(BLE_SCAN_DEVICE, bluetoothDevice).sendToTarget();
            }
        }
    }

    public void scan(boolean isScan, boolean isTimer){
        if(isScan){
            if(isScanning)
                return;
            startBLEScan();
            if(isTimer)
                bleHandler.postDelayed(scanStopRunnable, scanTime);
        }else{
            if(isScanning) stopBLEScan();
        }
    }

    private Runnable scanStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopBLEScan();
        }
    };
    //endregion


    //region    Bluetooth LE Service
    public void bindService(){
        if(isBind)
            return;
        Intent gattServiceIntent = new Intent(context, BluetoothLEService.class);
        context.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void unBindService(){
        if(!isBind)
            return;
        try{
            isBind = false;
            context.unbindService(serviceConnection);
        }catch (Exception e){
            e.getStackTrace();
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e(TAG, "# Bluetooth Service Connected..");
            bluetoothLEService = ((BluetoothLEService.ServiceBinder) iBinder).getBLEService();
            bluetoothLEService.initialize();
            isBind = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "# Bluetooth Service DisConnected..");
            bluetoothLEService = null;
            isBind = false;
        }
    };

    public void connectDevice(BluetoothDevice device){
        if(!isBind || bluetoothLEService == null)
            return;
        bluetoothLEService.connectGatt(device);
    }

    public void disconnectDevice(){
        if(!isBind || bluetoothLEService == null)
            return;
        bluetoothLEService.disconnectGatt();
    }

    public List<String> getGattServices(){
        if(!isBind || bluetoothLEService == null)
            return null;
        return bluetoothLEService.getServiceUUID();
    }

    public List<String> getGattCharacteristics(String serviceUUID){
        if(!isBind || bluetoothLEService == null)
            return null;
        return bluetoothLEService.getCharacteristicUUID(serviceUUID);
    }

    public boolean notifyCharacteristic(String serviceUUID, String charUUID){
        return bluetoothLEService.setNotifyCharacteristic(serviceUUID, charUUID);
    }

    public void writeCharacteristic(String writeData){
        bluetoothLEService.writeCharacteristic(writeData);
    }
    //endregion


    //region Service Broadcast Receiver
    public void registerReceiver(){
        if(isRegister)
            return;
        context.registerReceiver(gattReceiver, BluetoothLEService.gattIntentFilter());
        isRegister = true;
    }

    public void unregisterReceiver(){
        if(!isRegister)
            return;
        context.unregisterReceiver(gattReceiver);
        isRegister = false;
    }

    private BroadcastReceiver gattReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "# Broadcast : " + action);
            assert action != null;
            switch (action) {
                case BluetoothLEService.ACTION_GATT_CONNECTED:
                    bleHandler.obtainMessage(BLE_CONNECT_DEVICE).sendToTarget();
                    break;
                case BluetoothLEService.ACTION_GATT_SERVICES_DISCOVERED:
                    bleHandler.obtainMessage(BLE_SERVICES_DISCOVERED).sendToTarget();
                    break;
                case BluetoothLEService.ACTION_CHARACTERISTIC_READ:
                    bleHandler.obtainMessage(BLE_READ_CHARACTERISTIC).sendToTarget();
                    break;
                case BluetoothLEService.ACTION_DATA_AVAILABLE:
                    bleHandler.obtainMessage(BLE_DATA_AVAILABLE,
                            intent.getStringExtra(BluetoothLEService.EXTRA_KEY_CHANGE_VALUE)).sendToTarget();
                    break;
                case BluetoothLEService.ACTION_GATT_DISCONNECTED:
                    bleHandler.obtainMessage(BLE_DISCONNECT_DEVICE).sendToTarget();
                    break;
            }
        }
    };
    //endregion

}
