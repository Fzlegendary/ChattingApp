package com.example.chattingapp;

public class Message {
    private String uid; // Firebase key
    private String sender;
    private String text;
    private long timestamp;

    public Message() {}

    public Message(String uid, String sender, String text, long timestamp) {
        this.uid = uid;
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
