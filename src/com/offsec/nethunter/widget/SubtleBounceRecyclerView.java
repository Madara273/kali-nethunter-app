package com.offsec.nethunter.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.animation.OvershootInterpolator;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** A small content-only edge bounce, shared by the Home and Kali Services lists. */
public class SubtleBounceRecyclerView extends RecyclerView {
    private final float maxBouncePx;
    private float bounceOffset;
    private ValueAnimator releaseAnimator;

    public SubtleBounceRecyclerView(Context context) {
        this(context, null);
    }

    public SubtleBounceRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SubtleBounceRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        maxBouncePx = 12f * getResources().getDisplayMetrics().density;
        setHasFixedSize(true);
        setItemAnimator(null);
        setItemViewCacheSize(6);
        setEdgeEffectFactory(new EdgeEffectFactory() {
            @NonNull
            @Override
            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
                return new BounceEdgeEffect(view.getContext(), direction);
            }
        });
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        int save = canvas.save();
        canvas.translate(0f, bounceOffset);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    private void setBounceOffset(float offset) {
        bounceOffset = Math.max(-maxBouncePx, Math.min(maxBouncePx, offset));
        postInvalidateOnAnimation();
    }

    private void cancelRelease() {
        if (releaseAnimator != null) {
            releaseAnimator.cancel();
            releaseAnimator = null;
        }
    }

    private void release() {
        cancelRelease();
        if (Math.abs(bounceOffset) < 0.5f) {
            setBounceOffset(0f);
            return;
        }
        releaseAnimator = ValueAnimator.ofFloat(bounceOffset, 0f);
        releaseAnimator.setDuration(320L);
        releaseAnimator.setInterpolator(new OvershootInterpolator(0.45f));
        releaseAnimator.addUpdateListener(animation -> setBounceOffset((float) animation.getAnimatedValue()));
        releaseAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelRelease();
        bounceOffset = 0f;
        super.onDetachedFromWindow();
    }

    private final class BounceEdgeEffect extends EdgeEffect {
        private final int sign;
        private float distance;

        BounceEdgeEffect(Context context, int direction) {
            super(context);
            sign = direction == EdgeEffectFactory.DIRECTION_TOP ? 1
                    : direction == EdgeEffectFactory.DIRECTION_BOTTOM ? -1 : 0;
        }

        @Override
        public void onPull(float deltaDistance) {
            onPull(deltaDistance, 0.5f);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            onPullDistance(deltaDistance, displacement);
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            if (sign == 0) return 0f;
            cancelRelease();
            float previous = distance;
            distance = Math.max(0f, Math.min(1f, distance + deltaDistance));
            setBounceOffset(sign * Math.min(maxBouncePx, distance * getHeight() * 0.35f));
            return distance - previous;
        }

        @Override
        public void onRelease() {
            distance = 0f;
            if (sign * bounceOffset > 0f) release();
        }

        @Override
        public void onAbsorb(int velocity) {
            if (sign == 0) return;
            cancelRelease();
            distance = 0f;
            setBounceOffset(sign * Math.min(maxBouncePx,
                    Math.max(maxBouncePx * 0.3f, Math.abs(velocity) * 0.004f)));
            release();
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        @Override
        public boolean isFinished() {
            return distance == 0f && (releaseAnimator == null || !releaseAnimator.isRunning());
        }

        @Override
        public void finish() {
            distance = 0f;
            cancelRelease();
            setBounceOffset(0f);
        }

        @Override
        public float getDistance() {
            return distance;
        }
    }
}
