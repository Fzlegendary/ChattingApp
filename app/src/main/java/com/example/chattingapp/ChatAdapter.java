package com.example.chattingapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private List<ChatItem> chatList;
    private List<ChatItem> fullList;
    private Context context;

    public ChatAdapter(Context context, List<ChatItem> chatList) {
        this.context = context;
        this.chatList = chatList;
        this.fullList = new ArrayList<>(chatList);
    }

    public void updateFullList(List<ChatItem> newList) {
        fullList = new ArrayList<>(newList);
    }

    public void filter(String text) {
        chatList.clear();

        if (TextUtils.isEmpty(text)) {
            chatList.addAll(fullList);
        } else {
            String search = text.toLowerCase();
            for (ChatItem item : fullList) {
                if (item.getName().toLowerCase().contains(search)) {
                    chatList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatAdapter.ViewHolder holder, int position) {
        ChatItem chat = chatList.get(position);

        holder.tvName.setText(chat.getName());
        holder.tvLastMessage.setText(chat.getLastMessage());

        Glide.with(holder.itemView.getContext())
                .load(chat.getProfileImageUrl())
                .placeholder(R.drawable.ic_person_foreground)
                .into(holder.imgProfile);

        // Click to open chat
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("receiverUid", chat.getUserUid());
            intent.putExtra("senderUid", FirebaseAuth.getInstance().getCurrentUser().getUid());
            context.startActivity(intent);
        });

        // Long-click dialog with Edit Name first, then Delete
        holder.itemView.setOnLongClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return true;

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Chat Options")
                    .setItems(new CharSequence[]{"Edit Name", "Delete Chat"}, (dialog, which) -> {
                        ChatItem selectedChat = chatList.get(adapterPos);

                        if (which == 0) {
                            // Edit name
                            showEditNameDialog(selectedChat, adapterPos);
                        } else if (which == 1) {
                            // Delete chat locally
                            chatList.remove(adapterPos);
                            fullList.removeIf(item -> item.getChatId().equals(selectedChat.getChatId()));
                            notifyDataSetChanged();
                            Toast.makeText(context, "Chat removed", Toast.LENGTH_SHORT).show();

                            // Remove from Firebase
                            DatabaseReference chatRef = FirebaseDatabase.getInstance()
                                    .getReference("chats")
                                    .child(selectedChat.getChatId());
                            chatRef.removeValue();
                        }
                    }).show();
            return true;
        });
    }

    private void showEditNameDialog(ChatItem chat, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Chat Name");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(chat.getName());
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                chat.setName(newName);
                notifyItemChanged(position);
                // Update in fullList
                for (ChatItem item : fullList) {
                    if (item.getChatId().equals(chat.getChatId())) {
                        item.setName(newName);
                        break;
                    }
                }
                // Update in Firebase
                DatabaseReference chatRef = FirebaseDatabase.getInstance()
                        .getReference("chats")
                        .child(chat.getChatId())
                        .child("nameMap")
                        .child(FirebaseAuth.getInstance().getCurrentUser().getUid());
                chatRef.setValue(newName)
                        .addOnFailureListener(e ->
                                Toast.makeText(context, "Failed to update name", Toast.LENGTH_SHORT).show()
                        );
            }
            else{
                input.setError("name cannot be empty");
                Toast.makeText(context, input.getError(), Toast.LENGTH_SHORT).show();
            }

        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProfile;
        TextView tvName, tvLastMessage;

        ViewHolder(View itemView) {
            super(itemView);
            imgProfile = itemView.findViewById(R.id.imgProfile);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
        }
    }
}
