package com.example.chattingapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final List<Message> messages;
    private final String currentUserUid;
    private final Map<String, String> nicknameCache = new HashMap<>();

    public MessageAdapter(List<Message> messages, String currentUserUid) {
        this.messages = messages;
        this.currentUserUid = currentUserUid != null ? currentUserUid : "";
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        if (message.getSender().equals(currentUserUid)) {
            return 1; // sent
        } else {
            return 0; // received
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == 1) ? R.layout.item_message_sent : R.layout.item_message_received;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);
        if (message == null) return;

        holder.tvMessage.setText(message.getText());
        String timeStr = "";
        if (message.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            timeStr = sdf.format(new Date(message.getTimestamp()));
        }
        holder.tvTime.setText(timeStr);

        if (!message.getSender().equals(currentUserUid)) {
            // fetch nickname if not cached
            String sender = message.getSender();
            if (nicknameCache.containsKey(sender)) {
                holder.tvNickname.setText(nicknameCache.get(sender));
            } else {
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(sender).child("chatName");
                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String nick = snapshot.getValue(String.class);
                        if (nick != null) {
                            nicknameCache.put(sender, nick);
                            holder.tvNickname.setText(nick);
                        } else {
                            holder.tvNickname.setText("Unknown");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvNickname;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
            tvNickname = itemView.findViewById(R.id.tvMessageNickname); // new TextView in XML
        }
    }
}
