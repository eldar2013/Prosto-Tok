package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A message somebody took back stays in the chat, faded.
 *
 * TikTok does not delete anything when a message is recalled: the text stays in its database, and
 * the only thing that happens is that the message gets marked, after which every screen shows
 * "unsent a message" instead. So the mark is simply not put on. One call does that, in the updater
 * that handles the server's recall command, and it comes here first. The message then stays what
 * it was, everywhere, and the mod remembers its id to draw it faded.
 *
 * Only other people's messages: what you take back yourself goes the normal way, so that "Unsend"
 * and editing a sent message keep working.
 */
public final class Recalled {

    private Recalled() {}

    static final String KEY = "keep_recalled";
    private static final String STORE = "margyt_recalled";
    private static final int MOST = 4000;
    private static final float FADED = 0.45f;
    private static final int FADED_ROW = 0x7e0c0381, WATCHED_ROW = 0x7e0c0383;

    private static volatile Boolean on;
    private static Map<String, Long> kept;
    private static final Map<View, String> rows = new WeakHashMap<>();
    private static Method recall;

    static boolean enabled() {
        // 2.2.2: always on, and no longer a switch: nobody wants it off
        return true;
    }

    static void setEnabled(boolean value) {
        on = value;
        Context context = Margy.context();
        if (context != null) context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply();
    }

    private static SharedPreferences store() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    private static synchronized Map<String, Long> kept() {
        if (kept != null) return kept;
        Map<String, Long> out = new HashMap<>();
        SharedPreferences store = store();
        if (store != null) for (Map.Entry<String, ?> one : store.getAll().entrySet())
            if (one.getValue() instanceof Long) out.put(one.getKey(), (Long) one.getValue());
        kept = out;
        return out;
    }

    /** Where the build sends the updater's call: the message, who took it back, and what to run after. */
    public static void arrived(Object domain, Object message, String recaller, Object after) throws Throwable {
        try {
            if (enabled() && holdBack(message, recaller)) return;
        } catch (Throwable error) {
            Diary.note("recalled: " + error);
        }
        original(domain, message, recaller, after);
    }

    private static boolean holdBack(Object message, String recaller) {
        // the id kept on disk: a recall can arrive before anything has asked who is signed in
        String own = Account.id();
        if (own == null || own.length() == 0 || own.equals(recaller)) return false;
        if (own.equals(String.valueOf(NativeRead.number(NativeRead.get(message, "getSender"))))) return false;
        String uuid = NativeRead.string(NativeRead.get(message, "getUuid"));
        // nothing was kept of it (a message this phone never had in full): let TikTok say so
        if (uuid.length() == 0 || NativeRead.string(NativeRead.get(message, "getContent")).length() == 0) return false;
        remember(uuid);
        return true;
    }

    private static void remember(String uuid) {
        long now = System.currentTimeMillis();
        Map<String, Long> known = kept();
        synchronized (Recalled.class) {
            known.put(uuid, now);
            SharedPreferences store = store();
            if (store != null) {
                SharedPreferences.Editor edit = store.edit().putLong(uuid, now);
                if (known.size() > MOST) {
                    List<Map.Entry<String, Long>> old = new ArrayList<>(known.entrySet());
                    java.util.Collections.sort(old, (a, b) -> Long.compare(a.getValue(), b.getValue()));
                    for (int i = 0; i < old.size() - MOST; i++) { known.remove(old.get(i).getKey()); edit.remove(old.get(i).getKey()); }
                }
                edit.apply();
            }
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            List<View> showing = new ArrayList<>();
            synchronized (rows) {
                for (Map.Entry<View, String> row : rows.entrySet()) if (uuid.equals(row.getValue()) && row.getKey() != null) showing.add(row.getKey());
            }
            for (View row : showing) fade(row);
        });
    }

    /**
     * Faded for as long as the row shows this message. Setting the alpha once isn't enough: the list
     * animates its rows whenever anything in it changes and ends each animation with alpha 1, and
     * nothing tells this that it did. So a faded row looks at itself before every frame it's drawn.
     */
    private static void fade(final View row) {
        row.setTag(FADED_ROW, Boolean.TRUE);
        row.setAlpha(FADED);
        if (row.getTag(WATCHED_ROW) != null) return;
        row.setTag(WATCHED_ROW, Boolean.TRUE);
        row.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (row.getTag(FADED_ROW) != null && row.getAlpha() != FADED) row.setAlpha(FADED);
            return true;
        });
    }

    private static void original(Object domain, Object message, String recaller, Object after) throws Throwable {
        Method known = recall;
        if (known == null) {
            for (Method one : domain.getClass().getMethods()) {
                if (one.getName().equals("LLJJIJI") && one.getParameterTypes().length == 3) { known = one; break; }
            }
            if (known == null) throw new NoSuchMethodException("recall on " + domain.getClass());
            known.setAccessible(true);
            recall = known;
        }
        try {
            known.invoke(domain, message, recaller, after);
        } catch (java.lang.reflect.InvocationTargetException error) {
            throw error.getCause() == null ? error : error.getCause();
        }
    }

    /** A row of the chat was given its message: faded if it's one that was taken back. */
    static void bound(View row, Object message) {
        try {
            String uuid = NativeRead.string(NativeRead.get(message, "getUuid"));
            synchronized (rows) { rows.put(row, uuid); }
            if (kept().containsKey(uuid)) {
                fade(row);
            } else if (row.getTag(FADED_ROW) != null) {
                // the list reuses rows: this one has somebody else's message now
                row.setTag(FADED_ROW, null);
                row.setAlpha(1f);
            }
        } catch (Throwable error) {
            Diary.note("recalled row: " + error);
        }
    }

    static int count() { return kept().size(); }
}
