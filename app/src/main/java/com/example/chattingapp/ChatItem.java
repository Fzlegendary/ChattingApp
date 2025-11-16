package com.example.chattingapp;

public class ChatItem {
    private String chatId;       // unique chat identifier
    private String userUid;      // sender UID for chat reference
    private String name;
    private String lastMessage;
    private String lastSender;   // NEW: stores who sent the last message
    private boolean sentByCurrentUser;
    private String profileImageUrl;

    public ChatItem() {}

    public ChatItem(String chatId, String userUid, String name, String lastMessage,
                    boolean sentByCurrentUser, String profileImageUrl, String lastSender) {
        this.chatId = chatId;
        this.userUid = userUid;
        this.name = name;
        this.lastMessage = lastMessage;
        this.sentByCurrentUser = sentByCurrentUser;
        this.profileImageUrl = profileImageUrl;
        this.lastSender = lastSender;
    }

    public String getChatId() {
        return chatId;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastSender() {
        return lastSender;
    }

    public void setLastSender(String lastSender) {
        this.lastSender = lastSender;
    }

    public boolean isSentByCurrentUser() {
        return sentByCurrentUser;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
}
