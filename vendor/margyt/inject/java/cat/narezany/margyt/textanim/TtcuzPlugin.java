package cat.narezany.margyt.textanim;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * What the plugin asked of the ttcuz mod, answered by MargyT: its own preferences,
 * the accent, a layer over the screen for the settings, and the rest of the small
 * things. The callbacks are called by cat.narezany.margyt.TextAnim.
 */
public abstract class TtcuzPlugin {

    private final Api api = new Api(this);

    public Api ttcuz() {
        return api;
    }

    public void onStart() {}

    public void onStop() {}

    public boolean onOpenSettings(Activity activity) {
        return false;
    }

    public void onActivityDestroyed(Activity activity) {}

    public void onPreferenceChanged(String scope, String key) {}

    public void onScreen(Activity activity, ViewGroup content) {}

    public void onActivityResumed(Activity activity) {}

    public void onView(View view) {}

    public static final class Api {
        private final TtcuzPlugin plugin;
        private SharedPreferences prefs;
        private final Handler main = new Handler(Looper.getMainLooper());
        // kept here: SharedPreferences holds its listeners weakly
        private SharedPreferences.OnSharedPreferenceChangeListener listener;

        Api(TtcuzPlugin plugin) {
            this.plugin = plugin;
        }

        public SharedPreferences prefs() {
            if (prefs == null) {
                Context context = cat.narezany.margyt.Margy.context();
                prefs = context.getSharedPreferences("margyt_text_animation", Context.MODE_PRIVATE);
                listener = (p, key) -> main.post(() -> {
                    try { plugin.onPreferenceChanged("plugin", key); } catch (Throwable ignored) {}
                });
                prefs.registerOnSharedPreferenceChangeListener(listener);
            }
            return prefs;
        }

        public int accent() {
            try {
                return cat.narezany.margyt.Accent.colour();
            } catch (Throwable ignored) {
                return 0;
            }
        }

        public int dp(float value) {
            Context context = cat.narezany.margyt.Margy.context();
            float density = context == null ? 2.75f : context.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        }

        public void log(String line) {
            try { cat.narezany.margyt.Diary.note("text animation: " + line); } catch (Throwable ignored) {}
        }

        public void later(Runnable action, long delay) {
            main.postDelayed(action, delay);
        }

        public <T> List<T> views(View root, Class<T> type) {
            List<T> out = new ArrayList<T>();
            collect(root, type, out);
            return out;
        }

        private static <T> void collect(View view, Class<T> type, List<T> out) {
            if (view == null) return;
            if (type.isInstance(view)) out.add(type.cast(view));
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), type, out);
            }
        }

        /** The settings screen goes over the whole window, above TikTok and the mod. */
        public View overlay(Activity host, View root, int gravity) {
            ViewGroup decor = (ViewGroup) host.getWindow().getDecorView();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, gravity);
            decor.addView(root, params);
            return root;
        }

        public void animateIn(View view) {
            view.setAlpha(0f);
            view.setTranslationY(dp(24));
            view.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }

        public void animateOut(View view, Runnable done) {
            view.animate().alpha(0f).translationY(dp(24)).setDuration(180)
                    .withEndAction(done).start();
        }

        public void removeOverlay(View view) {
            if (view != null && view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        }
    }
}
