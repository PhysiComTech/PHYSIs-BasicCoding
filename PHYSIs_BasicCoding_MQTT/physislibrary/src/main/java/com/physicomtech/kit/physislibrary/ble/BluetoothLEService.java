package com.physicomtech.kit.physislibrary.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothLEService extends Service {

    private static final String TAG = "BluetoothLEService";

    /*
            Service Bind
     */
    private final IBinder binder = new ServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "# Bluetooth LE Service Bind..");
        return binder;
    }

    class ServiceBinder extends Binder {
        BluetoothLEService getBLEService(){
            return BluetoothLEService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.e(TAG, "# Bluetooth LE Service UnBind..");
        return super.onUnbind(intent);
    }


    /*
               Ble Service
     */
    public final static String ACTION_GATT_CONNECTED = "com.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_CHARACTERISTIC_READ = "com.bluetooth.le.ACTION_CHARACTERISTIC_READ";

    public final static String EXTRA_KEY_CHANGE_VALUE = "changeValue";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    private BluetoothManager bluetoothManager = null;
    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothGatt bluetoothGatt = null;
    private BluetoothGattCharacteristic gattCharacteristic = null;
    private BluetoothGattCharacteristic notifyCharacteristic;


    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e(TAG, "# Unable to initialize BluetoothManager..");
                return false;
            }
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "# Unable to obtain a BluetoothAdapter..");
            return false;
        }
        return true;
    }

    public static IntentFilter gattIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_CONNECTED);
        intentFilter.addAction(ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        intentFilter.addAction(ACTION_CHARACTERISTIC_READ);
        return intentFilter;
    }

    public void connectGatt(BluetoothDevice device) {
        if(bluetoothManager == null && bluetoothAdapter == null){
            Log.e(TAG, "# Unable to initialize BluetoothLEService..");
            return;
        }
        bluetoothGatt = device.connectGatt(getApplicationContext(), true, gattCallback);
    }

    public void disconnectGatt() {
        if (bluetoothGatt == null) {
            return;
        }
        Log.e(TAG, "# Disconnected Bluetooth Gatt..");
        bluetoothGatt.disconnect();
        bluetoothGatt.close();
        bluetoothGatt = null;
        sendBroadcast(new Intent(ACTION_GATT_DISCONNECTED));
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //super.onConnectionStateChange(gatt, status, newState);
            if(newState == BluetoothProfile.STATE_CONNECTED){
                Log.e(TAG, "# Connected to Bluetooth Gatt Server..");
                Log.e(TAG, "# Attempting to start service discovery:" + bluetoothGatt.discoverServices());
                sendBroadcast(new Intent(ACTION_GATT_CONNECTED));
            }else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.e(TAG, "# Disconnected from Gatt Server..");
                bluetoothGatt.close();
                bluetoothGatt = null;
                sendBroadcast(new Intent(ACTION_GATT_DISCONNECTED));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            //super.onServicesDiscovered(gatt, status);
            sendBroadcast(new Intent(ACTION_GATT_SERVICES_DISCOVERED));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //super.onCharacteristicRead(gatt, characteristic, status);
            sendBroadcast(new Intent(ACTION_CHARACTERISTIC_READ));
//            Log.e(TAG,"# onCharacteristicRead : " + new String(characteristic.getValue()));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //super.onCharacteristicChanged(gatt, characteristic);
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                Intent intent = new Intent(ACTION_DATA_AVAILABLE);
                String value =  new String(data).trim();
                Log.e(TAG,"# onCharacteristic Changed : " + value);
                intent.putExtra(EXTRA_KEY_CHANGE_VALUE, value);
                sendBroadcast(intent);
            }
        }
    };

    public List<String> getServiceUUID(){
        Log.e(TAG, "# Get Gatt Service UUID..");
        List<String> gattServiceList = new ArrayList<>();
        if(bluetoothGatt == null)
            return gattServiceList;
        for(BluetoothGattService gattService : bluetoothGatt.getServices()) {
            Log.e(TAG, "# Service : " + gattService.getUuid().toString());
            gattServiceList.add(gattService.getUuid().toString());
        }
        return gattServiceList;
    }

    public List<String> getCharacteristicUUID(String serviceUUID){
        Log.e(TAG, "# Get Gatt Characteristic UUID..");
        List<String> gattcharList = new ArrayList<>();
        if(bluetoothGatt == null)
            return gattcharList;
        for(BluetoothGattCharacteristic characteristic : bluetoothGatt.getService(UUID.fromString(serviceUUID)).getCharacteristics()) {
            Log.e(TAG, "# Char : " + characteristic.getUuid().toString());
            gattcharList.add(characteristic.getUuid().toString());
//            for(BluetoothGattDescriptor descriptor : characteristic.getDescriptors()){
//                Log.e(TAG, "# Descriptor : " + descriptor.getUuid().toString());
//            }
        }
        return gattcharList;
    }


    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "# BluetoothAdapter not initialized..");
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }


    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "# BluetoothAdapter not initialized..");
            return;
        }
        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public boolean setNotifyCharacteristic(String serviceUUID, String charUUID){
        try{
            gattCharacteristic = bluetoothGatt.getService(UUID.fromString(serviceUUID))
                    .getCharacteristic(UUID.fromString(charUUID));
            final int charaProp = gattCharacteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                // If there is an active notification on a characteristic, clear
                // it first so it doesn't update the data field on the user interface.
                if (notifyCharacteristic != null) {
                    setCharacteristicNotification(notifyCharacteristic, false);
                    notifyCharacteristic = null;
                }
                readCharacteristic(gattCharacteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                notifyCharacteristic = gattCharacteristic;
                setCharacteristicNotification(gattCharacteristic, true);
            }
            return true;
        }catch (Exception e){
            e.getStackTrace();
            return false;
        }
    }

    public boolean writeCharacteristic(String msg) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.e(TAG, "# BluetoothAdapter not initialized..");
            return false;
        }

        if(gattCharacteristic == null){
            Log.e(TAG, "# Characteristic not Notification..");
            return false;
        }

        gattCharacteristic.setValue(msg.getBytes());
        return bluetoothGatt.writeCharacteristic(gattCharacteristic);
    }
}
