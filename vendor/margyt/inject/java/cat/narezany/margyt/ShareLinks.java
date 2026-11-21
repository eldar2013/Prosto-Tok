package cat.narezany.margyt;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clean links: a TikTok link put on the clipboard loses what tracks who shared it.
 *
 * "Copy link" gives a short link (vm.tiktok.com/…) that stands for the video and for the
 * person who copied it, and long links carry ?_r=…&u_code=…&share_item_id=… and the like;
 * whoever opens them is shown who sent it and asked to answer. The short link is followed
 * to where it leads, and what goes on the clipboard is the bare address of the video:
 * https://www.tiktok.com/@author/video/123.
 *
 * Every setPrimaryClip in TikTok comes here (the build rewrites the calls). Anything that is
 * not a TikTok link goes on as it was.
 */
public final class ShareLinks {

    private ShareLinks() {}

    static final String KEY = "clean_links";

    static boolean enabled() {
        Context c = Margy.context();
        return c == null || c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    static void setEnabled(boolean on) {
        Context c = Margy.context();
        if (c != null) c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://[^\\s]+");
    private static final Pattern SHORT = Pattern.compile("https?://(?:vm|vt)\\.tiktok\\.com/[^\\s]+|https?://(?:www\\.)?tiktok\\.com/t/[^\\s]+");
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    public static void setPrimaryClip(final ClipboardManager manager, final ClipData clip) {
        try {
            String text = text(clip);
            if (!enabled() || text == null || !text.contains("tiktok.com")) {
                manager.setPrimaryClip(clip);
                return;
            }
            final CharSequence label = clip.getDescription() == null ? "" : clip.getDescription().getLabel();
            if (!SHORT.matcher(text).find()) {
                String clean = cleanLong(text);
                manager.setPrimaryClip(clean.equals(text) ? clip : ClipData.newPlainText(label, clean));
                return;
            }
            // a short link has to be followed to learn which video it is: off the main thread,
            // and the clipboard is filled when that is known (or with the original if it is not)
            final String original = text;
            Net.away("clean-link", () -> {
                String clean = original;
                try {
                    Matcher m = SHORT.matcher(original);
                    StringBuffer out = new StringBuffer();
                    while (m.find()) {
                        String resolved = follow(trim(m.group()));
                        m.appendReplacement(out, Matcher.quoteReplacement(resolved == null ? m.group() : strip(resolved)));
                    }
                    m.appendTail(out);
                    clean = cleanLong(out.toString());
                } catch (Throwable error) {
                    Diary.note("clean link: " + error);
                }
                final ClipData done = clean.equals(original) ? clip : ClipData.newPlainText(label, clean);
                MAIN.post(() -> {
                    try { manager.setPrimaryClip(done); } catch (Throwable ignored) { }
                });
            });
        } catch (Throwable error) {
            try { manager.setPrimaryClip(clip); } catch (Throwable ignored) { }
        }
    }

    private static String text(ClipData clip) {
        if (clip == null || clip.getItemCount() != 1) return null;
        CharSequence text = clip.getItemAt(0).getText();
        return text == null ? null : text.toString();
    }

    /** A link ends where a sentence goes on: a closing bracket or a full stop is not part of it. */
    private static String trim(String url) {
        while (url.length() > 0 && ".,!?)]}»\"'".indexOf(url.charAt(url.length() - 1)) >= 0) url = url.substring(0, url.length() - 1);
        return url;
    }

    /** Every long TikTok link in the text, without its query and fragment. */
    static String cleanLong(String text) {
        Matcher m = URL_IN_TEXT.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String url = trim(m.group());
            String rest = m.group().substring(url.length());
            String host = host(url);
            String replaced = host != null && (host.equals("tiktok.com") || host.endsWith(".tiktok.com"))
                    && !SHORT.matcher(url).matches() ? strip(url) : url;
            m.appendReplacement(out, Matcher.quoteReplacement(replaced + rest));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String host(String url) {
        try {
            return new URL(url).getHost().toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable error) {
            return null;
        }
    }

    /** https://www.tiktok.com/@a/video/1?x=y#z -> https://www.tiktok.com/@a/video/1 */
    static String strip(String url) {
        try {
            URL parsed = new URL(url);
            String host = parsed.getHost().toLowerCase(java.util.Locale.ROOT);
            if (host.equals("m.tiktok.com") || host.equals("tiktok.com")) host = "www.tiktok.com";
            String path = parsed.getPath();
            // m.tiktok.com/v/123.html is the same video
            Matcher old = Pattern.compile("^/v/(\\d+)\\.html$").matcher(path);
            if (old.find()) path = "/@_/video/" + old.group(1);
            return "https://" + host + path;
        } catch (Throwable error) {
            return url;
        }
    }

    /** Where a short link leads: its redirects followed, nothing of the page read. */
    private static String follow(String url) {
        String at = url;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(at).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1");
                int code = connection.getResponseCode();
                String next = connection.getHeaderField("Location");
                if (code / 100 != 3 || next == null) return hop == 0 ? null : at;
                at = new URL(new URL(at), next).toString();
                if (host(at) != null && host(at).endsWith("tiktok.com") && at.matches(".*/(video|photo)/\\d+.*")) return at;
            } catch (Throwable error) {
                return hop == 0 ? null : at;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return at;
    }
}
