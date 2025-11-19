package com.example.chattingapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private String senderSafeEmail, chatId;
    private RecyclerView recyclerView;
    private MessageAdapter messagesAdapter;
    private List<Message> messagesList;
    private EditText etMessage;
    private ImageButton btnSend;

    private DatabaseReference messagesRef, globalChatRef, usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        senderSafeEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail()
                .replace(".", "_").replace("@", "_");

        chatId = getIntent().getStringExtra("chatId");
        if (chatId == null || chatId.isEmpty()) {
            Toast.makeText(this, "Chat ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        globalChatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId);
        messagesRef = globalChatRef.child("messages");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        recyclerView = findViewById(R.id.recyclerViewMessages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesList = new ArrayList<>();
        messagesAdapter = new MessageAdapter(messagesList, senderSafeEmail);
        recyclerView.setAdapter(messagesAdapter);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        loadMessages();
        Log.d("ChatActivity progress", "loaded messages");
        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                sendMessage(text);
                etMessage.setText(" ");
            }
        });
        Log.d("ChatActivity progress", "set onClick");
    }

    private void loadMessages() {
        messagesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messagesList.clear();
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    Message message = msgSnap.getValue(Message.class);
                    if (message != null) {
                        messagesList.add(message);
                        Log.d("hezi", "onDataChange: message: " + message.getText());
                    }
                }
                Log.d("hezi", "onDataChange: " + messagesList.size());
                messagesAdapter.notifyDataSetChanged();
                if (!messagesList.isEmpty()) {
                    recyclerView.scrollToPosition(messagesList.size() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage(String text) {
        String messageId = messagesRef.push().getKey();
        long timestamp = System.currentTimeMillis();

        if (messageId == null) return;

        Message message = new Message(senderSafeEmail, text, timestamp);
        messagesRef.child(messageId).setValue(message);

        Map<String, Object> lastMsg = new HashMap<>();
        lastMsg.put("text", text);
        lastMsg.put("sender", senderSafeEmail);
        lastMsg.put("timestamp", timestamp);

        globalChatRef.child("lastMessage").setValue(lastMsg);
        globalChatRef.child("timestamp").setValue(timestamp);

        DatabaseReference userUpdateRef = FirebaseDatabase.getInstance().getReference("users");
        String firstEmail = chatId.split("\\|")[0];
        String secondEmail = chatId.split("\\|")[1];
        updateForEmail(firstEmail);
        updateForEmail(secondEmail);

//         Update lastMessage for each participant
//        globalChatRef.child("participants").addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                for (DataSnapshot participantSnap : snapshot.getChildren()) {
//                    String participantEmail = participantSnap.getKey();
//                    if (participantEmail != null) {
//                        DatabaseReference participantChatRef = usersRef.child(participantEmail)
//                                .child("chats")
//                                .child(chatId);
//
//                        Map<String, Object> updateMap = new HashMap<>();
//                        updateMap.put("lastMessage", lastMsg);
//                        updateMap.put("timestamp", timestamp);
//
//                        participantChatRef.updateChildren(updateMap);
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Toast.makeText(ChatActivity.this, "Failed to update participants", Toast.LENGTH_SHORT).show();
//            }
//        });
    }
    private void updateForEmail(String safeEmail){
        DatabaseReference participantChatRef = usersRef.child(safeEmail)
                .child("chats")
                .child(chatId);

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("lastMessage/text", etMessage.getText().toString().trim());
        updateMap.put("lastMessage/sender", senderSafeEmail);
        updateMap.put("lastMessage/timestamp", System.currentTimeMillis());
        updateMap.put("timestamp", System.currentTimeMillis());

        participantChatRef.updateChildren(updateMap);
    }

}
