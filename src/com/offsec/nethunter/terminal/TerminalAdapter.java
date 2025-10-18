package com.offsec.nethunter.terminal;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.offsec.nethunter.R;

import java.util.ArrayList;
import java.util.List;

public class TerminalAdapter extends RecyclerView.Adapter<TerminalAdapter.LineVH> {
    private final List<CharSequence> lines = new ArrayList<>();
    private final int maxLines;
    private final long nextId = 0; // retained if stable IDs expanded later
    private float textSizeSp = 12f; // default, can be modified at runtime
    private String highlightTerm = null;
    private int baseTextColor = Color.WHITE; // default; can be themed
    private float lineSpacingExtraPx = 0f;
    private float lineSpacingMult = 1.0f;

    public TerminalAdapter(int maxLines) {
        this.maxLines = maxLines;
        setHasStableIds(true);
    }

    // Optional constructor for specifying initial text size
    public TerminalAdapter(int maxLines, float initialTextSizeSp) {
        this(maxLines);
        this.textSizeSp = initialTextSizeSp;
    }

    public static class LineVH extends RecyclerView.ViewHolder {
        final TextView tv;
        public LineVH(@NonNull View itemView) { super(itemView); tv = itemView.findViewById(R.id.line_text); }
    }

    @NonNull
    @Override
    public LineVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_terminal_line, parent, false);
        return new LineVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LineVH holder, int position) {
        CharSequence line = lines.get(position);
        if (highlightTerm != null && !highlightTerm.isEmpty()) {
            SpannableStringBuilder spannable = new SpannableStringBuilder(line);
            String lineStr = line.toString().toLowerCase();
            String termLower = highlightTerm.toLowerCase();
            int index = lineStr.indexOf(termLower);
            while (index >= 0) {
                spannable.setSpan(new BackgroundColorSpan(Color.YELLOW), index, index + highlightTerm.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = lineStr.indexOf(termLower, index + highlightTerm.length());
            }
            holder.tv.setText(spannable);
        } else {
            holder.tv.setText(line);
        }
        // Apply current dynamic text size and base color
        holder.tv.setTextSize(textSizeSp);
        holder.tv.setTextColor(baseTextColor);
        // Apply line spacing settings
        holder.tv.setLineSpacing(lineSpacingExtraPx, lineSpacingMult);
    }

    @Override
    public int getItemCount() { return lines.size(); }

    @Override
    public long getItemId(int position) { return position; }

    public void addLine(CharSequence line, RecyclerView recycler) {
        int removed = 0;
        if (lines.size() >= maxLines) {
            // trim oldest to keep size < maxLines (remove enough so after add it's <= max)
            int targetRemove = (lines.size() + 1) - maxLines;
            if (targetRemove > 0) {
                lines.subList(0, targetRemove).clear();
                removed = targetRemove;
                notifyItemRangeRemoved(0, removed);
            }
        }
        lines.add(line);
        notifyItemInserted(lines.size() - 1);
        recycler.scrollToPosition(lines.size() - 1);
    }

    public void clearAll() {
        int sz = lines.size();
        if (sz == 0) return;
        lines.clear();
        notifyItemRangeRemoved(0, sz);
    }

    public List<CharSequence> getLines() {
        return new ArrayList<>(lines);
    }

    public void setTextSizeSp(float sizeSp) {
        if (sizeSp == this.textSizeSp) return;
        this.textSizeSp = sizeSp;
        notifyDataSetChanged();
    }

    public float getTextSizeSp() { return textSizeSp; }

    public void setHighlightTerm(String term) {
        this.highlightTerm = term;
        notifyDataSetChanged();
    }

    public void setBaseTextColor(int color) {
        if (this.baseTextColor == color) return;
        this.baseTextColor = color;
        notifyDataSetChanged();
    }

    public void setLineSpacing(float extraPx, float multiplier) {
        if (this.lineSpacingExtraPx == extraPx && this.lineSpacingMult == multiplier) return;
        this.lineSpacingExtraPx = extraPx;
        this.lineSpacingMult = multiplier;
        notifyDataSetChanged();
    }
}
