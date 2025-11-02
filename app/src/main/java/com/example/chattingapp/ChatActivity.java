package com.example.chattingapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private String senderUid, receiverUid, chatId;
    private RecyclerView recyclerView;
    private MessagesAdapter messagesAdapter;
    private List<Message> messagesList;
    private EditText etMessage;
    private ImageButton btnSend;
    private DatabaseReference messagesRef, chatRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 1️⃣ Get sender and receiver UIDs from Intent
        senderUid = getIntent().getStringExtra("senderUid");
        receiverUid = getIntent().getStringExtra("receiverUid");

        // 2️⃣ Generate unique chatId
        chatId = senderUid.compareTo(receiverUid) < 0
                ? senderUid + "_" + receiverUid
                : receiverUid + "_" + senderUid;

        // 3️⃣ Setup Firebase references
        messagesRef = FirebaseDatabase.getInstance().getReference("chats")
                .child(chatId)
                .child("messages");

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId);
        chatRef.child("participants").child(senderUid).setValue(true);
        chatRef.child("participants").child(receiverUid).setValue(true);

        // 4️⃣ Setup UI
        recyclerView = findViewById(R.id.recyclerViewMessages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesList = new ArrayList<>();
        messagesAdapter = new MessagesAdapter(this, messagesList, senderUid);
        recyclerView.setAdapter(messagesAdapter);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        // 5️⃣ Load messages automatically
        loadMessages();

        // 6️⃣ Send message on click
        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                sendMessage(text);
                etMessage.setText("");
            }
        });
    }

    private void loadMessages() {
        messagesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messagesList.clear();
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    Message message = msgSnap.getValue(Message.class);
                    messagesList.add(message);
                }
                messagesAdapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(messagesList.size() - 1);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendMessage(String text) {
        String messageId = messagesRef.push().getKey();
        long timestamp = System.currentTimeMillis();
        Message message = new Message(senderUid, text, timestamp);

        if (messageId != null) {
            messagesRef.child(messageId).setValue(message);
            chatRef.child("lastMessage").setValue(text);
        }
    }
}
