package com.physiskit.basic.wifi;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.physicomtech.kit.physislibrary.PHYSIsMQTTActivity;

public class MainActivity extends PHYSIsMQTTActivity {

    // Widgets
    EditText etSerialNumber, etSubscribeTopic, etPublishTopic;
    Button btnConnect, btnDisconnect;
    TextView tvSensorType1, tvSensorType2, tvSensorType3, tvSensorType4;
    TextView tvSensingValue1, tvSensingValue2, tvSensingValue3, tvSensingValue4;
    Button btnDigitalHigh, btnDigitalLow, btnAnalogWrite;
    EditText etAnalogValue;

    // MQTT 연결 상태 변수
    private boolean isConnected = false;
    // PHYSIs MQTT 설정 변수
    private String serialNumber, subscribeTopic, publishTopic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initWidget();                   // Widget 초기화
        setButtonEvent();               // 버튼 클릭 이벤트 설정

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(isConnected)
            disconnectMQTT();
    }

    /*  =================================================================
                             MQTT 연결 상태 호출 함수
        ================================================================= */
    @Override
    protected void onMQTTConnectedStatus(boolean result) {
        // MQTT 연결 결과 수신 : MQTT Broker 연결 결과를 전달받을 때 호출
        super.onMQTTConnectedStatus(result);
        // MQTT 연결 상태 저장
        isConnected = result;

        Toast.makeText(getApplicationContext(), "MQTT 연결 결과 : " + isConnected, Toast.LENGTH_SHORT).show();
        // MQTT 연결 성공 시, 지정된 Topic으로 Subscribe 시작
        if(isConnected){
            startSubscribe(serialNumber, subscribeTopic);
        }
    }

    @Override
    protected void onMQTTDisconnected() {
        super.onMQTTDisconnected();
        // MQTT 연결 종료 : MQTT Broker 연결이 종료될 때 호출
        isConnected = false;
    }

    /*  =================================================================
                       MQTT 메시지 수신(Subscribe) 함수
    ! Arduino Sensing Message Protocol : Sensor1 , Sensor2 , Sensor3
    ! 센싱값 사이에 구분자 콤마(,)를 사용하여 하나의 문자열로 메시지 전송
    ================================================================= */
    @SuppressLint("SetTextI18n")
    @Override
    protected void onSubscribeListener(String serialNum, String topic, String data) {
        super.onSubscribeListener(serialNum, topic, data);
        if(serialNum.equals(serialNumber) && topic.equals(subscribeTopic)){                 // 토픽에 따른 데이터 처리
            String[] values = data.split(",");                                      // 구분자 콤마(,)를 기준으로 문자열 나눔
            tvSensingValue1.setText(values[0] + " ℃");                                    // 센싱 측정값 출력
            tvSensingValue2.setText(values[1] +" %");
            tvSensingValue3.setText(values[2] + " Lux");
            String btnState = values[3].equals("1") ? "버튼 DOWN" : "버튼 UP";              // Button Click 시 "1"(HIGH)
            tvSensingValue4.setText(btnState);
        }
    }

    /*  =================================================================
                            애플리케이션 버튼 클릭 설정
        ================================================================= */
    private void setButtonEvent() {
        // MQTT 연결
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // PHYSIs 시리얼번호 및 MQTT Topic 저장
                serialNumber = etSerialNumber.getText().toString();
                subscribeTopic = etSubscribeTopic.getText().toString();
                publishTopic = etPublishTopic.getText().toString();

                if(serialNumber.length() == 0){
                    Toast.makeText(getApplicationContext(), "PHYSIs 시리얼 번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(subscribeTopic.length() == 0){
                    Toast.makeText(getApplicationContext(), "Subscribe (Monitoring) Topic을 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(publishTopic.length() == 0){
                    Toast.makeText(getApplicationContext(), "Publish (Control) Topic을 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // MQTT 연결되지 않았을 경우 연결 함수(connectMQTT) 호출
                if(!isConnected)
                    connectMQTT();
            }
        });

        // MQTT 연결 종료
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // MQTT 연결 상태일 경우, MQTT 연결 종료 함수 호출
                if(isConnected)
                    disconnectMQTT();
            }
        });

        // Digital High 메시지 전송
        btnDigitalHigh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // MQTT 연결 상태일 경우, Digital High 메시지("DH") Publish
                if(isConnected)
                    publish(serialNumber, publishTopic, "DH");
            }
        });

        // Digital Low 메시지 전송
        btnDigitalLow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // MQTT 연결 상태일 경우, Digital Low 메시지("DL") Publish
                if(isConnected)
                    publish(serialNumber, publishTopic, "DL");
            }
        });

        // Analog 값 전송
        btnAnalogWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String analogValue = etAnalogValue.getText().toString();
                if(analogValue.length() == 0){
                    Toast.makeText(getApplicationContext(), "전송할 아날로그 값을 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // MQTT 연결 상태일 경우, Analog 메시지 Publish
                String analogMessage = "A" + analogValue;
                if(isConnected)
                    publish(serialNumber, publishTopic, analogMessage);
            }
        });
    }

    /*  =================================================================
                   애플리케이션 Widget 초기화 & 설정
    ================================================================= */
    private void initWidget() {
        // Widget Object 생성
        etSerialNumber = findViewById(R.id.et_serial_number);
        etSubscribeTopic = findViewById(R.id.et_subscribe_topic);
        etPublishTopic = findViewById(R.id.et_publish_topic);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        tvSensorType1 = findViewById(R.id.tv_sensor_type1);
        tvSensorType2 = findViewById(R.id.tv_sensor_type2);
        tvSensorType3 = findViewById(R.id.tv_sensor_type3);
        tvSensorType4 = findViewById(R.id.tv_sensor_type4);
        tvSensingValue1 = findViewById(R.id.tv_sensor_value1);
        tvSensingValue2 = findViewById(R.id.tv_sensor_value2);
        tvSensingValue3 = findViewById(R.id.tv_sensor_value3);
        tvSensingValue4 = findViewById(R.id.tv_sensor_value4);
        btnDigitalHigh = findViewById(R.id.btn_digital_high);
        btnDigitalLow = findViewById(R.id.btn_digital_low);
        btnAnalogWrite = findViewById(R.id.btn_analog_write);
        etAnalogValue = findViewById(R.id.et_analog_value);

        // Sensor Type 출력
        tvSensorType1.setText("온도");
        tvSensorType2.setText("습도");
        tvSensorType3.setText("조도");
        tvSensorType4.setText("버튼 상태");
        // Sensor Value  출력
        tvSensingValue1.setText("00");
        tvSensingValue2.setText("00");
        tvSensingValue3.setText("00");
        tvSensingValue4.setText("버튼 UP");
    }
}
