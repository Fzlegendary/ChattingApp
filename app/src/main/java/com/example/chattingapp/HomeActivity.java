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
import android.widget.ImageButton;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

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

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // TOOLBAR + HAMBURGER BUTTON ON RIGHT
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_menu_24); // Your hamburger icon
        toolbar.setNavigationContentDescription("Menu");
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(Gravity.END));
        toolbar.setTitleCentered(true);
// Force icon to appear on the right side
        toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Drawer menu click
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

        // SEARCH FILTER
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // FAB - new chat dialog
        fabNewChat.setOnClickListener(v -> showNewChatDialog());

        // Load chats from SharedPreferences first
        loadChatsFromPrefs();
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
                                            String profile = userSnap.child("profileImageUrl").getValue(String.class);

                                            DatabaseReference nameRef = chatsRef.child(chatId)
                                                    .child("nameMap")
                                                    .child(currentUid);

                                            nameRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(DataSnapshot snapshot) {
                                                    String chatName = snapshot.getValue(String.class);
                                                    if (chatName == null) chatName = "(Unknown User)";

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
                                                                chatName,
                                                                lastMessage,
                                                                false,
                                                                profile
                                                        ));
                                                    } else {
                                                        existing.setLastMessage(lastMessage);
                                                        existing.setName(chatName);
                                                    }

                                                    adapter.updateFullList(chatList);
                                                    adapter.notifyDataSetChanged();

                                                    // Save to SharedPreferences whenever we load from Firebase
                                                    saveChatsToPrefs();
                                                }

                                                @Override
                                                public void onCancelled(DatabaseError error) { }
                                            });
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError error) { }
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

    // ----------------------------
    // NEW CHAT DIALOG
    // ----------------------------
    private void showNewChatDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_new_chat);
        EditText inputEmail = dialog.findViewById(R.id.inputUserUid);
        EditText username = dialog.findViewById(R.id.inputUsername);
        Button btnCreate = dialog.findViewById(R.id.btnCreateChat);

        btnCreate.setOnClickListener(v -> {
            String name = username.getText().toString().trim();
            String email = inputEmail.getText().toString().trim();

            if (email.isEmpty()) {
                inputEmail.setError("Enter an email");
                return;
            }

            // Lookup UID by email asynchronously
            getUidFromEmail(email, new FirebaseUidCallback() {
                @Override
                public void onUidFound(String otherUid) {
                    if (otherUid != null && !otherUid.isBlank()) {
                        createChatWithOtherUserUid(otherUid, name);
                        dialog.dismiss();
                    } else {
                        Toast.makeText(HomeActivity.this, "Invalid UID", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onUidNotFound() {
                    Toast.makeText(HomeActivity.this, "Email not found", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void getUidFromEmail(String email, FirebaseUidCallback callback) {
        if (email.isEmpty()) {
            callback.onUidNotFound();
            return;
        }

        usersRef.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String uid = null;
                            for (DataSnapshot userSnap : snapshot.getChildren()) {
                                uid = userSnap.getKey();
                                break;
                            }
                            if (uid != null) callback.onUidFound(uid);
                            else callback.onUidNotFound();
                        } else {
                            callback.onUidNotFound();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onUidNotFound();
                    }
                });
    }

    private void createChatWithOtherUserUid(String otherUid, String name){
        String currentUid = mAuth.getCurrentUser().getUid();
        String chatId = getChatUidBy(currentUid, otherUid);

        ChatItem e = new ChatItem(chatId, otherUid, name, "", false, null);
        chatList.add(e);
        adapter.notifyDataSetChanged();

        DatabaseReference chatRef = chatsRef.child(chatId);
        chatRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;

            if (!task.getResult().exists()) {
                chatRef.child("participants").child(currentUid).setValue(true);
                chatRef.child("participants").child(otherUid).setValue(true);
                chatRef.child("lastMessage").setValue("");
                chatRef.child("timestamp").setValue(System.currentTimeMillis());
            }
            loadChats();
        });
    }

    private void saveChatsToPrefs() {
        if (chatList == null || chatList.isEmpty()) return;

        JSONArray jsonArray = new JSONArray();
        try {
            for (ChatItem chat : chatList) {
                JSONObject obj = new JSONObject();
                obj.put("chatId", chat.getChatId());
                obj.put("otherUid", chat.getUserUid());
                obj.put("name", chat.getName());
                obj.put("lastMessage", chat.getLastMessage());
                obj.put("profile", chat.getProfileImageUrl());
                jsonArray.put(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        getSharedPreferences("chat_prefs", MODE_PRIVATE)
                .edit()
                .putString("chat_list", jsonArray.toString())
                .apply();
    }

    private void loadChatsFromPrefs() {
        String json = getSharedPreferences("chat_prefs", MODE_PRIVATE)
                .getString("chat_list", null);

        if (json == null) return;

        try {
            JSONArray jsonArray = new JSONArray(json);
            chatList.clear();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                ChatItem chat = new ChatItem(
                        obj.getString("chatId"),
                        obj.getString("otherUid"),
                        obj.getString("name"),
                        obj.getString("lastMessage"),
                        false,
                        obj.optString("profile", null)
                );
                chatList.add(chat);
            }
            adapter.updateFullList(chatList);
            adapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getChatUidBy(String uid1, String uid2) {
        if (uid1 == null || uid2 == null || uid1.equals(uid2)) return null;
        String first = uid1.compareTo(uid2) < 0 ? uid1 : uid2;
        String second = uid1.compareTo(uid2) < 0 ? uid2 : uid1;
        return first + "_" + second;
    }

    @Override
    public void finish() {
        super.finish();
        saveChatsToPrefs();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawer(Gravity.END);
        }
        else super.onBackPressed();
    }
}
