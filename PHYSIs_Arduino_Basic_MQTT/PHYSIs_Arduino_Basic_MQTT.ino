#include <PHYSIs_Master.h>            // PHYSIs Kit 라이브러리 포함
#include <DHT11.h>                    // 온습도 센서 라이브러리 포함
#include <Wire.h>                     // I2C 라이브러리 포함
#include <LiquidCrystal_I2C.h>        // I2C LCD 라이브러리

/*
    센서 핀 번호 지정
*/
#define DHT11_PIN 3                    // 온습도 센서 PIN = Digital 3
#define LED_PIN 4                      // LED 버튼 모듈의 LED PIN = Digital 4
#define BTN_PIN 5                      // LED 버튼 모듈의 버튼 PIN = Digital 5  
#define BUZZER_PIN 6                   // BUZZER PIN = Digital 6
#define CDS_PIN A1                     // 조도 센서 PIN = Analog 1  

/*
    객체 선언
*/
PHYSIs_WiFi physisWiFi;                     // PHYSIs WiFi 객체 선언
DHT11 dht11(DHT11_PIN);                    // 온습도 센서 객체 선언
LiquidCrystal_I2C lcd(0x3f, 16, 2);         // I2C LCD 객체 선언

/*
    WiFi/MQTT 설정 정보
*/
String WIFI_SSID     = "U+Net4C63";       // WiFi 명
String WIFI_PWD      = "6000214821";      // WiFi 비밀번호
String SERIAL_NUMBER = "5C313EF0C48F";    // PHYSIs KIT 시리얼번호
String SUBSCRIBE_TOPIC = "con";           // Subscribe(Control) Topic
String PUBLISH_TOPIC = "mon";             // Publish(Monitoring) Topic

/*
    변수 선언
*/
float temperature, humidity;                // 온도, 습도 변수
float illuminance;                          // 조도 변수
long sendTime = 0;                          // 메시지 전송 시간 변수
long sendDelay = 3000;                      // 메시지 전송 딜레이


/*
  +++++   아두이노 초기 설정 함수   +++++
  ! 시리얼 활성화
  ! LED, BUZZER PinMode 설정
  ! PHYSIs WiFi 설정
  ! LCD 설정
*/
void setup() {
  Serial.begin(115200);
  Serial.println(F("!! PHYSIs Basic Coding Kit."));

  setupMqtt();                              // PHYSIs WiFi & MQTT 설정

  pinMode(LED_PIN, OUTPUT);                 // LED On/Off 제어를 위해 PinMode를 Output으로 지정
  pinMode(BTN_PIN, INPUT);                  // Button Up/Down을 감지하기 위해 PinMode를 Input으로 지정
  pinMode(BUZZER_PIN, OUTPUT);              // BUZZER 출력(PWM) 제어를 위해 PinMode를 Output으로 지정

  lcd.init();                               // LCD 초기화
  lcd.backlight();                          // LCD backlight On
}

/*
   +++++   아두이노 반복 함수  +++++
   ! millis 함수를 통한 전송 딜레이 측정
   - 일반적인 delay(ms) 함수 사용할 경우, 프로그램이 지정 밀리초 만큼 멈춤
   - 이 경우, startReceiveMsg의 동작이 멈춰 연결된 BLE 디바이스 또는 애플리케이션의 전송 메시지가 수신되지 않는 현상이 발생
   - 이에, 현재 시간과 메시지 전송 시간 간의 차이를 통해 일정 시간마다 센싱 및 상태 메시지를 전송
*/
void loop() {
  // 메시지 전송 간격 측정
  long sendInterval = millis() - sendTime;
  // 메시지 전송 딜레이 비교
  if (sendInterval > sendDelay) {
    // 메시지 전송 간격이 지정 딜레이 시간 보다 클 경우,
    sensingDHT();                 // 온습도 측정(Digital)
    sensingCDS();                 // 조도 측정(Analog)

    // 온도, 습도, 조도 측정 값에 대한 출력 문자열 생성
    String dhtStr = String(temperature) + " C ," + String(humidity) + " %";
    String cdsStr = String(illuminance) + " %";
    showLcd(dhtStr, cdsStr);      // I2C LCD 출력
    sendMessage();                // 센서 측정값 전송

    sendTime = millis();          // 전송 시간 초기화
  }

  // MQTT 연결 유지 및 수신
  // - startReceiveMsg() 호출하지 않을 경우, MQTT Broker 연결 유지 및 메시지 수신이 이루어지지 않음
  physisWiFi.startReceiveMsg();
}

/*
    PHYSIs WiFi & MQTT 설정 함수
*/
void setupMqtt() {
  physisWiFi.enable();                                      // WiFi 모듈 활성화
  physisWiFi.connectWiFi(WIFI_SSID, WIFI_PWD);              // 지정 WiFi에 대한 연결 요청
  Serial.print("# WiFi Connecting..");
  delay(1000);
  while (!physisWiFi.isWiFiStatus()) {                      // WiFi가 연결될때 까지 반복해서 연결상태를 확인
    Serial.print(".");
    delay(1000);
  }
  Serial.println(F("Connected..."));

  Serial.print("# MQTT Connecting..");
  if (physisWiFi.connectMQTT()) {                           // PHYSIs 플랫폼의 MQTT Broker와 연결
    Serial.println("Success...");                                         // MQTT Broker 연결 성공 시,
    if (physisWiFi.startSubscribe(SERIAL_NUMBER, SUBSCRIBE_TOPIC)) {      // Subscribe 요청
      Serial.print(">> Start Subscirbe : ");
      Serial.println(SUBSCRIBE_TOPIC);
    }
  } else {
    Serial.print("Fail...");
  }
  physisWiFi.subscribeListener = &subscribeListener;        // MQTT Subscribe 수신 리스너 함수 설정
}

/*
    센서 측정 메시지 전송 함수
    - 메시지 프로토콜 : 온도, 습도, 조도, 버튼 상태
    - 센싱값 사이에 구분자 콤마(,)를 사용하여 하나의 문자열 생성
*/
void sendMessage() {
  String publishMsg = String(temperature) + "," + String(humidity) + "," + String(illuminance) + "," + String(digitalRead(BTN_PIN));
  bool pubResult = physisWiFi.publish(SERIAL_NUMBER, PUBLISH_TOPIC, publishMsg);     // 메시지 Publish
  if (pubResult) {
    Serial.print(F("# Pub Data : "));
    Serial.println(publishMsg);
  }
}

/*
    MQTT 메시지 수신 함수
    - 수신 메시지의 시작 문자열에 따라 센서 또는 모듈을 제어
    - "DH" / "DL" : LED On/Off를 제어
    - "A0" ~ "A255" : 부저 소리 출력을 제어
*/
void subscribeListener(String serialNum, String topic, String message) {
  Serial.print(F("# Subscribe Message : "));
  Serial.println(message);
  // Kit 시리얼번호 및 Subscribe Topic 확인
  if (serialNum.equals(SERIAL_NUMBER) && topic.equals(SUBSCRIBE_TOPIC)) {
    // 수신 메시지에 따라 기능 구분
    if (message.equals("DH")) {
      // DH(Digital High) 메시지 수신 시 LED HIGH
      digitalWrite(LED_PIN, HIGH);
    } else if (message.equals("DL")) {
      // DL(Digital Low) 메시지 수신 시 LED LOW
      digitalWrite(LED_PIN, LOW);
    } else if (message.startsWith("A")) {
      // Analog 제어 메시지(수신 문자열의 시작 문자가 'A'인 경우) 수신 시 analogWrite 호출
      String pwmValue = message.substring(1, message.length());      // analog 설정값 추출
      analogWrite(BUZZER_PIN, pwmValue.toInt());
    }
  }
}

/*
    온도, 습도 측정 함수
*/
void sensingDHT() {
  dht11.read(humidity, temperature);        // 온/습도 측정값 temperature, humidity에 저장
  Serial.print("Temperature: ");            // temperature, humidity 값 시리얼 모니터 출력
  Serial.print(temperature);
  Serial.print("%\t");
  Serial.print("Humidity: ");
  Serial.print(humidity);
  Serial.println(" C");
}

/*
    조도 측정 함수
*/
void sensingCDS() {
  illuminance = analogRead(CDS_PIN);                // 센서로부터 아날로그 신호(저항값) 읽기
  illuminance = (1024 - illuminance) / 1024 * 100;  // 저항값에 따른 빛 밝기(%) 계산
  Serial.print("Illuminance: ");
  Serial.print(illuminance);
  Serial.println(" %");
}

/*
    LCD 출력 함수
*/
void showLcd(String str1, String str2) {
  lcd.clear();                        // LCD 화면의 모든 내용 지움
  lcd.setCursor(0, 0);                // LCD의 커서 위치를 첫번째 Row 좌표로 이동
  lcd.print(str1);                    // LCD 화면에 str1 문자 출력
  lcd.setCursor(0, 1);                // LCD의 커서 위치를 두번째 Row 좌표로 이동
  lcd.print(str2);                    // LCD 화면에 str2 문자 출력
}
