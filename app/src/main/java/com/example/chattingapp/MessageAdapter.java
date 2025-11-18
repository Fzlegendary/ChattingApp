package com.example.chattingapp;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final List<Message> messages;
    private final String currentUserUid; // should be senderSafeEmail

    public MessageAdapter(List<Message> messages, String currentUserUid) {
        this.messages = messages;
        this.currentUserUid = currentUserUid != null ? currentUserUid : "";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_left, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);
        if (message == null) return;

        String sender = message.getSender() != null ? message.getSender() : "";
        String text = message.getText() != null ? message.getText() : "";
        Log.d("hezi", position + "");
        Log.d("hezi", text);
        holder.tvMessage.setText(text);

        String timeStr = "";
        if (message.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            timeStr = sdf.format(new Date(message.getTimestamp()));
        }
        holder.tvTime.setText(timeStr);

        // Left/right styling
        if (sender.equals(currentUserUid)) {
            holder.tvMessage.setBackgroundResource(R.drawable.bg_message_right);
            holder.tvMessage.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        } else {
            holder.tvMessage.setBackgroundResource(R.drawable.bg_message_left);
            holder.tvMessage.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}
