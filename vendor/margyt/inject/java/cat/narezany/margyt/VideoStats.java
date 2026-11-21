package cat.narezany.margyt;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Everything known about a video, any video: a round button beside the share button of
 * the feed opens it, as a page of MargyT's settings. The numbers are what TikTok's own
 * post object carries; nothing is asked of the network.
 */
final class VideoStats {

    private VideoStats() {}

    static final String EXTRA = "margyt_video_stats";
    private static final int TAG = 0x7e0d0441;

    /** Opens the stats of a post: from the date under the author, in any feed. */
    static void open(Context context, Object post) {
        if (post == null || context == null) return;
        report(post);
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(EXTRA, read(post));
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // ---------------------------------------------------- history, gathered by everyone

    private static final java.util.ArrayList<String> queue = new java.util.ArrayList<>();
    private static final java.util.LinkedHashMap<String, Long> sent = new java.util.LinkedHashMap<String, Long>() {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> eldest) { return size() > 500; }
    };
    private static long flushedAt;

    /**
     * A video was played (or its stats opened): its public numbers go into the shared history,
     * at most once an hour for each video, sent in batches every few minutes.
     */
    static void report(Object post) {
        try {
            String aid = NativeRead.aid(post);
            if (aid.length() == 0) return;
            long now = System.currentTimeMillis() / 1000L;
            Object stats = NativeRead.get(post, "getStatistics");
            long play = NativeRead.number(NativeRead.get(stats, "getPlayCount"));
            if (play <= 0) return;
            synchronized (queue) {
                Long last = sent.get(aid);
                if (last != null && now - last < 3600) return;
                sent.put(aid, now);
                queue.add("{\"aid\":\"" + aid + "\",\"t\":" + now + ",\"play\":" + play
                        + ",\"digg\":" + NativeRead.number(NativeRead.get(stats, "getDiggCount"))
                        + ",\"comment\":" + NativeRead.number(NativeRead.get(stats, "getCommentCount"))
                        + ",\"share\":" + NativeRead.number(NativeRead.get(stats, "getShareCount"))
                        + ",\"collect\":" + NativeRead.number(NativeRead.get(stats, "getCollectCount")) + "}");
                if (queue.size() < 40 && System.currentTimeMillis() - flushedAt < 5 * 60_000L) return;
                flushedAt = System.currentTimeMillis();
                final String body = "{\"points\":[" + android.text.TextUtils.join(",", queue) + "]}";
                queue.clear();
                Net.away("video-stats", () -> Net.post(Badges.SERVER + "/vstats", body));
            }
        } catch (Throwable error) {
            Diary.detail("video stats report: " + error);
        }
    }

    /** The history of a video, oldest first: {t, play, digg, comment, share, collect} each. */
    interface Got { void got(long[][] points); }

    static void history(String aid, Got then) {
        Net.away("video-history", () -> {
            long[][] points = new long[0][];
            try {
                String text = Net.text(Badges.SERVER + "/vstats/" + aid);
                if (text != null) {
                    org.json.JSONArray list = new org.json.JSONObject(text).optJSONArray("points");
                    if (list != null) {
                        points = new long[list.length()][];
                        for (int i = 0; i < list.length(); i++) {
                            org.json.JSONArray p = list.getJSONArray(i);
                            points[i] = new long[p.length()];
                            for (int k = 0; k < p.length(); k++) points[i][k] = p.optLong(k);
                        }
                    }
                }
            } catch (Throwable error) {
                Diary.detail("video history: " + error);
            }
            final long[][] done = points;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> then.got(done));
        });
    }

    // ------------------------------------------------------------ the numbers

    static final String[] COUNTS = {"play", "digg", "comment", "share", "collect", "download", "forward", "repost", "lose", "lose_comment"};
    private static final String[] GETTERS = {"getPlayCount", "getDiggCount", "getCommentCount", "getShareCount", "getCollectCount",
            "getDownloadCount", "getForwardCount", "getRepostCount", "getLoseCount", "getLoseCommentCount"};

    /** What the page shows, copied out of the post: no model object is kept alive. */
    static Bundle read(Object post) {
        Bundle out = new Bundle();
        out.putString("aid", NativeRead.aid(post));
        out.putString("desc", NativeRead.string(NativeRead.get(post, "getDesc")));
        out.putLong("created", NativeRead.number(NativeRead.get(post, "getCreateTime")));
        out.putString("region", NativeRead.string(NativeRead.get(post, "getRegion")));
        out.putString("language", NativeRead.string(NativeRead.get(post, "getDescLanguage")));
        out.putString("share_url", NativeRead.string(NativeRead.get(post, "getShareUrl")));
        out.putLong("type", NativeRead.number(NativeRead.get(post, "getAwemeType")));
        Object ad = NativeRead.get(post, "isAd");
        out.putBoolean("ad", ad instanceof Boolean && (Boolean) ad);
        // every post carries an AIGCInfo; it is AI only when it says so (a label, or made by AI)
        Object aigc = NativeRead.get(post, "getAigcInfo");
        boolean ai = NativeRead.number(NativeRead.get(aigc, "getAIGCLabelType")) > 0;
        if (!ai && aigc != null) {
            try {
                java.lang.reflect.Field made = aigc.getClass().getDeclaredField("createByAI");
                made.setAccessible(true);
                ai = made.getBoolean(aigc);
            } catch (Throwable ignored) {
            }
        }
        out.putBoolean("ai", ai);
        Object author = NativeRead.get(post, "getAuthor");
        out.putString("author", NativeRead.string(NativeRead.get(author, "getNickname")));
        out.putString("handle", NativeRead.string(NativeRead.get(author, "getUniqueId")));
        out.putString("uid", NativeRead.uid(author));
        out.putLong("followers", NativeRead.number(NativeRead.get(author, "getFollowerCount")));
        Object stats = NativeRead.get(post, "getStatistics");
        for (int i = 0; i < COUNTS.length; i++) out.putLong(COUNTS[i], NativeRead.number(NativeRead.get(stats, GETTERS[i])));
        Object video = NativeRead.get(post, "getVideo");
        out.putLong("duration", NativeRead.number(NativeRead.get(video, "getDuration")));
        out.putLong("width", NativeRead.number(NativeRead.get(video, "getWidth")));
        out.putLong("height", NativeRead.number(NativeRead.get(video, "getHeight")));
        out.putString("ratio", NativeRead.string(NativeRead.get(video, "getRatio")));
        out.putString("format", NativeRead.string(NativeRead.get(video, "getFormat")));
        Object rates = NativeRead.get(video, "getBitRate");
        long best = 0;
        int qualities = 0;
        if (rates instanceof List) {
            for (Object rate : (List<?>) rates) {
                qualities++;
                best = Math.max(best, NativeRead.number(NativeRead.get(rate, "getBitRate")));
            }
        }
        out.putLong("bitrate", best);
        out.putInt("qualities", qualities);
        Object images = NativeRead.get(post, "getImageInfos");
        out.putInt("images", images instanceof List ? ((List<?>) images).size() : 0);
        Object music = NativeRead.get(post, "getMusic");
        out.putString("music", NativeRead.string(NativeRead.get(music, "getMusicName")).length() > 0
                ? NativeRead.string(NativeRead.get(music, "getMusicName")) : NativeRead.string(NativeRead.get(music, "getTitle")));
        out.putString("music_author", NativeRead.string(NativeRead.get(music, "getAuthorName")));
        Object challenges = NativeRead.get(post, "getChallengeList");
        ArrayList<String> tags = new ArrayList<>();
        if (challenges instanceof List) {
            for (Object one : (List<?>) challenges) {
                String name = NativeRead.string(NativeRead.get(one, "getChallengeName"));
                if (name.length() > 0) tags.add(name);
            }
        }
        // TikTok's list holds only the hashtags it made into challenges: the caption has them all
        java.util.regex.Matcher hash = java.util.regex.Pattern.compile("#([\\p{L}\\p{N}_]+)")
                .matcher(NativeRead.string(NativeRead.get(post, "getDesc")));
        while (hash.find()) {
            boolean known = false;
            for (String tag : tags) if (tag.equalsIgnoreCase(hash.group(1))) { known = true; break; }
            if (!known) tags.add(hash.group(1));
        }
        out.putStringArrayList("tags", tags);
        return out;
    }
}
