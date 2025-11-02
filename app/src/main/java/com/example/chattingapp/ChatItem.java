package com.example.chattingapp;

public class ChatItem {
    private String userUid;
    private String name;
    private String lastMessage;
    private boolean sentByCurrentUser;
    private String profileImageUrl;

    public ChatItem() {} // default constructor

    public ChatItem(String userUid, String name, String lastMessage, boolean sentByCurrentUser, String profileImageUrl) {
        this.userUid = userUid;
        this.name = name;
        this.lastMessage = lastMessage;
        this.sentByCurrentUser = sentByCurrentUser;
        this.profileImageUrl = profileImageUrl;
    }

    public String getName() {
        return name;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public boolean isSentByCurrentUser() {
        return sentByCurrentUser;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getUserUid() {
        return null;
    }
}
