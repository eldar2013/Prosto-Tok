package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Motion blur: whatever scrolls is blurred along the way it moves, as much as it moves.
 * The feed going to the next video is smeared up and down, a row of chats sideways.
 *
 * Every list, pager and scroll view of a screen is found when the screen is laid out
 * (not on every frame), and before each frame each says how far it has scrolled since
 * the last one. That distance is the blur, in its own direction, and it fades out as the
 * scrolling stops. Android 12 and later (RenderEffect).
 *
 * A video drawn in a SurfaceView is a layer of its own that no effect reaches, so with
 * the blur on, TikTok's player is asked for a TextureView instead ({@link #textureVideo}).
 */
public final class MotionBlur {

    private MotionBlur() {}

    static final String KEY = "motion_blur", STRENGTH = "motion_blur_strength";
    private static final int TAG = 0x7e0d0442;

    private static Boolean on;
    private static int strength = -1;

    static boolean on() {
        if (on == null) {
            Context c = Margy.context();
            if (c == null) return false;
            on = c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
        }
        return on && android.os.Build.VERSION.SDK_INT >= 31;
    }

    static void set(boolean value) {
        on = value;
        Context c = Margy.context();
        if (c != null) c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply();
    }

    /** 0 to 100: how much blur a given speed makes. */
    static int strength() {
        if (strength < 0) {
            Context c = Margy.context();
            strength = c == null ? 50 : c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getInt(STRENGTH, 50);
        }
        return strength;
    }

    static void setStrength(int value) {
        strength = Math.max(0, Math.min(100, value));
        Context c = Margy.context();
        if (c != null) c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putInt(STRENGTH, strength).apply();
    }

    /**
     * Asked by TikTok's player settings (PlayerSettingService.isUseSurfaceView) when a player
     * is made: with the blur on, the answer is no, and the video comes in a TextureView.
     */
    public static boolean textureVideo() {
        try {
            return on();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // --------------------------------------------------------------- the screens

    static void attach(Activity activity) {
        if (!on()) return;
        try {
            View decor = activity.getWindow().getDecorView();
            if (decor.getTag(TAG) instanceof Watcher) return;
            Watcher watcher = new Watcher(decor);
            decor.setTag(TAG, watcher);
            ViewTreeObserver tree = decor.getViewTreeObserver();
            tree.addOnGlobalLayoutListener(watcher);
            tree.addOnPreDrawListener(watcher);
        } catch (Throwable error) {
            Diary.note("motion blur: " + error);
        }
    }

    private static Method vertical, horizontal;

    private static int offset(View view, boolean down) {
        try {
            if (vertical == null) {
                vertical = View.class.getDeclaredMethod("computeVerticalScrollOffset");
                vertical.setAccessible(true);
                horizontal = View.class.getDeclaredMethod("computeHorizontalScrollOffset");
                horizontal.setAccessible(true);
            }
            return (Integer) (down ? vertical : horizontal).invoke(view);
        } catch (Throwable error) {
            return down ? view.getScrollY() : view.getScrollX();
        }
    }

    /** One thing that scrolls, and where it was on the last frame. */
    private static final class Mover {
        int x, y;
        float blurX, blurY, shownX = -1, shownY = -1;
        boolean known;
    }

    private static final class Watcher implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
        final java.lang.ref.WeakReference<View> root;
        final WeakHashMap<View, Mover> movers = new WeakHashMap<>();
        long found;

        Watcher(View root) {
            this.root = new java.lang.ref.WeakReference<>(root);
        }

        @Override
        public void onGlobalLayout() {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - found < 700L) return;
            found = now;
            View top = root.get();
            if (top != null) find(top, new int[] {20000});
        }

        private void find(View view, int[] budget) {
            if (budget[0]-- <= 0 || view.getVisibility() != View.VISIBLE) return;
            if (scrolls(view) && !movers.containsKey(view)) movers.put(view, new Mover());
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) find(group.getChildAt(i), budget);
            }
        }

        private boolean scrolls(View view) {
            if (view instanceof android.widget.ScrollView || view instanceof android.widget.HorizontalScrollView
                    || view instanceof android.widget.AbsListView) return true;
            for (Class<?> c = view.getClass(); c != null && c != View.class; c = c.getSuperclass()) {
                String name = c.getName();
                if (name.equals("androidx.recyclerview.widget.RecyclerView") || name.equals("androidx.viewpager.widget.ViewPager")
                        || name.endsWith(".VerticalViewPager") || name.equals("androidx.core.widget.NestedScrollView")) return true;
            }
            return false;
        }

        @Override
        public boolean onPreDraw() {
            if (!on()) {
                for (View view : new ArrayList<>(movers.keySet())) if (view != null) view.setRenderEffect(null);
                movers.clear();
                return true;
            }
            float density = 1f;
            View top = root.get();
            if (top != null) density = top.getResources().getDisplayMetrics().density;
            // a full speed swipe moves some 40 dp a frame; at the middle strength that is 12 dp of blur
            float gain = 0.15f + 0.45f * strength() / 100f, most = 28f * density;
            for (java.util.Map.Entry<View, Mover> entry : new ArrayList<>(movers.entrySet())) {
                View view = entry.getKey();
                Mover m = entry.getValue();
                if (view == null) continue;
                if (!view.isAttachedToWindow() || !view.isShown()) { m.known = false; clear(view, m); continue; }
                int x = offset(view, false), y = offset(view, true);
                if (!m.known) { m.x = x; m.y = y; m.known = true; continue; }
                float dx = Math.abs(x - m.x), dy = Math.abs(y - m.y);
                m.x = x; m.y = y;
                // up at once with the speed, down softly as it stops
                m.blurX = Math.max(Math.min(most, dx * gain), m.blurX * 0.72f);
                m.blurY = Math.max(Math.min(most, dy * gain), m.blurY * 0.72f);
                if (m.blurX < 0.6f) m.blurX = 0;
                if (m.blurY < 0.6f) m.blurY = 0;
                if (Math.abs(m.blurX - m.shownX) < 0.4f && Math.abs(m.blurY - m.shownY) < 0.4f) continue;
                m.shownX = m.blurX;
                m.shownY = m.blurY;
                if (m.blurX == 0 && m.blurY == 0) view.setRenderEffect(null);
                else view.setRenderEffect(RenderEffect.createBlurEffect(Math.max(0.01f, m.blurX), Math.max(0.01f, m.blurY), Shader.TileMode.CLAMP));
                // fading out needs frames even when nothing moves any more
                if (m.blurX > 0 || m.blurY > 0) view.postInvalidateOnAnimation();
            }
            return true;
        }

        private void clear(View view, Mover m) {
            if (m.shownX != 0 || m.shownY != 0) {
                view.setRenderEffect(null);
                m.shownX = m.shownY = 0;
                m.blurX = m.blurY = 0;
            }
        }
    }
}
