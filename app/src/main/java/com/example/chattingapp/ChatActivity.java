package com.example.chattingapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private String senderSafeEmail, chatId;
    private RecyclerView recyclerView;
    private MessageAdapter messagesAdapter;
    private List<Message> messagesList;
    private EditText etMessage;
    private ImageButton btnSend;
    private DatabaseReference messagesRef, chatRef;
    private ValueEventListener messagesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_chat);

        senderSafeEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_").replace("@", "_");
        chatId = getIntent().getStringExtra("chatId");
        if (senderSafeEmail == null || chatId == null) {
            finish();
            return;
        }

        // Firebase refs
        chatRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(senderSafeEmail)
                .child("chats")
                .child(chatId);
        messagesRef = chatRef.child("messages");

        // UI
        recyclerView = findViewById(R.id.recyclerViewMessages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesList = new ArrayList<>();
        messagesAdapter = new MessageAdapter(messagesList);
        recyclerView.setAdapter(messagesAdapter);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        loadMessages();

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                sendMessage(text);
                etMessage.setText("");
                loadMessages();
            }
        });
    }

    private void loadMessages() {
        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messagesList.clear();
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    Message message = msgSnap.getValue(Message.class);
                    if (message != null) {
                        messagesList.add(message);
                    }
                }
                messagesAdapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(messagesList.size() - 1);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        messagesRef.addValueEventListener(messagesListener);
    }

    private void sendMessage(String text) {
        String messageId = messagesRef.push().getKey();
        long timestamp = System.currentTimeMillis();
        Message message = new Message(FirebaseAuth.getInstance().getCurrentUser().getUid(), text, timestamp);

        if (messageId != null) {
            messagesRef.child(messageId).setValue(message);
            chatRef.child("lastMessage").setValue(text);
            chatRef.child("lastTimestamp").setValue(timestamp);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesRef.removeEventListener(messagesListener);
        }
    }
}
