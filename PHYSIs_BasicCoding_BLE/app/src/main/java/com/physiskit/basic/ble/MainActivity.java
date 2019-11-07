package com.physiskit.basic.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.physicomtech.kit.physislibrary.PHYSIsBLEActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends PHYSIsBLEActivity {

    // region Check Bluetooth Permission
    private static final int REQ_APP_PERMISSION = 1000;
    private static final List<String> appPermissions
            = Collections.singletonList(Manifest.permission.ACCESS_COARSE_LOCATION);

    /*
        # 애플리케이션의 정상 동작을 위한 권한 체크
        - 안드로이드 마시멜로우 버전 이상에서는 일부 권한에 대한 사용자의 허용이 필요
        - 권한을 허용하지 않을 경우, 관련 기능의 정상 동작을 보장하지 않음.
        - 권한 정보 URL : https://developer.android.com/guide/topics/security/permissions?hl=ko
        - PHYSIs Maker Kit에서는 블루투스 사용을 위한 위치 권한이 필요.
     */
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> reqPermissions = new ArrayList<>();
            for(String permission : appPermissions){
                if(checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED){
                    reqPermissions.add(permission);
                }
            }
            if(reqPermissions.size() != 0){
                requestPermissions(reqPermissions.toArray(new String[reqPermissions.size()]), REQ_APP_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQ_APP_PERMISSION){
            boolean accessStatus = true;
            for(int grantResult : grantResults){
                if(grantResult == PackageManager.PERMISSION_DENIED)
                    accessStatus = false;
            }
            if(!accessStatus){
                Toast.makeText(getApplicationContext(), "위치 권한 거부로 인해 애플리케이션을 종료합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    // endregion

    // Widgets
    EditText etSerialNumber;
    Button btnConnect, btnDisconnect;
    ProgressBar pbConnecting;
    TextView tvSensorType1, tvSensorType2, tvSensorType3, tvSensorType4;
    TextView tvSensingValue1, tvSensingValue2, tvSensingValue3, tvSensingValue4;
    Button btnDigitalHigh, btnDigitalLow, btnAnalogWrite;
    EditText etAnalogValue;

    // BLE 연결 상태 변수
    private int connectStateCode;
    // PHYSIs Kit 시리얼 변수
    private String serialNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();

        initWidget();                   // Widget 초기화
        setButtonEvent();               // 버튼 클릭 이벤트 설정

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(connectStateCode == CONNECTED)
            disconnectDevice();
    }

    /*  =================================================================
                         BLE 연결 상태 호출 함수
    ================================================================= */
    @Override
    protected void onBLEConnectedStatus(int result) {
        // BLE 연결 결과 수신 : PHYSIs Kit와 BLE 연결 상태를 전달받을 때 호출
        super.onBLEConnectedStatus(result);
        // BLE 연결 상태 저장
        // CONNECTED(200) = BLE 연결
        // DISCONNECTED(201) = BLE 연결 실패 또는 연결 종료
        // NO_DISCOVERY(202) = 지정된 BLE가 검색되지 않음
        connectStateCode = result;
        Toast.makeText(getApplicationContext(), "BLE 연결 결과 (" + result + ") :" + (result == 200), Toast.LENGTH_SHORT).show();
        pbConnecting.setVisibility(View.INVISIBLE);
    }


    /*  =================================================================
                       BLE 메시지 수신 함수
    ! Arduino Sensing Message Protocol : Sensor1 , Sensor2 , Sensor3
    ! 센싱값 사이에 구분자 콤마(,)를 사용하여 하나의 문자열로 메시지 전송
    ================================================================= */
    @SuppressLint("SetTextI18n")
    @Override
    protected void onBLEReceiveMsg(String msg) {
        super.onBLEReceiveMsg(msg);
        String[] values = msg.split(",");                   // 구분자 콤마(,)를 기준으로 문자열 나눔
        tvSensingValue1.setText(values[0] + " ℃");                // 센싱 측정값 출력
        tvSensingValue2.setText(values[1] +" %");
        tvSensingValue3.setText(values[2] + " Lux");
        String btnState = values[3].equals("1") ? "버튼 DOWN" : "버튼 UP";              // Button Click 시 "1"(HIGH)
        tvSensingValue4.setText(btnState);
    }

    /*  =================================================================
                            애플리케이션 버튼 클릭 설정
        ================================================================= */
    private void setButtonEvent() {
        // BLE 연결
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {



                // PHYSIs 시리얼번호 저장
                serialNumber = etSerialNumber.getText().toString();

                if(serialNumber.length() == 0){
                    Toast.makeText(getApplicationContext(), "PHYSIs 시리얼 번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 연결된 PHYSIs Kit가 없을 경우, 입력된 시리얼번호의 PHYSIs Kit와 BLE 연결을 시도
                if(connectStateCode != CONNECTED){
                    pbConnecting.setVisibility(View.VISIBLE);                   // 연결 진행 Widget 출력
                    connectDevice(serialNumber);                                // BLE 연결 함수 호출
                }
            }
        });

        // BLE 연결 종료
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // PHYSIs Kit와 BLE 연결 상태일 경우, 연결 종료 함수 호출
                if(connectStateCode == CONNECTED)
                    disconnectDevice();
            }
        });

        // Digital High 메시지 전송
        btnDigitalHigh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // PHYSIs Kit와 BLE 연결 상태일 경우, Digital High 메시지("DH") 전송
                if(connectStateCode == CONNECTED)
                    sendMessage("DH");
            }
        });

        // Digital Low 메시지 전송
        btnDigitalLow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // PHYSIs Kit와 BLE 연결 상태일 경우, Digital Low 메시지("DL") 전송
                if(connectStateCode == CONNECTED)
                    sendMessage("DL");
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

                // PHYSIs Kit와 BLE 연결 상태일 경우, Analog 메시지 전송
                String analogMessage = "A" + analogValue;
                if(connectStateCode == CONNECTED)
                    sendMessage(analogMessage);
            }
        });
    }

    /*  =================================================================
                   애플리케이션 Widget 초기화 & 설정
    ================================================================= */
    private void initWidget() {
        // Widget Object 생성
        etSerialNumber = findViewById(R.id.et_serial_number);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        pbConnecting = findViewById(R.id.pb_connecting);
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
