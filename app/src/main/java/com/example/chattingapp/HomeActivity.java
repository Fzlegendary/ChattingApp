package com.example.chattingapp;

import static android.util.Log.DEBUG;

import android.app.Dialog;
import android.content.Intent;
import android.nfc.Tag;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

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

        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Handle window insets
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

        // Drawer menu click
        rightDrawer.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_logout) {
                mAuth.signOut();
                startActivity(new Intent(HomeActivity.this, Log_in_Activity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
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

        // FAB new chat
        fabNewChat.setOnClickListener(v -> showNewChatDialog());

      //  loadChatsFromPrefs();
        loadChats();
    }

    // ---------------------------
    // Load chats from Firebase
    // ---------------------------
    private void loadChats() {
        String currentUid = mAuth.getCurrentUser().getUid();
        if (currentUid == null) return;

        DatabaseReference userChatsRef = usersRef.child(currentUid).child("chats");
        userChatsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                chatList.clear();

                for (DataSnapshot chatSnap : snapshot.getChildren()) {
                    String chatId = chatSnap.getKey();

                    DatabaseReference chatRef = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(currentUid)
                            .child("chats")
                            .child(chatId);

                    chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot chatData) {
                            String lastMessage = "";
                            if (chatData.child("messages").exists()) {
                                for (DataSnapshot msgSnap : chatData.child("messages").getChildren()) {
                                    lastMessage = msgSnap.child("text").getValue(String.class);
                                }
                            }

                            String chatName = chatData.child("nameMap").child(currentUid).getValue(String.class);
                            if (chatName == null) {
                                chatName = chatId; // fallback to chatId if no name set
                            }

                            chatList.add(new ChatItem(chatId, chatId, chatName, lastMessage, false, null));
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

    // ---------------------------
    // New chat dialog
    // ---------------------------
    private void showNewChatDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_new_chat);

        EditText inputEmail = dialog.findViewById(R.id.inputUserUid); // user enters email
        EditText username = dialog.findViewById(R.id.inputUsername);
        Button btnCreate = dialog.findViewById(R.id.btnCreateChat);

        btnCreate.setOnClickListener(v -> {
            String name = username.getText().toString().trim();
            String otherEmail = inputEmail.getText().toString().trim();

            if (otherEmail.isEmpty()) {
                inputEmail.setError("Enter email");
                return;
            }

            // Lookup UID in database
            String safeEmail = otherEmail.replace(".", "_").replace("@", "_");
            usersRef.child(safeEmail).child("uid")
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

                        createChatWithOtherUid(otherUid, name);
                        dialog.dismiss();
                    });
        });

        dialog.show();
    }


    // ---------------------------
    // Create chat only using sender UID
    // ---------------------------
    private void createChatWithOtherUid(String otherUid, String name) {
        String currentUid = mAuth.getCurrentUser().getUid();
        if (currentUid == null || otherUid == null || currentUid.equals(otherUid)) {
            Toast.makeText(this, "cant add yourself nor leave email empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate chatId consistently
        String chatId = currentUid + "_" + otherUid;
        Log.d("DEBUG", "createChatWithOtherUid: chatID created.");

        DatabaseReference chatRefCurrent = usersRef.child(currentUid).child("chats").child(chatId);
        DatabaseReference chatRefOther = usersRef.child(otherUid).child("chats").child(chatId);
        Log.d("DEBUG", "createChatWithOtherUid: got prefs.");


        // Use a single update map for both users
        long timestamp = System.currentTimeMillis();
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("participants/" + currentUid, true);
        chatData.put("participants/" + otherUid, true);
        chatData.put("lastMessage", "");
        chatData.put("timestamp", timestamp);

        Log.d("DEBUG", "createChatWithOtherUid: created hashmap");

        // Check if chat exists and create atomically
        chatRefCurrent.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;

            if (!task.getResult().exists()) {
                Log.d("DEBUG", "createChatWithOtherUid: task successful ");

                // Update both users
                chatRefCurrent.updateChildren(chatData);
                chatRefOther.updateChildren(chatData);

                Log.d("DEBUG", "createChatWithOtherUid: updates users.");
                // Add to local list
                ChatItem newChat = new ChatItem(chatId, otherUid, name, "", false, null);
                chatList.add(newChat);
                adapter.updateFullList(chatList);
                adapter.notifyDataSetChanged();
                Log.d("DEBUG", "createChatWithOtherUid: added chat to fullList");

            } else {
                Toast.makeText(HomeActivity.this, "Chat already exists", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------
    // SharedPreferences
    // ---------------------------
//    private void saveChatsToPrefs() {
//        if (chatList == null || chatList.isEmpty()) return;
//
//        JSONArray jsonArray = new JSONArray();
//        try {
//            for (ChatItem chat : chatList) {
//                JSONObject obj = new JSONObject();
//                obj.put("chatId", chat.getChatId());
//                obj.put("otherUid", chat.getUserUid());
//                obj.put("name", chat.getName());
//                obj.put("lastMessage", chat.getLastMessage());
//                obj.put("profile", chat.getProfileImageUrl());
//                jsonArray.put(obj);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        getSharedPreferences("chat_prefs", MODE_PRIVATE)
//                .edit()
//                .putString("chat_list", jsonArray.toString())
//                .apply();
//    }

//    private void loadChatsFromPrefs() {
//        String json = getSharedPreferences("chat_prefs", MODE_PRIVATE)
//                .getString("chat_list", null);
//
//        if (json == null) return;
//
//        try {
//            JSONArray jsonArray = new JSONArray(json);
//            chatList.clear();
//            for (int i = 0; i < jsonArray.length(); i++) {
//                JSONObject obj = jsonArray.getJSONObject(i);
//                ChatItem chat = new ChatItem(
//                        obj.getString("chatId"),
//                        obj.getString("otherUid"),
//                        obj.getString("name"),
//                        obj.getString("lastMessage"),
//                        false,
//                        obj.optString("profile", null)
//                );
//                chatList.add(chat);
//            }
//            adapter.updateFullList(chatList);
//            adapter.notifyDataSetChanged();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    @Override
    public void finish() {
        super.finish();
//        saveChatsToPrefs();
    }
}
