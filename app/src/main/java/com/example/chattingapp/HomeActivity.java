package com.example.chattingapp;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DrawerLayout drawerLayout;
    private NavigationView rightDrawer;

    private EditText searchBar;
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatItem> chatList;

    private DatabaseReference usersRef;
    private DatabaseReference chatsRef;
    private ChildEventListener chatsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        DatabaseReference uidRef = FirebaseDatabase.getInstance().getReference()
                .child("users")
                .child(FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_").replace("@", "_"))
                .child("uid");
        if(uidRef.getKey() != FirebaseAuth.getInstance().getCurrentUser().getUid())
            uidRef.setValue(FirebaseAuth.getInstance().getCurrentUser().getUid());
        else
            uidRef.onDisconnect();
        drawerLayout = findViewById(R.id.drawerLayout);
        rightDrawer = findViewById(R.id.rightDrawer);
        searchBar = findViewById(R.id.searchBar);
        FloatingActionButton fabNewChat = findViewById(R.id.fabNewChat);

        mAuth = FirebaseAuth.getInstance();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatList = new ArrayList<>();
        adapter = new ChatAdapter(this, chatList);
        recyclerView.setAdapter(adapter);

        usersRef = FirebaseDatabase.getInstance().getReference("users");
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");

        // Window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_menu_24);
        toolbar.setNavigationContentDescription("Menu");
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(Gravity.END));
        toolbar.setTitleCentered(true);
        toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        rightDrawer.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_logout) {
                mAuth.signOut();
                startActivity(new Intent(HomeActivity.this, Log_in_Activity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } else if (item.getItemId() == R.id.nav_clear_chats) {
                clearAllUserChatsFromFirebase();
            }
            drawerLayout.closeDrawer(Gravity.END);
            return true;
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        fabNewChat.setOnClickListener(v -> showNewChatDialog());

        // Load existing chats & listen for updates
        loadChats();
        listenForGlobalChats();
    }

    // -----------------------------
    // Load existing chats from Firebase
    // -----------------------------
    private void loadChats() {
        String safeEmailCurrentUser = mAuth.getCurrentUser().getEmail().replace(".", "_").replace("@", "_");
        DatabaseReference userChatsRef = usersRef.child(safeEmailCurrentUser).child("chats");

        userChatsRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;

            chatList.clear();

            for (DataSnapshot chatSnap : task.getResult().getChildren()) {
                String chatId = chatSnap.getKey();
                if (chatId == null) continue;

                String chatName = chatSnap.child("chatName").getValue(String.class);
                if (chatName == null) chatName = chatId;

                String lastText = "";
                String lastSender = "";
                if (chatSnap.child("lastMessage").exists()) {
                    lastText = chatSnap.child("lastMessage").child("text").getValue(String.class);
                    lastSender = chatSnap.child("lastMessage").child("sender").getValue(String.class);
                }

                chatList.add(new ChatItem(chatId, chatId, chatName, lastText, false,"" ,lastSender));

            }

            adapter.updateFullList(chatList);
            adapter.notifyDataSetChanged();
        });
    }

    // -----------------------------
    // Listen for global chats changes
    // -----------------------------
    private void listenForGlobalChats() {
        String safeEmailCurrentUser = mAuth.getCurrentUser().getEmail().replace(".", "_").replace("@", "_");

        chatsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                if (!snapshot.child("participants").child(safeEmailCurrentUser).exists()) return;

                String chatId = snapshot.getKey();
                if (chatId == null) return;

                String chatName = snapshot.child("chatName").getValue(String.class);
                if (chatName == null) chatName = "(unknown user)";

                String lastText = "";
                String lastSender = "";
                if (snapshot.child("lastMessage").exists()) {
                    lastText = snapshot.child("lastMessage").child("text").getValue(String.class);
                    lastSender = snapshot.child("lastMessage").child("sender").getValue(String.class);
                }

                // Make sure the chat exists under /users
                DatabaseReference userChatRef = usersRef.child(safeEmailCurrentUser).child("chats").child(chatId);
                final String finalChatName = chatName;
                final String finalLastText = lastText;
                final String finalLastSender = lastSender;

                userChatRef.get().addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) return;
                    if (!task.getResult().exists()) {
                        Map<String, Object> chatMeta = new HashMap<>();
                        chatMeta.put("participants", snapshot.child("participants").getValue());
                        chatMeta.put("timestamp", snapshot.child("timestamp").getValue());
                        chatMeta.put("chatName", finalChatName);

                        Map<String, Object> lastMsg = new HashMap<>();
                        lastMsg.put("text", finalLastText);
                        lastMsg.put("sender", finalLastSender);
                        chatMeta.put("lastMessage", lastMsg);

                        userChatRef.updateChildren(chatMeta);
                    }
                });

                boolean exists = false;
                for (ChatItem item : chatList) {
                    if (item.getChatId().equals(chatId)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    chatList.add(new ChatItem(chatId, chatId, chatName, lastText, false,"", lastSender));
                    adapter.updateFullList(chatList);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                String chatId = snapshot.getKey();
                if (chatId == null) return;

                String lastText = "";
                String lastSender = "";
                if (snapshot.child("lastMessage").exists()) {
                    lastText = snapshot.child("lastMessage").child("text").getValue(String.class);
                    lastSender = snapshot.child("lastMessage").child("sender").getValue(String.class);
                }

                for (int i = 0; i < chatList.size(); i++) {
                    ChatItem item = chatList.get(i);
                    if (item.getChatId().equals(chatId)) {
                        item.setLastMessage(lastText);
                        item.setLastSender(lastSender);
                        adapter.notifyItemChanged(i);
                        break;
                    }
                }
            }

            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {}
        };

        chatsRef.addChildEventListener(chatsListener);
    }

    // -----------------------------
    // New chat dialog
    // -----------------------------
    private void showNewChatDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_new_chat);

        EditText inputEmail = dialog.findViewById(R.id.inputUserUid);
        EditText username = dialog.findViewById(R.id.inputUsername);
        Button btnCreate = dialog.findViewById(R.id.btnCreateChat);

        btnCreate.setOnClickListener(v -> {
            String name = username.getText().toString().trim();
            String otherEmail = inputEmail.getText().toString().trim();

            if (otherEmail.isEmpty()) {
                inputEmail.setError("Enter email");
                return;
            }

            String safeEmailOther = otherEmail.replace(".", "_").replace("@", "_");
            String safeEmailCurrent = mAuth.getCurrentUser().getEmail().replace(".", "_").replace("@", "_");

            usersRef.child(safeEmailOther).child("uid")
                    .get().addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            Toast.makeText(HomeActivity.this, "Failed to fetch UID", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String otherUid = task.getResult().getValue(String.class);
                        if (otherUid == null || otherUid.isEmpty()) {
                            Toast.makeText(HomeActivity.this, "Email not registered", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        createChatWithOtherEmail(safeEmailCurrent, safeEmailOther, name);
                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    // -----------------------------
    // Create chat with another user
    // -----------------------------
    private void createChatWithOtherEmail(String creatorSafeEmail, String otherSafeEmail, String chatName) {
        if (creatorSafeEmail.equals(otherSafeEmail)) {
            Toast.makeText(this, "Cannot create chat with yourself", Toast.LENGTH_SHORT).show();
            return;
        }

        String chatId = creatorSafeEmail + "|" + otherSafeEmail;

        DatabaseReference chatRefCurrent = usersRef.child(creatorSafeEmail).child("chats").child(chatId);
        DatabaseReference chatRefOther = usersRef.child(otherSafeEmail).child("chats").child(chatId);

        long timestamp = System.currentTimeMillis();

        Map<String, Object> chatMeta = new HashMap<>();
        chatMeta.put("participants/" + creatorSafeEmail, true);
        chatMeta.put("participants/" + otherSafeEmail, true);
        chatMeta.put("timestamp", timestamp);
        chatMeta.put("chatName", chatName);

        Map<String, Object> lastMsg = new HashMap<>();
        lastMsg.put("text", "");
        lastMsg.put("sender", "");
        chatMeta.put("lastMessage", lastMsg);

        chatRefCurrent.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            if (!task.getResult().exists()) {
                chatRefCurrent.updateChildren(chatMeta);
                chatRefOther.updateChildren(chatMeta);

                DatabaseReference globalChatRef = chatsRef.child(chatId);
                Map<String, Object> globalChatMap = new HashMap<>();
                globalChatMap.put("participants/" + creatorSafeEmail, true);
                globalChatMap.put("participants/" + otherSafeEmail, true);
                globalChatMap.put("timestamp", timestamp);
                globalChatRef.updateChildren(globalChatMap);

                chatList.add(new ChatItem(chatId, otherSafeEmail, chatName, "", false, "", ""));
                adapter.updateFullList(chatList);
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(HomeActivity.this, "Chat already exists", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -----------------------------
    // Clear all chats (local + Firebase)
    // -----------------------------
    private void clearLocalChatList() {
        chatList.clear();
        adapter.updateFullList(chatList);
        adapter.notifyDataSetChanged();
    }

    private void clearAllUserChatsFromFirebase() {
        String safeEmailCurrent = mAuth.getCurrentUser().getEmail().replace(".", "_").replace("@", "_");
        DatabaseReference userChatsRef = usersRef.child(safeEmailCurrent).child("chats");

        userChatsRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(HomeActivity.this, "Failed to fetch chats", Toast.LENGTH_SHORT).show();
                return;
            }

            if (task.getResult().exists()) {
                for (DataSnapshot chatSnap : task.getResult().getChildren()) {
                    String chatId = chatSnap.getKey();
                    if (chatId != null) {
                        // Remove user from global participants
                        DatabaseReference globalChatRef = chatsRef.child(chatId).child("participants").child(safeEmailCurrent);
                        globalChatRef.removeValue();

                        // Remove chat from user's own node
                        userChatsRef.child(chatId).removeValue();
                    }
                }
            }

            clearLocalChatList();
            Toast.makeText(HomeActivity.this, "All chats cleared", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void finish() {
        super.finish();
        if (chatsListener != null) {
            chatsRef.removeEventListener(chatsListener);
        }
    }
}
