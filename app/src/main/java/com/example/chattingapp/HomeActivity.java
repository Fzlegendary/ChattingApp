package com.example.chattingapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private Button btnLogout;
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        btnLogout = findViewById(R.id.btnLogout);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        chatList = new ArrayList<>();
        adapter = new ChatAdapter(this, chatList);
        recyclerView.setAdapter(adapter);

        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(HomeActivity.this, Log_in_Activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
        chatList.add(new ChatItem("uid_alice", "Alice", "Hey, how are you?", false, "https://i.pravatar.cc/150?img=1"));
        chatList.add(new ChatItem("uid_bob", "Bob", "See you tomorrow!", false, "https://i.pravatar.cc/150?img=2"));
        chatList.add(new ChatItem("uid_charlie", "Charlie", "Let's play tonight", false, "https://i.pravatar.cc/150?img=3"));
        adapter.notifyDataSetChanged();
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
                            String lastMessage = chatSnap.child("lastMessage").getValue(String.class);
                            String otherUid = getOtherUid(chatSnap.child("participants"), currentUid);

                            if (otherUid != null) {
                                usersRef.child(otherUid).addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot userSnap) {
                                        String name = userSnap.child("name").getValue(String.class);
                                        String profile = userSnap.child("profileImageUrl").getValue(String.class);

                                        chatList.add(new ChatItem(otherUid, name, lastMessage, false, profile));
                                        adapter.notifyDataSetChanged();
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError error) {}
                                });
                            }
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
}
