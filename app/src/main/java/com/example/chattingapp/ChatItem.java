package com.example.chattingapp;

public class ChatItem {
    private String chatId;       // unique chat identifier
    private String userUid;      // sender UID for chat reference
    private String name;
    private String lastMessage;
    private String lastSender;   // stores who sent the last message
    private boolean sentByCurrentUser;
    private String profileImageUrl;
    private long lastTimestamp;  // NEW: timestamp of last message

    public ChatItem() {}

    public ChatItem(String chatId, String userUid, String name, String lastMessage,
                    boolean sentByCurrentUser, String profileImageUrl, String lastSender,
                    long lastTimestamp) {   // add timestamp to constructor
        this.chatId = chatId;
        this.userUid = userUid;
        this.name = name;
        this.lastMessage = lastMessage;
        this.sentByCurrentUser = sentByCurrentUser;
        this.profileImageUrl = profileImageUrl;
        this.lastSender = lastSender;
        this.lastTimestamp = lastTimestamp;   // set timestamp
    }

    // --- getters and setters ---

    public String getChatId() { return chatId; }
    public String getUserUid() { return userUid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public String getLastSender() { return lastSender; }
    public void setLastSender(String lastSender) { this.lastSender = lastSender; }
    public boolean isSentByCurrentUser() { return sentByCurrentUser; }
    public String getProfileImageUrl() { return profileImageUrl; }

    // --- timestamp getter and setter ---
    public long getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(long lastTimestamp) { this.lastTimestamp = lastTimestamp; }
}