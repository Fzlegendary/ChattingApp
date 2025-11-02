package com.example.chattingapp;

public class Message {
    private String senderUid;
    private String text;
    private long timestamp;

    public Message() {}

    public Message(String senderUid, String text, long timestamp) {
        this.senderUid = senderUid;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getSenderUid() { return senderUid; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
}
