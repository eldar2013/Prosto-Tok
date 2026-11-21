package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MargyCode's way in: its agent writes plugins and tries them on the running app.
 *
 * Plugins are the one thing here nobody can write without seeing the app, and TikTok tells nothing
 * about itself from outside. So once the person has said yes (DevLinkActivity), a small server runs
 * inside TikTok on 127.0.0.1: it installs a plugin, reads the diary, shows the views and classes,
 * and taps. Only with the key given at pairing, and only to the app signed with MargyCode's key.
 *
 * A plugin can do anything TikTok can, so an agent that can install them can too. That's said in so
 * many words when pairing, and it's undone by one row in the settings.
 */
public final class DevLink {

    private DevLink() {}

    static final String MARGYCODE = "cat.narezany.margycode";
    /** SHA-256 of the certificate MargyCode is signed with. */
    private static final String SIGNED_BY = "5821624A80C17D15CE684D2657BC42098A948336242E5FA9F85D973F761E2474";
    private static final String PREFS = "margyt_devlink";
    private static final int LOG_KEEP = 1500;

    private static ServerSocket server;
    private static final ArrayDeque<String> log = new ArrayDeque<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean linked(Context context) {
        return prefs(context).getString("token", "").length() > 0;
    }

    /** At every start of the app, when the two are paired. */
    static void start(Context context) {
        if (linked(context)) listen(context.getApplicationContext());
    }

    /** Is this really MargyCode asking, and not an app that took its name? */
    static boolean genuine(Context context, String caller) {
        if (!MARGYCODE.equals(caller)) return false;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(caller,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null || info.signingInfo.hasMultipleSigners()) return false;
            for (android.content.pm.Signature one : info.signingInfo.getSigningCertificateHistory()) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(one.toByteArray());
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) hex.append(String.format("%02X", b));
                if (SIGNED_BY.contentEquals(hex)) return true;
            }
        } catch (Throwable error) {
            Diary.note("devlink: " + error);
        }
        return false;
    }

    /** The person said yes: a new key, every time. */
    static synchronized Object[] pair(Context context) throws Exception {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder token = new StringBuilder();
        for (byte b : raw) token.append(String.format("%02x", b));
        stop();
        ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        int port = probe.getLocalPort();
        probe.close();
        prefs(context).edit().putString("token", token.toString()).putInt("port", port).apply();
        listen(context.getApplicationContext());
        Diary.note("MargyCode connected");
        return new Object[] {port, token.toString()};
    }

    static synchronized void unlink(Context context) {
        prefs(context).edit().clear().apply();
        stop();
        Diary.note("MargyCode disconnected");
    }

    private static synchronized void stop() {
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
        server = null;
    }

    /** Whatever the diary hears, kept longer here: the agent reads it after each try. */
    static void heard(String line) {
        if (server == null) return;
        synchronized (log) {
            log.addLast(line);
            while (log.size() > LOG_KEEP) log.removeFirst();
        }
    }

    private static synchronized void listen(final Context context) {
        if (server != null) return;
        final int port = prefs(context).getInt("port", 0);
        if (port == 0) return;
        try {
            server = new ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"));
        } catch (Throwable error) {
            Diary.note("devlink: can't listen: " + error);
            return;
        }
        final ServerSocket listening = server;
        Thread thread = new Thread(() -> {
            while (!listening.isClosed()) {
                try {
                    final Socket client = listening.accept();
                    Thread one = new Thread(() -> serve(context, client), "margyt-devlink-call");
                    one.setDaemon(true);
                    one.start();
                } catch (Throwable error) {
                    return;
                }
            }
        }, "margyt-devlink");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------- the wire

    private static void serve(Context context, Socket client) {
        try {
            BufferedInputStream in = new BufferedInputStream(client.getInputStream());
            ByteArrayOutputStream headBytes = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4 && headBytes.size() < 32768) {
                int b = in.read();
                if (b < 0) return;
                headBytes.write(b);
                matched = (matched % 2 == 0 ? b == 13 : b == 10) ? matched + 1 : 0;
            }
            String[] lines = headBytes.toString("UTF-8").split("\r\n");
            String auth = "";
            int length = 0;
            for (String line : lines) {
                String lower = line.toLowerCase();
                if (lower.startsWith("authorization:")) auth = line.substring(14).trim();
                if (lower.startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            }
            String token = prefs(context).getString("token", "");
            if (token.length() == 0 || !MessageDigest.isEqual(("Bearer " + token).getBytes(), auth.getBytes())) {
                reply(client, 401, new JSONObject().put("ok", false));
                return;
            }
            if (length > 40 * 1024 * 1024) { reply(client, 413, new JSONObject().put("ok", false)); return; }
            byte[] body = new byte[length];
            int at = 0;
            while (at < length) { int got = in.read(body, at, length - at); if (got < 0) break; at += got; }
            JSONObject request = new JSONObject(new String(body, "UTF-8"));
            JSONObject out;
            try {
                out = call(context, request.optString("tool"), request.optJSONObject("args") == null ? new JSONObject() : request.optJSONObject("args"));
            } catch (Throwable error) {
                out = new JSONObject().put("ok", false).put("text", String.valueOf(error.getCause() != null ? error.getCause() : error));
            }
            reply(client, 200, out);
        } catch (Throwable error) {
            Diary.note("devlink: " + error);
        } finally {
            try { client.close(); } catch (Throwable ignored) {}
        }
    }

    private static void reply(Socket client, int code, JSONObject body) throws Exception {
        byte[] bytes = body.toString().getBytes("UTF-8");
        java.io.OutputStream out = client.getOutputStream();
        out.write(("HTTP/1.1 " + code + " OK\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes());
        out.write(bytes);
        out.flush();
    }

    private static JSONObject said(String text) throws Exception { return new JSONObject().put("ok", true).put("text", text); }
    private static JSONObject failed(String text) throws Exception { return new JSONObject().put("ok", false).put("text", text); }

    private static <T> T onMain(final Callable<T> work) throws Exception {
        final Object[] out = new Object[2];
        final CountDownLatch done = new CountDownLatch(1);
        MAIN.post(() -> {
            try { out[0] = work.call(); } catch (Throwable error) { out[1] = error; }
            done.countDown();
        });
        if (!done.await(15, TimeUnit.SECONDS)) throw new Exception("the app's main thread didn't answer in 15 seconds");
        if (out[1] != null) throw new Exception(String.valueOf(out[1]), (Throwable) out[1]);
        @SuppressWarnings("unchecked") T result = (T) out[0];
        return result;
    }

    private static Activity screen() throws Exception {
        Activity now = Screen.now();
        if (now == null) throw new Exception("No screen of TikTok is showing: it's in the background. Bring it to the front.");
        return now;
    }

    // ---------------------------------------------------------------------- calls

    private static JSONObject call(Context context, String tool, JSONObject a) throws Exception {
        switch (tool) {
            case "status": return status(context);
            case "logs": return logs(a.optInt("limit", 200), a.optString("filter", ""));
            case "plugin_install": return install(context, a);
            case "plugin_enable":
                Plugins.setEnabled(a.getString("id"), a.getBoolean("on"));
                return said(a.getBoolean("on") ? pluginState(a.getString("id")) : "Off.");
            case "plugin_remove":
                Plugins.uninstall(context, a.getString("id"));
                return said("Removed.");
            case "restart": return restart(context);
            case "screens": return onMain(DevLink::screens);
            case "views": return onMain(() -> views(a.optString("under", ""), a.optInt("depth", 40)));
            case "find": return onMain(() -> find(a.getString("query")));
            case "screenshot": return screenshot();
            case "tap": return onMain(() -> tap(a.getInt("x"), a.getInt("y"), a.optBoolean("hold")));
            case "swipe": return onMain(() -> swipe(a.getInt("x1"), a.getInt("y1"), a.getInt("x2"), a.getInt("y2"), a.optInt("ms", 300)));
            case "type": return onMain(() -> type(a.getString("text")));
            case "back": return onMain(() -> { screen().onBackPressed(); return said("Back."); });
            case "open": return onMain(() -> open(context, a));
            case "classes": return classes(context, a.getString("query"), a.optInt("limit", 100));
            case "class": return describe(a.getString("name"));
            case "settings_open": return onMain(() -> { Activity now = screen(); now.startActivity(new Intent(now, SettingsActivity.class)); return said("Opened."); });
            case "unlink": unlink(context); return said("Disconnected.");
            default: return failed("MargyT has no " + tool + ": this version may be older than the tool. Ask the person to update.");
        }
    }

    private static JSONObject status(Context context) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("MargyT ").append(Version.MOD).append(" on TikTok ").append(Version.TIKTOK)
                .append(", plugin api ").append(cat.narezany.margyt.plugin.MargyPlugin.API).append('\n');
        out.append("Android ").append(android.os.Build.VERSION.SDK_INT).append(", account ").append(Account.liveId()).append('\n');
        Activity now = Screen.now();
        out.append("In front: ").append(now == null ? "nothing, TikTok is in the background" : now.getClass().getName()).append('\n');
        List<Plugins.Info> list = Plugins.list();
        out.append("Plugins: ").append(list.isEmpty() ? "none" : "").append('\n');
        for (Plugins.Info one : list) out.append(pluginLine(one)).append('\n');
        return said(out.toString());
    }

    private static String pluginLine(Plugins.Info one) {
        return one.id + " " + one.version + ": "
                + (Plugins.isRunning(one.id) ? "running" : Plugins.isEnabled(one.id) ? "on but not running" : "off")
                + (one.trouble == null ? "" : ", trouble: " + one.trouble);
    }

    private static String pluginState(String id) {
        for (Plugins.Info one : Plugins.list()) if (one.id.equals(id)) return pluginLine(one);
        return id + ": not installed";
    }

    private static JSONObject logs(int limit, String filter) throws Exception {
        List<String> lines;
        synchronized (log) { lines = new ArrayList<>(log); }
        if (lines.isEmpty()) lines = Diary.lines();
        StringBuilder out = new StringBuilder();
        int from = 0;
        if (filter.length() > 0) {
            List<String> kept = new ArrayList<>();
            for (String line : lines) if (line.toLowerCase().contains(filter.toLowerCase())) kept.add(line);
            lines = kept;
        }
        from = Math.max(0, lines.size() - limit);
        for (int i = from; i < lines.size(); i++) out.append(lines.get(i)).append('\n');
        return said(out.length() == 0 ? "The diary is empty." : out.toString());
    }

    private static JSONObject install(Context context, JSONObject a) throws Exception {
        byte[] raw = Base64.decode(a.getString("mtp"), Base64.DEFAULT);
        File file = new File(context.getCacheDir(), "devlink.mtp");
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(raw); }
        int before;
        synchronized (log) { before = log.size(); }
        String id;
        try {
            id = Plugins.install(context, Uri.fromFile(file));
        } finally {
            file.delete();
        }
        Plugins.setEnabled(id, true);
        Thread.sleep(500);
        StringBuilder out = new StringBuilder("Installed and started. ").append(pluginState(id)).append('\n');
        List<String> lines;
        synchronized (log) { lines = new ArrayList<>(log); }
        for (int i = Math.min(before, lines.size()); i < lines.size(); i++) out.append(lines.get(i)).append('\n');
        return said(out.toString());
    }

    private static JSONObject restart(final Context context) throws Exception {
        MAIN.postDelayed(() -> {
            try {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    Activity now = Screen.now();
                    if (now != null) now.startActivity(intent); else context.startActivity(intent);
                }
            } catch (Throwable error) {
                Diary.note("devlink restart: " + error);
            }
            Runtime.getRuntime().exit(0);
        }, 300);
        return said("Restarting. Wait about 10 seconds before the next call.");
    }

    // ------------------------------------------------------------- what's on screen

    private static float scale(View root) {
        return 1280f / Math.max(1, Math.max(root.getWidth(), root.getHeight()));
    }

    private static JSONObject screens() throws Exception {
        Activity now = screen();
        StringBuilder out = new StringBuilder("Activity: ").append(now.getClass().getName()).append('\n');
        try {
            Object manager = now.getClass().getMethod("getSupportFragmentManager").invoke(now);
            fragments(manager, out, 0);
        } catch (Throwable error) {
            out.append("(fragments unavailable: ").append(error).append(")\n");
        }
        return said(out.toString());
    }

    private static void fragments(Object manager, StringBuilder out, int depth) throws Exception {
        if (manager == null || depth > 8) return;
        List<?> list = null;
        for (Method method : manager.getClass().getMethods()) {
            if (method.getParameterTypes().length == 0 && List.class.isAssignableFrom(method.getReturnType())) {
                Object got = method.invoke(manager);
                if (got instanceof List && (method.getName().equals("getFragments") || list == null)) list = (List<?>) got;
            }
        }
        if (list == null) return;
        for (Object fragment : list) {
            if (fragment == null) continue;
            boolean visible = false;
            try { visible = Boolean.TRUE.equals(fragment.getClass().getMethod("isVisible").invoke(fragment)); } catch (Throwable ignored) {}
            for (int i = 0; i <= depth; i++) out.append("  ");
            out.append(fragment.getClass().getName()).append(visible ? " (visible)" : "").append('\n');
            try { fragments(fragment.getClass().getMethod("getChildFragmentManager").invoke(fragment), out, depth + 1); } catch (Throwable ignored) {}
        }
    }

    private static View rootOf(Activity now) { return now.getWindow().getDecorView(); }

    private static void describeView(View view, float s, int indent, StringBuilder out) {
        for (int i = 0; i < indent && i < 24; i++) out.append(' ');
        out.append(view.getClass().getName());
        String id = Names.of(view);
        if (id != null && id.length() > 0) out.append(" #").append(id);
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) out.append(" \"").append(text.length() > 80 ? text.subSequence(0, 80) + "…" : text).append('"');
        }
        CharSequence about = view.getContentDescription();
        if (about != null && about.length() > 0) out.append(" about=\"").append(about).append('"');
        int[] at = new int[2];
        view.getLocationInWindow(at);
        out.append(" [").append((int) (at[0] * s)).append(',').append((int) (at[1] * s)).append(' ')
                .append((int) ((at[0] + view.getWidth()) * s)).append(',').append((int) ((at[1] + view.getHeight()) * s)).append(']');
        if (view.isClickable()) out.append(" tap");
        if (view.isLongClickable()) out.append(" hold");
        if (view.getVisibility() == View.INVISIBLE) out.append(" invisible");
        if (view.getAlpha() < 1f) out.append(" alpha=").append(view.getAlpha());
        out.append('\n');
    }

    private static JSONObject views(String under, int depth) throws Exception {
        View root = rootOf(screen());
        float s = scale(root);
        View from = root;
        if (under.length() > 0) {
            from = findFirst(root, under, 0);
            if (from == null) return failed("Nothing on this screen has the id or class " + under);
        }
        StringBuilder out = new StringBuilder("Coordinates are for a space where the long side is 1280, the same as screenshots and taps.\n");
        int[] count = {0};
        walk(from, s, 0, depth, out, count);
        if (count[0] >= 1500) out.append("… cut at 1500. Ask for less with `under`.\n");
        return said(out.toString());
    }

    private static void walk(View view, float s, int level, int depth, StringBuilder out, int[] count) {
        if (view.getVisibility() == View.GONE || count[0] >= 1500) return;
        describeView(view, s, level, out);
        count[0]++;
        if (view instanceof ViewGroup && level < depth) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), s, level + 1, depth, out, count);
        }
    }

    private static View findFirst(View view, String what, int depth) {
        if (depth > 60) return null;
        if (what.equals(Names.of(view)) || view.getClass().getName().equals(what) || view.getClass().getSimpleName().equals(what)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFirst(group.getChildAt(i), what, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JSONObject find(String query) throws Exception {
        View root = rootOf(screen());
        StringBuilder out = new StringBuilder();
        int[] count = {0};
        search(root, query.toLowerCase(), scale(root), out, count, 0);
        return said(count[0] == 0 ? "Nothing on this screen matches." : out.toString());
    }

    private static void search(View view, String query, float s, StringBuilder out, int[] count, int depth) {
        if (depth > 60 || count[0] >= 100 || view.getVisibility() == View.GONE) return;
        String text = view instanceof TextView && ((TextView) view).getText() != null ? ((TextView) view).getText().toString() : "";
        String about = view.getContentDescription() == null ? "" : view.getContentDescription().toString();
        String id = Names.of(view);
        if (text.toLowerCase().contains(query) || about.toLowerCase().contains(query) || (id != null && id.toLowerCase().contains(query))
                || view.getClass().getName().toLowerCase().contains(query)) {
            describeView(view, s, 0, out);
            count[0]++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) search(group.getChildAt(i), query, s, out, count, depth + 1);
        }
    }

    private static JSONObject screenshot() throws Exception {
        final Activity now = onMain(DevLink::screen);
        final View root = now.getWindow().getDecorView();
        float s = Math.min(1f, scale(root));
        final int width = Math.max(1, (int) (root.getWidth() * s)), height = Math.max(1, (int) (root.getHeight() * s));
        final Bitmap full = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        final CountDownLatch done = new CountDownLatch(1);
        final int[] result = {-1};
        android.os.HandlerThread thread = new android.os.HandlerThread("margyt-devlink-shot");
        thread.start();
        MAIN.post(() -> {
            try {
                PixelCopy.request(now.getWindow(), full, code -> { result[0] = code; done.countDown(); }, new Handler(thread.getLooper()));
            } catch (Throwable error) {
                done.countDown();
            }
        });
        boolean came = done.await(8, TimeUnit.SECONDS);
        thread.quitSafely();
        if (!came || result[0] != PixelCopy.SUCCESS) return failed("Android didn't give the picture, code " + result[0]);
        Bitmap small = Bitmap.createScaledBitmap(full, width, height, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return said("Picture is " + width + "x" + height + "; margyt_tap uses this space. Dialogs are separate windows and don't appear here.")
                .put("image", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
    }

    private static void touch(Activity activity, int action, long down, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        activity.dispatchTouchEvent(event);
        event.recycle();
    }

    private static JSONObject tap(int x, int y, boolean hold) throws Exception {
        final Activity now = screen();
        float s = scale(rootOf(now));
        final float px = x / s, py = y / s;
        final long down = android.os.SystemClock.uptimeMillis();
        touch(now, MotionEvent.ACTION_DOWN, down, px, py);
        MAIN.postDelayed(() -> touch(now, MotionEvent.ACTION_UP, down, px, py), hold ? 800 : 60);
        return said("Tapped.");
    }

    private static JSONObject swipe(int x1, int y1, int x2, int y2, int ms) throws Exception {
        final Activity now = screen();
        float s = scale(rootOf(now));
        final float ax = x1 / s, ay = y1 / s, bx = x2 / s, by = y2 / s;
        final long down = android.os.SystemClock.uptimeMillis();
        final int steps = Math.max(4, Math.min(40, ms / 16));
        touch(now, MotionEvent.ACTION_DOWN, down, ax, ay);
        for (int i = 1; i <= steps; i++) {
            final float t = i / (float) steps;
            final boolean last = i == steps;
            MAIN.postDelayed(() -> {
                float px = ax + (bx - ax) * t, py = ay + (by - ay) * t;
                touch(now, last ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, down, px, py);
            }, (long) (ms * t));
        }
        return said("Swiped.");
    }

    private static JSONObject type(String text) throws Exception {
        View focus = screen().getCurrentFocus();
        if (!(focus instanceof TextView)) return failed("No text field has focus. Tap one first.");
        TextView field = (TextView) focus;
        field.setText(text);
        if (field instanceof android.widget.EditText) ((android.widget.EditText) field).setSelection(field.getText().length());
        return said("Written.");
    }

    private static JSONObject open(Context context, JSONObject a) throws Exception {
        Activity now = Screen.now();
        Intent intent = a.optString("url").length() > 0
                ? new Intent(Intent.ACTION_VIEW, Uri.parse(a.optString("url"))).setPackage(context.getPackageName())
                : new Intent().setClassName(context, a.getString("activity"));
        if (now == null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); }
        else now.startActivity(intent);
        return said("Opened.");
    }

    // ---------------------------------------------------------------- what's in the app

    /** The names of everything in the apk, once: there are hundreds of thousands, so they stay on disk. */
    private static synchronized File classList(Context context) throws Exception {
        File file = new File(context.getCacheDir(), "devlink-classes-" + Version.MOD + ".txt");
        if (file.isFile() && file.length() > 0) return file;
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            @SuppressWarnings("deprecation")
            dalvik.system.DexFile dex = new dalvik.system.DexFile(context.getApplicationInfo().sourceDir);
            Enumeration<String> names = dex.entries();
            while (names.hasMoreElements()) { out.write(names.nextElement()); out.write('\n'); }
            dex.close();
        }
        return file;
    }

    private static JSONObject classes(Context context, String query, int limit) throws Exception {
        String wanted = query.toLowerCase();
        StringBuilder out = new StringBuilder();
        int count = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(classList(context)))) {
            String line;
            while ((line = in.readLine()) != null && count < limit) {
                if (line.toLowerCase().contains(wanted)) { out.append(line).append('\n'); count++; }
            }
        }
        return said(count == 0 ? "No class has that in its name." : out.toString());
    }

    private static JSONObject describe(String name) throws Exception {
        Class<?> type;
        try {
            type = Class.forName(name, false, DevLink.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            return failed("No class called " + name);
        }
        StringBuilder out = new StringBuilder(Modifier.toString(type.getModifiers())).append(' ').append(type.getName()).append('\n');
        if (type.getSuperclass() != null) out.append("extends ").append(type.getSuperclass().getName()).append('\n');
        for (Class<?> face : type.getInterfaces()) out.append("implements ").append(face.getName()).append('\n');
        for (Field field : type.getDeclaredFields())
            out.append("  ").append(Modifier.toString(field.getModifiers())).append(' ').append(field.getType().getName()).append(' ').append(field.getName()).append('\n');
        for (Constructor<?> made : type.getDeclaredConstructors())
            out.append("  ").append(Modifier.toString(made.getModifiers())).append(" <init>").append(params(made.getParameterTypes())).append('\n');
        for (Method method : type.getDeclaredMethods())
            out.append("  ").append(Modifier.toString(method.getModifiers())).append(' ').append(method.getReturnType().getName()).append(' ').append(method.getName()).append(params(method.getParameterTypes())).append('\n');
        return said(out.length() > 200000 ? out.substring(0, 200000) + "\n… cut" : out.toString());
    }

    private static String params(Class<?>[] types) {
        StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < types.length; i++) { if (i > 0) out.append(", "); out.append(types[i].getName()); }
        return out.append(')').toString();
    }
}
