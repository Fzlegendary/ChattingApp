package com.example.chattingapp;

public class ChatItem {
    private String chatId;            // NEW: unique chat identifier
    private String userUid;
    private String name;
    private String lastMessage;
    private boolean sentByCurrentUser;
    private String profileImageUrl;

    public ChatItem() {}

    public ChatItem(String chatId, String userUid, String name, String lastMessage,
                    boolean sentByCurrentUser, String profileImageUrl) {
        this.chatId = chatId;
        this.userUid = userUid;
        this.name = name;
        this.lastMessage = lastMessage;
        this.sentByCurrentUser = sentByCurrentUser;
        this.profileImageUrl = profileImageUrl;
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

    public String getLastMessage() {
        return lastMessage;
    }

    public boolean isSentByCurrentUser() {
        return sentByCurrentUser;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    // NEW: update last message without adding a new ChatItem
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }
}
