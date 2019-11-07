package com.physicomtech.kit.physislibrary.mqtt.data;

public class SubscribeData {

    private String topic;
    private String message;

    public SubscribeData(String topic, String message){
        this.topic = topic;
        this.message = message;
    }

    public String getTopic(){
        return topic;
    }

    public String getMessage(){
        return message;
    }

}
