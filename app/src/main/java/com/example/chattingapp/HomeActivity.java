package com.example.chattingapp;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
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

    private TextView tvEmail, tvName;
    private ImageView ivImage;

    private EditText searchBar;
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatItem> chatList;

    private DatabaseReference usersRef;
    private DatabaseReference chatsRef;
    private ChildEventListener chatsListener;

    private String safeEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        safeEmail = mAuth.getCurrentUser().getEmail().replace(".", "_").replace("@", "_");

        DatabaseReference uidRef = FirebaseDatabase.getInstance().getReference()
                .child("users")
                .child(safeEmail)
                .child("uid");
        uidRef.setValue(mAuth.getCurrentUser().getUid());

        drawerLayout = findViewById(R.id.drawerLayout);
        rightDrawer = findViewById(R.id.rightDrawer);

        View headerView = rightDrawer.getHeaderView(0);
        tvEmail = headerView.findViewById(R.id.navHeaderEmail);
        tvName = headerView.findViewById(R.id.navHeaderName);
        ivImage = headerView.findViewById(R.id.navHeaderImage);

        tvEmail.setText(mAuth.getCurrentUser().getEmail());
        String displayName = mAuth.getCurrentUser().getDisplayName();
        tvName.setText(displayName != null ? displayName : "Unknown");

        Uri photoUri = mAuth.getCurrentUser().getPhotoUrl();
        if (photoUri != null) ivImage.setImageURI(photoUri);

        searchBar = findViewById(R.id.searchBar);
        FloatingActionButton fabNewChat = findViewById(R.id.fabNewChat);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        chatList = new ArrayList<>();
        adapter = new ChatAdapter(this, chatList, safeEmail);
        recyclerView.setAdapter(adapter);

        usersRef = FirebaseDatabase.getInstance().getReference("users");
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_menu_24);
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

        loadChats();
        listenForGlobalChats();
    }

    private void loadChats() {
        DatabaseReference userChatsRef = usersRef.child(safeEmail).child("chats");

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

                chatList.add(new ChatItem(chatId, chatId, chatName, lastText, false, "", lastSender,0));
            }

            adapter.updateFullList(chatList);
            adapter.notifyDataSetChanged();
        });
    }

    private void listenForGlobalChats() {
        chatsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                if (!snapshot.child("participants").child(safeEmail).exists()) return;

                String chatId = snapshot.getKey();
                if (chatId == null) return;

                String chatName = snapshot.child("chatName").getValue(String.class);
                if (chatName == null) chatName = "(unknown)";

                String lastText = "";
                String lastSender = "";
                if (snapshot.child("lastMessage").exists()) {
                    lastText = snapshot.child("lastMessage").child("text").getValue(String.class);
                    lastSender = snapshot.child("lastMessage").child("sender").getValue(String.class);
                }

                boolean existsLocally = false;
                for (ChatItem item : chatList) {
                    if (item.getChatId().equals(chatId)) {
                        existsLocally = true;
                        break;
                    }
                }

                if (!existsLocally) {
                    chatList.add(new ChatItem(chatId, chatId, chatName, lastText, false, "", lastSender,0));
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

            String safeOther = otherEmail.replace(".", "_").replace("@", "_");

            usersRef.child(safeOther).child("uid").get()
                    .addOnCompleteListener(task -> {
                        String otherUid = task.getResult().getValue(String.class);

                        if (otherUid == null) {
                            Toast.makeText(HomeActivity.this, "Email not registered", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        createChatWithOtherEmail(safeEmail, safeOther, name);
                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    private void createChatWithOtherEmail(String current, String other, String name) {
        if (current.equals(other)) {
            Toast.makeText(this, "Cannot chat with yourself", Toast.LENGTH_SHORT).show();
            return;
        }

        String chatId = current + "|" + other;

        DatabaseReference chatRefCurrent = usersRef.child(current).child("chats").child(chatId);
        DatabaseReference chatRefOther = usersRef.child(other).child("chats").child(chatId);

        long timestamp = System.currentTimeMillis();

        Map<String, Object> chatMeta = new HashMap<>();
        chatMeta.put("participants/" + current, true);
        chatMeta.put("participants/" + other, true);
        chatMeta.put("chatName", name);
        chatMeta.put("timestamp", timestamp);

        Map<String, Object> lastMsg = new HashMap<>();
        lastMsg.put("text", "");
        lastMsg.put("sender", "");
        chatMeta.put("lastMessage", lastMsg);

        chatRefCurrent.updateChildren(chatMeta);
        chatRefOther.updateChildren(chatMeta);

        chatsRef.child(chatId).updateChildren(chatMeta);

        chatList.add(new ChatItem(chatId, other, name, "", false, "", "",timestamp));
        adapter.updateFullList(chatList);
        adapter.notifyDataSetChanged();
    }

    private void clearAllUserChatsFromFirebase() {
        DatabaseReference userChats = usersRef.child(safeEmail).child("chats");
        userChats.removeValue().addOnCompleteListener(task -> {
            chatList.clear();
            adapter.updateFullList(chatList);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "All chats cleared", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawer(Gravity.END);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (chatsListener != null) chatsRef.removeEventListener(chatsListener);
    }
}
