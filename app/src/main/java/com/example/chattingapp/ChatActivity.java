package com.example.chattingapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
    private TextView tvChatName;

    private DatabaseReference messagesRef, globalChatRef, usersRef, myChatRef;

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

        usersRef = FirebaseDatabase.getInstance().getReference("users");
        globalChatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId);
        messagesRef = globalChatRef.child("messages");
        myChatRef = usersRef.child(senderSafeEmail).child("chats").child(chatId);

        tvChatName = findViewById(R.id.tvChatName);
        recyclerView = findViewById(R.id.recyclerViewMessages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        messagesList = new ArrayList<>();
        messagesAdapter = new MessageAdapter(messagesList, senderSafeEmail, chatId);
        recyclerView.setAdapter(messagesAdapter);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        loadChatName();   // load per-user nickname
        loadMessages();

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                sendMessage(text);
                etMessage.setText("");
            }
        });

        // Enter key sends message
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            String text = etMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                sendMessage(text);
                etMessage.setText("");
            }
            return true;
        });
    }

    private void loadChatName() {
        myChatRef.child("chatName").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String chatName = snapshot.getValue(String.class);
                if (chatName != null) {
                    tvChatName.setText(chatName);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void loadMessages() {
        messagesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messagesList.clear();

                Message previousMessage = null;
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    Message message = msgSnap.getValue(Message.class);
                    if (message == null) continue;

                    message.setUid(msgSnap.getKey()); // assign Firebase UID

                    // Check if a date separator is needed
                    String messageId = messagesRef.push().getKey();
                    if (previousMessage != null) {
                        Calendar prevCal = Calendar.getInstance();
                        prevCal.setTimeInMillis(previousMessage.getTimestamp());

                        Calendar currCal = Calendar.getInstance();
                        currCal.setTimeInMillis(message.getTimestamp());

                        if (prevCal.get(Calendar.YEAR) != currCal.get(Calendar.YEAR) ||
                                prevCal.get(Calendar.DAY_OF_YEAR) != currCal.get(Calendar.DAY_OF_YEAR)) {
                            // Add a virtual date separator before the current message
                            Message separator = new Message(
                                    messageId,
                                    "date_separator",
                                    formatDateMessage(message.getTimestamp()),
                                    message.getTimestamp()
                            );
                            messagesList.add(separator);
                        }
                    } else {
                        // First message, add date separator
                        Message separator = new Message(
                                messageId,
                                "date_separator",
                                formatDateMessage(message.getTimestamp()),
                                message.getTimestamp()
                        );
                        messagesList.add(separator);
                    }

                    messagesList.add(message);
                    previousMessage = message;
                }

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
        long timestamp = System.currentTimeMillis();

        String messageId = messagesRef.push().getKey();
        if (messageId == null) return;

        Message message = new Message(messageId, senderSafeEmail, text, timestamp);
        messagesRef.child(messageId).setValue(message);

        // Update global last message
        Map<String, Object> lastMsg = new HashMap<>();
        lastMsg.put("text", text);
        lastMsg.put("sender", senderSafeEmail);
        lastMsg.put("timestamp", timestamp);
        lastMsg.put("uid", messageId);

        globalChatRef.child("lastMessage").setValue(lastMsg);
        globalChatRef.child("timestamp").setValue(timestamp);

        // Update participants
        String[] participants = chatId.split("\\|");
        for (String participant : participants) {
            updateParticipantLastMessage(participant, text);
        }

        // Scroll
        recyclerView.post(() -> recyclerView.scrollToPosition(messagesList.size() - 1));
    }

    private void updateParticipantLastMessage(String safeEmail, String text){
        DatabaseReference participantChatRef = usersRef.child(safeEmail)
                .child("chats")
                .child(chatId);

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("lastMessage/text", text);
        updateMap.put("lastMessage/sender", senderSafeEmail);
        updateMap.put("lastMessage/timestamp", System.currentTimeMillis());
        updateMap.put("timestamp", System.currentTimeMillis());

        participantChatRef.updateChildren(updateMap);
    }

    private String formatDateMessage(long timestamp) {
        Calendar cal = Calendar.getInstance();
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);

        int today = cal.get(Calendar.DAY_OF_YEAR);
        int msgDay = msgCal.get(Calendar.DAY_OF_YEAR);
        int year = cal.get(Calendar.YEAR);
        int msgYear = msgCal.get(Calendar.YEAR);

        if (year == msgYear && today == msgDay) return "Today";
        else if (year == msgYear && today - msgDay == 1) return "Yesterday";
        else return new SimpleDateFormat("dd/MM/yyyy").format(msgCal.getTime());
    }
}
