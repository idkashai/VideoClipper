package com.videoclipper;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ClipAdapter extends RecyclerView.Adapter<ClipAdapter.ClipViewHolder> {

    private final List<ClipItem> clips;
    private final OnRemoveListener removeListener;

    public interface OnRemoveListener {
        void onRemove(int position);
    }

    public ClipAdapter(List<ClipItem> clips, OnRemoveListener removeListener) {
        this.clips = clips;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public ClipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_clip, parent, false);
        return new ClipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClipViewHolder holder, int position) {
        ClipItem clip = clips.get(position);

        holder.tvNumber.setText(String.valueOf(position + 1));

        // Remove old watchers to prevent duplicate triggers
        holder.etStart.removeTextChangedListener(holder.startWatcher);
        holder.etEnd.removeTextChangedListener(holder.endWatcher);

        holder.etStart.setText(clip.startTime);
        holder.etEnd.setText(clip.endTime);

        holder.startWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) clips.get(pos).startTime = s.toString();
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        holder.endWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) clips.get(pos).endTime = s.toString();
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        holder.etStart.addTextChangedListener(holder.startWatcher);
        holder.etEnd.addTextChangedListener(holder.endWatcher);

        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_ID) removeListener.onRemove(pos);
        });
    }

    @Override
    public int getItemCount() {
        return clips.size();
    }

    public List<ClipItem> getClips() {
        List<ClipItem> valid = new java.util.ArrayList<>();
        for (ClipItem c : clips) {
            if (!c.startTime.isEmpty() && !c.endTime.isEmpty()) {
                valid.add(c);
            }
        }
        return valid;
    }

    static class ClipViewHolder extends RecyclerView.ViewHolder {
        TextView tvNumber;
        EditText etStart, etEnd;
        ImageButton btnRemove;
        TextWatcher startWatcher, endWatcher;

        ClipViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tvClipNumber);
            etStart = itemView.findViewById(R.id.etStartTime);
            etEnd = itemView.findViewById(R.id.etEndTime);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
