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
    private final String chatId;

    public MessageAdapter(List<Message> messages, String currentUserUid, String chatId) {
        this.messages = messages;
        this.currentUserUid = currentUserUid != null ? currentUserUid : "";
        this.chatId = chatId;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        if ("date_separator".equals(message.getSender())) {
            return 2; // date separator
        } else if (message.getSender().equals(currentUserUid)) {
            return 1; // sent message
        } else {
            return 0; // received message
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        if (viewType == 2) {
            layout = R.layout.item_date_seperator;
        } else if (viewType == 1) {
            layout = R.layout.item_message_sent;
        } else {
            layout = R.layout.item_message_received;
        }

        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Message message = messages.get(position);
        if (message == null) return;

        int viewType = getItemViewType(position);

        if (viewType == 2) {
            holder.tvDate.setText(message.getText());
            return;
        }

        if (viewType == 1) {
            holder.itemView.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Delete Message")
                        .setMessage("Are you sure you want to delete this message?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            int adapterPos = holder.getAdapterPosition();
                            if (adapterPos == RecyclerView.NO_POSITION) return;

                            Message messageToDelete = messages.get(adapterPos);

                            if (chatId != null && messageToDelete.getUid() != null) {
                                DatabaseReference globalChatRef = FirebaseDatabase.getInstance()
                                        .getReference("chats")
                                        .child(chatId);

                                DatabaseReference messagesRef = globalChatRef.child("messages");
                                messagesRef.child(messageToDelete.getUid()).removeValue();

                                // Update last message if necessary
                                globalChatRef.child("lastMessage").addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String lastUid = snapshot.child("uid").getValue(String.class);
                                        if (lastUid != null && lastUid.equals(messageToDelete.getUid())) {
                                            Message newLast = null;
                                            for (int i = messages.size() - 1; i >= 0; i--) {
                                                Message m = messages.get(i);
                                                if (!"date_separator".equals(m.getSender()) && !m.getUid().equals(messageToDelete.getUid())) {
                                                    newLast = m;
                                                    break;
                                                }
                                            }

                                            if (newLast != null) {
                                                Map<String, Object> lastMsgMap = new HashMap<>();
                                                lastMsgMap.put("text", newLast.getText());
                                                lastMsgMap.put("sender", newLast.getSender());
                                                lastMsgMap.put("timestamp", newLast.getTimestamp());
                                                lastMsgMap.put("uid", newLast.getUid());
                                                globalChatRef.child("lastMessage").setValue(lastMsgMap);
                                                globalChatRef.child("timestamp").setValue(newLast.getTimestamp());
                                            } else {
                                                globalChatRef.child("lastMessage").removeValue();
                                                globalChatRef.child("timestamp").removeValue();
                                            }
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) { }
                                });
                            }

                            messages.remove(adapterPos);
                            notifyItemRemoved(adapterPos);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
                return true;
            });
        }

        holder.tvMessage.setText(message.getText());

        String timeStr = "";
        if (message.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            timeStr = sdf.format(new Date(message.getTimestamp()));
        }
        holder.tvTime.setText(timeStr);

        if (viewType == 0) {
            String sender = message.getSender();
            if (nicknameCache.containsKey(sender)) {
                holder.tvNickname.setText(nicknameCache.get(sender));
            } else {
                DatabaseReference userRef = FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(sender)
                        .child("chats")
                        .child(chatId)
                        .child("chatName");
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
        TextView tvMessage, tvTime, tvNickname, tvDate;

        ViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            if (viewType == 2) {
                tvDate = itemView.findViewById(R.id.tvDate);
            } else {
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTime = itemView.findViewById(R.id.tvMessageTime);
                tvNickname = itemView.findViewById(R.id.tvMessageNickname);
            }
        }
    }
}
