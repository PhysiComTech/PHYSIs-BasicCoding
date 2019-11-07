package com.physicomtech.kit.physislibrary.mqtt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.physicomtech.kit.physislibrary.mqtt.data.SubscribeData;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MQTTClient {

    private static final String TAG = "MQTTClient";

    public static final int MQTT_CONNECTED = 300;
    public static final int MQTT_SUB_LISTEN = 301;
    public static final int MQTT_DISCONNECTED = 302;

    private static MQTTClient mqttClient = null;
    private Context context;
    private MqttClient mClient = null;
    private Handler handler = null;
    private MqttConnectThread mConnectThread = null;

    private MQTTClient(Context context){
        this.context = context;
    }

    public synchronized static MQTTClient getInstance(Context context){
        if(mqttClient == null)
            mqttClient = new MQTTClient(context);
        return mqttClient;
    }

    public void setHandler(Handler handler){
        this.handler = handler;
    }


    public void disconnect(){
        if(mClient != null && mClient.isConnected()){
            try {
                mClient.disconnect();
                handler.obtainMessage(MQTT_DISCONNECTED).sendToTarget();
                mClient = null;
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
        mConnectThread = null;
    }

    public void connect(String ip, String port){
        disconnect();

        // Get Android Device ID
//        @SuppressLint("HardwareIds")
//        String android_ID = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String client_id = MqttClient.generateClientId();
        try {
            mClient = new MqttClient("tcp://" + ip + ":" + port, client_id, new MemoryPersistence());
            mClient.setCallback(mqttCallback);
            mConnectThread = new MqttConnectThread();
            mConnectThread.start();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String topic){
        try {
            if(mClient == null)
                return;
            mClient.subscribeWithResponse(topic, 0, iMqttMessageListener);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private final IMqttMessageListener iMqttMessageListener = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            handler.obtainMessage(MQTT_SUB_LISTEN,
                    new SubscribeData(topic, new String(message.getPayload()))).sendToTarget();
        }
    };

    public void unsubscribe(String topic){
        try {
            if(mClient == null)
                return;
            mClient.unsubscribe(topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message){
        try {
            if(mClient == null)
                return;
            mClient.publish(topic, new MqttMessage(message.getBytes()));
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private MqttCallback mqttCallback = new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {
            Log.e(TAG, "# MQTT Connection Lost.");
            handler.obtainMessage(MQTT_DISCONNECTED).sendToTarget();
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
//            Log.e(TAG, "messageArrived");
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            Log.e(TAG, "deliveryComplete");

        }
    };

    private class MqttConnectThread extends Thread{

        @Override
        public void run() {
            super.run();
            try {
                if(mClient != null && mClient.isConnected())
                    return;
                MqttConnectOptions options = new MqttConnectOptions();
                options.setConnectionTimeout(2);
                mClient.connect(options);
            } catch (MqttException e ) {
                e.printStackTrace();
            }finally {
                assert mClient != null;
                handler.obtainMessage(MQTT_CONNECTED, mClient.isConnected()).sendToTarget();
            }
        }
    }
}
