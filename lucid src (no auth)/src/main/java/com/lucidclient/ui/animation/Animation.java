package com.lucidclient.ui.animation;

import com.lucidclient.ui.util.MathUtils;

/**
 * A time-driven animation between two values.
 * Thread-safe enough for single-threaded render loop usage.
 */
public class Animation {
    private long durationMs;
    private Easing easing;
    private float start;
    private float end;
    private float value;
    private long elapsedMs;
    private boolean playing;
    private boolean finished;
    private boolean reverseOnFinish;
    private float delayMs;

    public Animation(float start, float end, long durationMs, Easing easing) {
        this.start = start;
        this.end = end;
        this.value = start;
        this.durationMs = Math.max(1L, durationMs);
        this.easing = easing;
        this.elapsedMs = 0;
        this.playing = false;
        this.finished = false;
    }

    /** Start playing from the beginning. */
    public void play() {
        this.elapsedMs = 0;
        this.playing = true;
        this.finished = false;
        this.value = start;
    }

    /** Advance the animation by dt milliseconds. */
    public void update(long dtMs) {
        if (!playing) return;
        if (delayMs > 0) {
            delayMs -= dtMs;
            if (delayMs > 0) return;
            dtMs = (long) (-delayMs);
            delayMs = 0;
        }
        elapsedMs += dtMs;
        float progress = MathUtils.clamp((float) elapsedMs / durationMs, 0f, 1f);
        float eased = easing.apply(progress);
        value = MathUtils.lerp(start, end, eased);
        if (progress >= 1f) {
            value = end;
            playing = false;
            finished = true;
        }
    }

    /** Reverse direction and continue (used for close animations). */
    public void reverse() {
        float tmp = start;
        start = end;
        end = tmp;
        elapsedMs = (long) (durationMs * (1f - (float) elapsedMs / durationMs));
        playing = true;
        finished = false;
    }

    /** Snap directly to end without animating. */
    public void finish() {
        this.value = end;
        this.playing = false;
        this.finished = true;
        this.elapsedMs = durationMs;
    }

    /** Reset to start value, not playing. */
    public void reset() {
        this.value = start;
        this.elapsedMs = 0;
        this.playing = false;
        this.finished = false;
    }

    public void setDelay(float delayMs) { this.delayMs = Math.max(0, delayMs); }
    public float getDelay() { return delayMs; }

    public float get() { return value; }
    public float getProgress() { return MathUtils.clamp((float) elapsedMs / durationMs, 0f, 1f); }
    public boolean isPlaying() { return playing; }
    public boolean isFinished() { return finished; }

    public void setRange(float start, float end) {
        this.start = start;
        this.end = end;
    }

    public void setDuration(long durationMs) { this.durationMs = Math.max(1L, durationMs); }
    public void setEasing(Easing easing) { this.easing = easing; }
}
