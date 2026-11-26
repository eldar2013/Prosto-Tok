package cat.narezany.margyt;

import android.content.SharedPreferences;

import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;

import java.util.ArrayList;
import java.util.List;

/**
 * The advertisements, taken out of the page before anything sees it.
 *
 * TikTok's feed arrives as a `FeedItemList`, and every post in it says for
 * itself whether it is an advertisement -- `Aweme.isAd()`, a real name in a
 * real model. So the ads are dropped where the page is read rather than hidden
 * on each screen that might draw one: `FeedItemList.getItems()` is rewritten
 * to come through here, and what comes back is the same list without them.
 *
 * Answering `isAd()` with false instead would be the wrong lever. The post
 * would still be in the feed; it would simply stop being labelled as an
 * advertisement, which is worse than leaving it alone.
 *
 * Whatever cannot be understood is passed through untouched. A feed with no
 * ads is a preference; a feed that does not load is a broken app.
 */
public final class Feed {

    private Feed() {}

    static void reloadSettings(){cached=null;}
    public static final String KEY = "hide_ads";
    public static final String KEY_VIDEOS = "hide_videos";
    public static final String KEY_LIVE = "hide_live";
    public static final String KEY_PHOTOS = "hide_photos";
    public static final String KEY_STORIES="hide_stories",KEY_SUGGESTIONS="hide_suggestions";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        boolean enabled = prefs != null && prefs.getBoolean(KEY, false);
        cached = enabled;
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
    }

    // --------------------------------------------- what else to leave out

    public static boolean hides(String key) {
        // 2.2.2: hiding the suggestion cards is gone; whoever had it on gets them back
        if (KEY_SUGGESTIONS.equals(key)) return false;
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(key, false);
    }

    public static void setHides(String key, boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, on).apply();
    }

    /** TikTok's own number for a live room in the feed. */
    private static final int LIVE = 101;

    private static boolean isLive(Aweme post) {
        try {
            if (post.getAwemeType() == LIVE) return true;
            if (post.getLiveId() != 0) return true;
            return post.getRoomFeedCellStruct() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isVideo(Aweme post) {
        try {
            // 0 is the ordinary video type. Image-mode posts can also carry
            // a video-like cover, so exclude every positively identified photo.
            return post.getAwemeType() == 0 && !isPhotos(post);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPhotos(Aweme post) {
        try {
            if (post.getPhotoModeImageInfo() != null) return true;
        } catch (Throwable ignored) {
        }
        try {
            // TikTok's image-mode Aweme type is 150 in the 47.x model.
            if (post.getAwemeType() == 150) return true;
        } catch (Throwable ignored) {
        }
        try {
            // Some 47.x photo posts expose their slides through getImages()
            // even when PhotoModeImageInfo has not been populated yet.
            Object images = NativeRead.get(post, "getImages");
            if (images instanceof java.util.Collection) {
                return !((java.util.Collection) images).isEmpty();
            }
            if (images != null && images.getClass().isArray()) {
                return java.lang.reflect.Array.getLength(images) > 0;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Your own post, which is never hidden from you.
     *
     * These filters are for recommendations, but every list of posts goes
     * through the same place, including your own profile. Turn off photo
     * posts and your own vanish from your own page.
     */
    private static boolean mine(Aweme post) {
        try {
            String me = Account.id();
            if (me == null || me.length() == 0) return false;
            return me.equals(post.getAuthorUid());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Made outside the dates the person chose; their own posts and live rooms stay. */
    private static boolean outOfDates(Aweme post) {
        try {
            if (mine(post) || isLive(post) || advert(post)) return false;
            return FeedDates.leavesOut(NativeRead.number(NativeRead.get(post, "getCreateTime")));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Whether this post is one the person asked not to see. */
    private static Aweme feedAweme(Object item) {
        if (item instanceof Aweme) return (Aweme) item;
        Object nested = NativeRead.get(item, "getAweme");
        return nested instanceof Aweme ? (Aweme) nested : null;
    }

    private static boolean unwanted(Object item, boolean ads, boolean live, boolean photos, boolean videos) {
        Aweme post = feedAweme(item);
        try {
            if (ads && (advert(post) || (post == null && isAdCard(item)))) return true;
            if (post == null) return false;
            if (mine(post)) return false;
            if (live && isLive(post)) return true;
            if (isLive(post)) return false;   // a room is neither a photo nor a video
            if (photos && isPhotos(post)) return true;
            if (videos && isVideo(post)) return true;
            if (Tags.blocks(post)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isAdCard(Object item) {
        if (item == null || item instanceof Aweme) return false;
        return NativeRead.get(item, "getPreciseAd") != null
                || NativeRead.get(item, "getAiAdCard") != null;
    }

    /** isAd() in 47.2.41 also requires awemeRawAd != null. The server's explicit
     * _isAd flag must still exclude an ad while its payload is missing/loading.
     * A raw-ad payload alone is deliberately insufficient for organic posts. */
    static boolean advert(Object post){
        if(!(post instanceof Aweme))return false;
        return ((Aweme)post).isAd() || Boolean.TRUE.equals(NativeRead.field(post,"_isAd"));
    }
    /** Native getter/ingress hook: does not run plugins or other feed preferences twice. */
    public static List withoutAds(List items){
        if(items==null || !isEnabled())return items;
        ArrayList kept=null;
        for(int i=0;i<items.size();i++){
            Object item=items.get(i);
            Object post=feedAweme(item);
            boolean card=isAdCard(item);
            if(advert(post) || card){if(kept==null){kept=new ArrayList(items.size());kept.addAll(items.subList(0,i));}}
            else if(kept!=null)kept.add(item);
        }
        return kept==null?items:kept;
    }
    public static List searchItems(com.ss.android.ugc.aweme.search.pages.result.topsearch.core.model.SearchMixFeedList page){
        if(page==null)return null;
        List given=page.mItems,kept=withoutAds(given);
        // Some people search and are told at once that nothing was found. Whether TikTok
        // had nothing to show or the mod took it all away is the first thing to know.
        int had=given==null?-1:given.size(),left=kept==null?-1:kept.size();
        if(had<=0||left!=had)Diary.note("search: a page of "+(had<0?"nothing at all":had+" from TikTok")+(left!=had?", "+left+" after the mod":""));
        return kept;
    }
    public static List rawItems(FeedItemList page){return page==null?null:without(page.items);}

    private static SharedPreferences prefs() {
        try {
            android.content.Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A social repost card is not a suggested-account card. Preserve its native data. */
    static boolean socialRepost(Object post){return NativeRead.get(post,"getUpvoteReason")!=null || NativeRead.get(post,"getUpvoteInfo")!=null || NativeRead.get(post,"getUpvotePreload")!=null;}
    static boolean suggestion(Object post){
        if(NativeRead.number(NativeRead.get(post,"getRecommendCardType"))<=0)return false;
        if(socialRepost(post))return false;
        return true;
    }

    /** How many have been dropped since the process started, for the diary. */
    private static int dropped;
    private static long reportedAt;

    // ------------------------------------------------ where the call lands

    /**
     * Every `FeedItemList.getItems()` in TikTok's bytecode comes here first.
     *
     * The list is only rebuilt when there is something to leave out -- the
     * ordinary page comes back as the very object the app asked for, which
     * keeps this out of the way of everything that reads a feed and is not
     * looking for advertisements.
     */
    public static List getItems(FeedItemList page) {
        if (page == null) return null;
        long probe = Probe.start();
        List out = without(page.getItems());
        Probe.stop(Probe.FEED, probe);
        return out;
    }

    /** Native FeedItemList.getItems/setItems hook: apply every active feed filter. */
    public static List filteredItems(List items) {
        return without(items);
    }

    /** A page of posts without the ones nobody asked to see. */
    private static List without(List items) {
        if (items == null) return items;
        boolean ads=isEnabled();SharedPreferences options=prefs();
        boolean live=options!=null && options.getBoolean(KEY_LIVE,false);
        boolean photos=options!=null && options.getBoolean(KEY_PHOTOS,false);
        boolean videos=options!=null && options.getBoolean(KEY_VIDEOS,false);
        // Stories are intentionally not filtered; the user-facing control is live rooms.
        boolean suggestions=options!=null && options.getBoolean(KEY_SUGGESTIONS,false);
        boolean authors=!FeedBlacklist.items().isEmpty() || !FeedBlacklist.cards().isEmpty();
        // a hashtag somebody blocked is a reason to look at the page too
        boolean others=!Tags.all().isEmpty();
        boolean dated=FeedDates.active();
        if (!ads && !live && !photos && !videos && !suggestions && !authors && !others && !dated) {
            return Plugins.feed(items);
        }
        try {
            int out=0,index=0;
            List kept=null;
            for(Object item:items) {
                Aweme post=feedAweme(item);
                boolean extra=post!=null && ((suggestions && suggestion(post))
                        || (authors && FeedBlacklist.hides(post))
                        || (dated && outOfDates(post)));
                if(extra || unwanted(item,ads,live,photos,videos)) {
                    if(kept==null){kept=new ArrayList(items.size());kept.addAll(items.subList(0,index));}
                    out++;
                } else if(kept!=null)kept.add(item);
                index++;
            }
            if(kept==null)return Plugins.feed(items);
            synchronized(Feed.class) {
                dropped+=out;long now=System.currentTimeMillis();
                if(now-reportedAt>=1000L){reportedAt=now;Diary.note("feed: " + out + " left out, " + dropped + " so far");}
            }
            return Plugins.feed(kept);
        } catch (Throwable error) {
            Diary.note("feed: leaving the page alone, " + error);
            return items;
        }
    }
}
