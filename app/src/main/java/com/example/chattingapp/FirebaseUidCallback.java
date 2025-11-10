package com.example.chattingapp;

public interface FirebaseUidCallback {
    void onUidFound(String uid);
    void onUidNotFound();
}

