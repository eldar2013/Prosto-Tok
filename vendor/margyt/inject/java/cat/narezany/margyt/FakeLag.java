package cat.narezany.margyt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Frozen frames over this app only. The UI thread stays free to handle Fix. */
final class FakeLag {
    private FakeLag() {}

    static final String KEY = "fun_lag";
    // Copy callbacks must survive a session's timer cancellation to release its bitmap.
    private static final Handler COPIES = new Handler(Looper.getMainLooper());
    private static Session active;

    static boolean isOn(Context c) {
        return c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    static void set(Context c, boolean on) {
        c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
        stop();
        Activity here = Watch.here();
        if (on && here != null) resumed(here);
    }

    static void resumed(Activity a) {
        if (active != null && active.activity == a) return;
        stop();
        // Settings remain usable, including the switch for this joke.
        if (a == null || a.getClass().getName().startsWith("cat.narezany.margyt.") || !isOn(a)) return;
        try {
            View decor = a.getWindow().getDecorView();
            if (!(decor instanceof FrameLayout)) return;
            active = new Session(a, (FrameLayout) decor);
            active.start();
        } catch (Throwable error) {
            stop();
            Diary.note("fake lag: " + error);
        }
    }

    static void paused(Activity a) {
        if (active != null && active.activity == a) stop();
    }

    private static void stop() {
        Session old = active;
        active = null;
        if (old != null) old.stop();
    }

    private static final class Session {
        final Activity activity;
        final FrameLayout screen;
        final Handler timers = new Handler(Looper.getMainLooper());
        final ImageView cover;
        final Runnable freeze = this::freeze;
        final Runnable thaw = this::thaw;
        final Runnable popup = this::popup;
        Bitmap shot;
        AlertDialog dialog;
        boolean stopped, copying, captured;

        Session(Activity a, FrameLayout s) {
            activity = a;
            screen = s;
            cover = new ImageView(a);
            cover.setScaleType(ImageView.ScaleType.FIT_XY);
            cover.setClickable(true);
            cover.setElevation(10000f);
            cover.setVisibility(View.GONE);
        }

        boolean current() {
            return !stopped && active == this && Watch.here() == activity
                    && !activity.isFinishing() && !activity.isDestroyed() && isOn(activity);
        }

        void start() {
            screen.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            timers.postDelayed(freeze, 600);
            timers.postDelayed(popup, between(4500, 7000));
        }

        void freeze() {
            if (!current()) { FakeLag.paused(activity); return; }
            if (dialog != null || copying) return;
            if (!screen.isAttachedToWindow() || !activity.hasWindowFocus()) {
                timers.postDelayed(freeze, 300);
                return;
            }
            int w = screen.getWidth(), h = screen.getHeight();
            if (w <= 0 || h <= 0) { timers.postDelayed(freeze, 300); return; }
            try {
                // One reusable image, at most 1280 pixels along the longer side.
                float scale = Math.min(1f, 1280f / Math.max(w, h));
                int sw = Math.max(1, Math.round(w * scale)), sh = Math.max(1, Math.round(h * scale));
                if (shot == null || shot.getWidth() != sw || shot.getHeight() != sh) {
                    release();
                    shot = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
                    captured = false;
                }
                copying = true;
                if (Build.VERSION.SDK_INT >= 26) {
                    PixelCopy.request(activity.getWindow(), shot, result -> copied(result == PixelCopy.SUCCESS), COPIES);
                } else copied(false);
            } catch (Throwable error) {
                copying = false;
                copied(false);
            }
        }

        void copied(boolean ok) {
            copying = false;
            if (!current()) { release(); return; }
            if (!ok && shot != null) {
                try {
                    Canvas canvas = new Canvas(shot);
                    canvas.scale(shot.getWidth() / (float) screen.getWidth(), shot.getHeight() / (float) screen.getHeight());
                    screen.draw(canvas);
                    ok = true;
                } catch (Throwable ignored) {}
            }
            if (ok) {
                captured = true;
                cover.setImageBitmap(shot);
                cover.setVisibility(View.VISIBLE);
            }
            // The dialog may have appeared while PixelCopy was still running.
            if (dialog == null) timers.postDelayed(thaw, between(350, 1200));
        }

        void thaw() {
            cover.setVisibility(View.GONE);
            if (current() && dialog == null) timers.postDelayed(freeze, between(90, 260));
        }

        void popup() {
            if (!current()) { FakeLag.paused(activity); return; }
            if (!activity.hasWindowFocus() || !screen.isAttachedToWindow()) {
                timers.postDelayed(popup, 300);
                return;
            }
            timers.removeCallbacks(freeze);
            timers.removeCallbacks(thaw);
            if (captured) cover.setVisibility(View.VISIBLE);
            try {
                int theme = NavBar.bright(Md3.behind(screen)) > .5f
                        ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                        : android.R.style.Theme_DeviceDefault_Dialog_Alert;
                dialog = new AlertDialog.Builder(activity, theme)
                        .setTitle(Text.LAG_TITLE)
                        .setMessage(Text.LAG_QUESTION)
                        .setNegativeButton(Text.LAG_CLOSE, (d, which) -> close())
                        .setNeutralButton(Text.LAG_WAIT, (d, which) -> waitAgain())
                        .setPositiveButton(Text.LAG_FIX, (d, which) -> FakeLag.set(activity, false))
                        .setCancelable(false).create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            } catch (Throwable error) {
                FakeLag.set(activity, false);
                Diary.note("fake lag dialog: " + error);
            }
        }

        void waitAgain() {
            dialog = null;
            cover.setVisibility(View.GONE);
            if (!current()) return;
            // An outstanding copy will resume the cycle when it returns.
            if (!copying) timers.postDelayed(freeze, 400);
            timers.postDelayed(popup, between(5500, 9000));
        }

        void close() {
            // Persist before killing the process so reopening doesn't restart the joke.
            activity.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, false).commit();
            FakeLag.stop();
            try { activity.finishAndRemoveTask(); } catch (Throwable ignored) {}
            try { activity.finishAffinity(); } catch (Throwable ignored) {}
            android.os.Process.killProcess(android.os.Process.myPid());
        }

        void stop() {
            stopped = true;
            timers.removeCallbacksAndMessages(null);
            if (dialog != null) {
                try { dialog.dismiss(); } catch (Throwable ignored) {}
                dialog = null;
            }
            cover.setVisibility(View.GONE);
            cover.setImageDrawable(null);
            if (cover.getParent() instanceof ViewGroup) ((ViewGroup) cover.getParent()).removeView(cover);
            if (!copying) release();
        }

        void release() {
            cover.setImageDrawable(null);
            if (shot != null) { shot.recycle(); shot = null; }
            captured = false;
        }

        static long between(int first, int last) {
            return first + (long) (Math.random() * (last - first));
        }
    }
}
