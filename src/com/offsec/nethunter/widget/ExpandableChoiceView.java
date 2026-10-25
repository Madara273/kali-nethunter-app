package com.offsec.nethunter.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.activity.ViewTreeOnBackPressedDispatcherOwner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.offsec.nethunter.R;

import java.lang.ref.WeakReference;

/** Inline choice card: a rotating arrow, downward expansion and a marked selection. */
public class ExpandableChoiceView extends LinearLayout {
    private static WeakReference<ExpandableChoiceView> openChoice = new WeakReference<>(null);
    private final AppCompatSpinner header;
    private final ImageView arrow;
    private final NestedScrollView options;
    private final LinearLayout rows;
    private final int accent;
    private final int textColor;
    private final int surface;
    private final int border;
    private SpinnerAdapter adapter;
    private AdapterView.OnItemSelectedListener selectionListener;
    private ValueAnimator heightAnimator;
    private OnBackPressedCallback backCallback;
    private boolean expanded;
    private boolean observing;
    private int restoredPosition = -1;
    private final DataSetObserver observer = new DataSetObserver() {
        @Override public void onChanged() {
            restoreSelectionIfReady();
            if (expanded) {
                if (getCount() == 0) setExpanded(false);
                else { rebuildRows(); animateOptions(true); }
            }
        }
        @Override public void onInvalidated() { setExpanded(false); }
    };

    public ExpandableChoiceView(Context context) { this(context, null); }

    public ExpandableChoiceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = ContextCompat.getColor(context, R.color.home_nh_accent);
        textColor = ContextCompat.getColor(context, R.color.home_nh_text);
        surface = ContextCompat.getColor(context, R.color.home_nh_surface);
        border = ContextCompat.getColor(context, R.color.home_nh_border);
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, 0);
        setClipToOutline(true);
        updateOutline();

        FrameLayout title = new FrameLayout(context);
        header = new AppCompatSpinner(context, null, androidx.appcompat.R.attr.spinnerStyle, Spinner.MODE_DIALOG) {
            @Override public boolean performClick() {
                if (!ExpandableChoiceView.this.isEnabled() || getCount() == 0) return false;
                setExpanded(!expanded);
                return true;
            }
        };
        header.setBackground(null);
        header.setPadding(dp(14), dp(4), dp(42), dp(4));
        header.setMinimumWidth(0);
        header.setMinimumHeight(dp(52));
        title.addView(header, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_home_dropdown);
        arrow.setColorFilter(ContextCompat.getColor(context, R.color.home_nh_muted));
        arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.END | Gravity.CENTER_VERTICAL);
        arrowParams.setMarginEnd(dp(12));
        title.addView(arrow, arrowParams);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        options = new NestedScrollView(context);
        options.setFillViewport(false);
        options.setVerticalScrollBarEnabled(false);
        options.setOverScrollMode(OVER_SCROLL_NEVER);
        options.setVisibility(GONE);
        options.setBackgroundColor(ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 15), surface));
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        rows.setPadding(dp(8), dp(8), dp(8), dp(8));
        options.addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(options, new LayoutParams(LayoutParams.MATCH_PARENT, 0));
        header.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                if (selectedView instanceof TextView) {
                    ((TextView) selectedView).setTextColor(textColor);
                    ((TextView) selectedView).setTextSize(14f);
                }
                if (expanded) rebuildRows();
                if (selectionListener != null) selectionListener.onItemSelected(parent, selectedView, position, id);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                if (selectionListener != null) selectionListener.onNothingSelected(parent);
            }
        });
        TypedArray values = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.entries});
        CharSequence[] entries = values.getTextArray(0);
        values.recycle();
        if (entries != null) {
            setAdapter(new ArrayAdapter<>(context, R.layout.home_choice_selected_item, entries));
        }
    }

    public void setAdapter(SpinnerAdapter newAdapter) {
        stopObserving();
        adapter = newAdapter;
        header.setAdapter(newAdapter);
        observe();
        restoreSelectionIfReady();
        if (expanded) { rebuildRows(); animateOptions(true); }
    }

    public SpinnerAdapter getAdapter() { return adapter; }
    public Object getSelectedItem() { return header.getSelectedItem(); }
    public int getSelectedItemPosition() { return header.getSelectedItemPosition(); }
    public Object getItemAtPosition(int position) { return header.getItemAtPosition(position); }
    public int getCount() { return adapter == null ? 0 : adapter.getCount(); }
    public void setSelection(int position) { header.setSelection(position); }
    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) { selectionListener = listener; }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (header == null) return;
        header.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.5f);
        if (!enabled) setExpanded(false);
    }

    private void setExpanded(boolean show) {
        if (expanded == show || (show && getCount() == 0)) return;
        if (show) {
            ExpandableChoiceView previous = openChoice.get();
            if (previous != null && previous != this) previous.setExpanded(false);
            openChoice = new WeakReference<>(this);
            rebuildRows();
            options.scrollTo(0, 0);
        } else if (openChoice.get() == this) {
            openChoice.clear();
        }
        expanded = show;
        if (backCallback != null) backCallback.setEnabled(show);
        ViewCompat.setStateDescription(header, show ? "Expanded" : "Collapsed");
        arrow.animate().rotation(show ? 180f : 0f).setDuration(300L).start();
        updateOutline();
        animateOptions(show);
    }

    private void rebuildRows() {
        rows.removeAllViews();
        for (int i = 0; i < getCount(); i++) {
            final int position = i;
            boolean selected = i == getSelectedItemPosition();
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable background = shape(selected ? ColorUtils.setAlphaComponent(accent, 24) : Color.TRANSPARENT, 10);
            TypedArray theme = getContext().obtainStyledAttributes(new int[]{android.R.attr.colorControlHighlight});
            int ripple = theme.getColor(0, ColorUtils.setAlphaComponent(accent, 30));
            theme.recycle();
            row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ripple), background, shape(Color.WHITE, 10)));
            TextView label = new TextView(getContext());
            Object item = adapter.getItem(i);
            label.setText(item == null ? "" : item.toString());
            label.setTextColor(textColor);
            label.setTextSize(14f);
            row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            if (selected) {
                ImageView check = new ImageView(getContext());
                check.setImageResource(R.drawable.ic_home_choice_check);
                check.setColorFilter(accent);
                check.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                LayoutParams checkParams = new LayoutParams(dp(20), dp(20));
                checkParams.setMarginStart(dp(10));
                row.addView(check, checkParams);
            }
            row.setContentDescription(label.getText() + (selected ? ", selected" : ""));
            row.setFocusable(true);
            row.setOnClickListener(v -> { setSelection(position); setExpanded(false); });
            rows.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private void animateOptions(boolean show) {
        if (heightAnimator != null) heightAnimator.cancel();
        int start = options.getVisibility() == VISIBLE ? options.getHeight() : 0;
        int width = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        rows.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int target = show ? Math.min(rows.getMeasuredHeight(), dp(280)) : 0;
        options.setVisibility(VISIBLE);
        if (start == 0) options.setAlpha(0f);
        options.animate().alpha(show ? 1f : 0f).setDuration(show ? 230L : 170L).start();
        heightAnimator = ValueAnimator.ofInt(start, target);
        heightAnimator.setDuration(show ? 320L : 250L);
        heightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        heightAnimator.addUpdateListener(animation -> {
            LayoutParams params = (LayoutParams) options.getLayoutParams();
            params.height = (int) animation.getAnimatedValue();
            options.setLayoutParams(params);
        });
        heightAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled && !expanded) options.setVisibility(GONE);
            }
        });
        heightAnimator.start();
    }

    private void updateOutline() {
        GradientDrawable drawable = shape(surface, 22);
        drawable.setStroke(dp(1), expanded ? ColorUtils.setAlphaComponent(accent, 100) : border);
        setBackground(drawable);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void observe() {
        if (adapter != null && !observing) { adapter.registerDataSetObserver(observer); observing = true; }
    }
    private void stopObserving() {
        if (adapter != null && observing) { adapter.unregisterDataSetObserver(observer); observing = false; }
    }
    private void restoreSelectionIfReady() {
        if (restoredPosition >= 0 && getCount() > 0) {
            header.setSelection(Math.min(restoredPosition, getCount() - 1));
            restoredPosition = -1;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observe();
        OnBackPressedDispatcherOwner owner = ViewTreeOnBackPressedDispatcherOwner.get(this);
        if (owner != null) {
            backCallback = new OnBackPressedCallback(expanded) {
                @Override public void handleOnBackPressed() { setExpanded(false); }
            };
            owner.getOnBackPressedDispatcher().addCallback(backCallback);
        }
    }

    @Override protected void onDetachedFromWindow() {
        stopObserving();
        if (backCallback != null) { backCallback.remove(); backCallback = null; }
        closeImmediately();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (options != null && visibility != VISIBLE) closeImmediately();
    }

    private void closeImmediately() {
        if (heightAnimator != null) heightAnimator.cancel();
        arrow.animate().cancel();
        options.animate().cancel();
        expanded = false;
        if (backCallback != null) backCallback.setEnabled(false);
        options.setVisibility(GONE);
        options.getLayoutParams().height = 0;
        arrow.setRotation(0f);
        if (openChoice.get() == this) openChoice.clear();
        ViewCompat.setStateDescription(header, "Collapsed");
        updateOutline();
    }

    @Nullable @Override protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.position = getSelectedItemPosition();
        return state;
    }

    @Override protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) { super.onRestoreInstanceState(state); return; }
        SavedState saved = (SavedState) state;
        super.onRestoreInstanceState(saved.getSuperState());
        restoredPosition = saved.position;
        restoreSelectionIfReady();
    }

    public static final class SavedState extends BaseSavedState {
        int position;
        SavedState(Parcelable state) { super(state); }
        SavedState(Parcel source) { super(source); position = source.readInt(); }
        @Override public void writeToParcel(@NonNull Parcel destination, int flags) {
            super.writeToParcel(destination, flags);
            destination.writeInt(position);
        }
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override public SavedState createFromParcel(Parcel source) { return new SavedState(source); }
            @Override public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
