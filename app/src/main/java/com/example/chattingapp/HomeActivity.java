package com.example.chattingapp;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DrawerLayout drawerLayout;
    private NavigationView rightDrawer;

    private EditText searchBar;
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatItem> chatList;

    private DatabaseReference chatsRef;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

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

        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // FAB - New Chat Dialog
        fabNewChat.setOnClickListener(v -> showNewChatDialog());

        // Window insets fix
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Drawer menu
        rightDrawer.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_logout) {
                mAuth.signOut();
                Intent intent = new Intent(HomeActivity.this, Log_in_Activity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
            drawerLayout.closeDrawer(Gravity.END);
            return true;
        });

        // Search filter
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadChats();
    }

    private void loadChats() {
        String currentUid = mAuth.getCurrentUser().getUid();

        chatsRef.orderByChild("participants/" + currentUid).equalTo(true)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        chatList.clear();

                        for (DataSnapshot chatSnap : snapshot.getChildren()) {

                            String chatId = chatSnap.getKey();
                            String lastMessage = chatSnap.child("lastMessage").getValue(String.class);
                            String otherUid = getOtherUid(chatSnap.child("participants"), currentUid);

                            if (otherUid == null) continue;

                            usersRef.child(otherUid)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot userSnap) {

                                            String name = userSnap.child("name").getValue(String.class);
                                            String profile = userSnap.child("profileImageUrl").getValue(String.class);

                                            if (name == null) name = "(Unknown User)";

                                            // Check duplicates by Chat ID
                                            ChatItem existing = null;
                                            for (ChatItem item : chatList) {
                                                if (item.getChatId().equals(chatId)) {
                                                    existing = item;
                                                    break;
                                                }
                                            }

                                            if (existing == null) {
                                                chatList.add(new ChatItem(
                                                        chatId,
                                                        otherUid,
                                                        name,
                                                        lastMessage,
                                                        false,
                                                        profile
                                                ));
                                            } else {
                                                existing.setLastMessage(lastMessage);
                                            }

                                            adapter.updateFullList(chatList);
                                            adapter.notifyDataSetChanged();
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError error) {}
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private String getOtherUid(DataSnapshot participants, String currentUid) {
        for (DataSnapshot snap : participants.getChildren()) {
            if (!snap.getKey().equals(currentUid)) {
                return snap.getKey();
            }
        }
        return null;
    }

    // ---------------------------------------------
    // NEW CHAT DIALOG
    // ---------------------------------------------
    private void showNewChatDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_new_chat);

        EditText inputUid = dialog.findViewById(R.id.inputUserUid);

        dialog.findViewById(R.id.btnCreateChat).setOnClickListener(v -> {

            String otherUid = inputUid.getText().toString().trim();
            String currentUid = mAuth.getCurrentUser().getUid();

            if (otherUid.isEmpty()) {
                inputUid.setError("Enter a UID");
                return;
            }

            // Deterministic unique chat ID
            String chatId = currentUid.compareTo(otherUid) < 0
                    ? currentUid + "_" + otherUid
                    : otherUid + "_" + currentUid;

            DatabaseReference chatRef = chatsRef.child(chatId);

            chatRef.get().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) return;

                if (!task.getResult().exists()) {

                    // Create new chat
                    chatRef.child("participants").child(currentUid).setValue(true);
                    chatRef.child("participants").child(otherUid).setValue(true);
                    chatRef.child("lastMessage").setValue("");

                    chatRef.child("timestamp").setValue(System.currentTimeMillis());
                }

                loadChats();
                adapter.notifyDataSetChanged();

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawer(Gravity.END);
        }
    }
}
