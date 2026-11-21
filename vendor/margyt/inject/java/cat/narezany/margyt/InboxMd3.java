package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** The approved inbox: native conversations, compact notification shortcuts and a compose button. */
final class InboxMd3 {
    private InboxMd3() {}

    private static final int SESSION = 0x7e0c0630, SAVED = 0x7e0c0631, HEADER = 0x7e0c0632,
            NATIVE_INK = 0x7e0c0633, PAINTED_INK = 0x7e0c0634,
            NATIVE_BG = 0x7e0c0635, PAINTED_BG = 0x7e0c0636, STORY_RING = 0x7e0c0637;
    private static final Map<ViewGroup, WeakReference<Session>> sessions = new WeakHashMap<>();
    private static final String[] LABELS = {"Подписчики", "Активность", "Система", "Монетизация"};
    private static final String[] GLYPHS = {"people", "bolt", "inbox", "link"};
    private static final int[] COLOURS = {0xff5865f2, 0xfff13674, 0xff3b3840, 0xff6d38ed};
    private static final Map<String,Integer> ids = new java.util.HashMap<>();

    static boolean enabled() { return Md3.isOn(Md3.KEY_INBOX); }

    static boolean storyRing(Object owner) {
        return owner instanceof View && Boolean.TRUE.equals(((View) owner).getTag(STORY_RING));
    }

    static void list(ViewGroup list, boolean on) {
        Object held = list.getTag(SESSION);
        Session session = held instanceof Session ? (Session) held : null;
        if (!on) { if (session != null) session.restore(); return; }
        if (!visible(list)) return;
        if (session == null) {
            session = new Session(list);
            list.setTag(SESSION, session);
            sessions.put(list, new WeakReference<>(session));
        }
        session.updateIfChanged();
    }

    private static long fullPass;

    /**
     * Twice a second from Md3: the rows restyled only when they have changed, and
     * fully every two seconds so the counters on the tiles keep up. It used to be a
     * full restyle every time, which laid the screen out again, which walked the
     * whole screen again: the lag of 2.2.2.
     */
    static void tick() {
        if (!enabled()) return;
        long now = android.os.SystemClock.uptimeMillis();
        boolean full = now - fullPass > 2000L;
        if (full) fullPass = now;
        for (Map.Entry<ViewGroup, WeakReference<Session>> entry : sessions.entrySet()) {
            Session session = entry.getValue().get();
            ViewGroup list = entry.getKey();
            if (session == null || !list.isAttachedToWindow()) continue;
            if (!visible(list)) { if (session.fab != null) session.fab.setVisibility(View.GONE); continue; }
            if (full) { session.update(); session.remember(); } else session.updateIfChanged();
        }
    }

    static void refresh() {
        boolean on = enabled();
        for (Map.Entry<ViewGroup, WeakReference<Session>> entry : sessions.entrySet()) {
            Session session = entry.getValue().get();
            if (session == null) continue;
            if (!on) session.restore();
            else if (entry.getKey().isAttachedToWindow()) {
                if (visible(entry.getKey())) session.update();
                else if (session.fab != null) session.fab.setVisibility(View.GONE);
            }
        }
    }

    /** Native binding can remove our panel from a recycled filter row. Repair
     * that hierarchy before drawing, without restyling the list every frame. */
    static void frame() {
        if(!enabled())return;
        for(Map.Entry<ViewGroup,WeakReference<Session>> entry:sessions.entrySet()){
            Session session=entry.getValue().get();ViewGroup list=entry.getKey();
            if(session==null)continue;
            if(!list.isAttachedToWindow()||!visible(list)){
                if(session.fab!=null)session.fab.setVisibility(View.GONE);
                continue;
            }
            Header header=session.header;
            if((header!=null&&header.panel.getParent()!=header.row.get())
                    ||(session.fab!=null&&session.fab.getParent()!=session.composeHost(list)))session.update();
            else if(session.topology!=topology(list))session.updateIfChanged();
            else session.composeVisibility(list);
        }
    }

    static int category(String title, String preview) {
        String text = (title + " " + preview).toLowerCase(Locale.ROOT);
        if (text.contains("актив") || text.contains("activ")) return 1;
        if (text.contains("систем") || text.contains("system")) return 2;
        if (text.contains("монет") || text.contains("moneti")) return 3;
        if (text.contains("подпис") || text.contains("підпис") || text.contains("follow")) return 0;
        return -1;
    }

    private static final class Session {
        final WeakReference<ViewGroup> list;
        final ArrayList<WeakReference<View>> changed = new ArrayList<>();
        final Target[] targets = new Target[4];
        final Tile[] tiles = new Tile[4];
        final WeakHashMap<View,Boolean> folders = new WeakHashMap<>();
        Header header;
        LinearLayout fab;
        TextView fabLabel;
        ImageView fabIcon;
        WeakReference<View> composeButton=new WeakReference<>(null);
        int page, ink, muted, surface, primary, onPrimary, topology;

        Session(ViewGroup list) { this.list = new WeakReference<>(list); }

        Saved keep(View view) {
            Object held = view.getTag(SAVED);
            if (held instanceof Saved) return (Saved) held;
            Saved saved = new Saved(view);
            view.setTag(SAVED, saved);
            changed.add(new WeakReference<>(view));
            return saved;
        }

        private int done;

        /**
         * update() is asked on every layout of the screen and twice a second besides, and
         * restyling the rows lays the screen out again: it runs only when the rows, their
         * order, their size or the colours have changed since the last time.
         */
        void updateIfChanged() {
            ViewGroup list = this.list.get();
            if (list == null) return;
            int sig = shape(list);
            if (sig == done && done != 0) return;
            update();
            remember();
        }

        void remember() {
            ViewGroup list = this.list.get();
            if (list != null) done = shape(list);
        }

        private int shape(ViewGroup list) {
            int sig = topology(list);
            for (int i = 0; i < list.getChildCount(); i++) sig = 31 * sig + list.getChildAt(i).getHeight();
            return 31 * (31 * sig + Md3.behind(list)) + Accent.colour();
        }

        void update() {
            ViewGroup list = this.list.get();
            if (list == null) return;
            page = Md3.behind(list);
            ink = Md3.ink(page);
            muted = NavBar.mix(ink, page, .24f);
            surface = NavBar.mix(page, ink, .055f);
            int accent = Accent.colour() | 0xff000000;
            primary = NavBar.mix(accent, NavBar.bright(page) < .5f ? Color.WHITE : page, .56f);
            onPrimary = Md3.ink(primary);
            ViewGroup filter = null;
            boolean[] seen = new boolean[4];
            // Read the current bound native rows before changing any dimensions.
            for (int i = 0; i < list.getChildCount(); i++) {
                View block = list.getChildAt(i);
                View row = named(block, "uxm");
                int kind = noticeKind(row);
                if (kind >= 0) {
                    seen[kind] = true;
                    targets[kind] = new Target(list, row, block, kind);
                }
                if (named(block, "user_name") != null) chat(block);
                HorizontalScrollView strip = scroller(block, 0);
                if (strip != null && block.isShown() && named(block, "user_name") == null && block instanceof ViewGroup) filter = (ViewGroup) block;
                if (named(block, "wq0") != null && strip == null) stories(block);
            }
            for (int i=0;i<targets.length;i++) {
                Target target=targets[i];if(target==null||seen[i])continue;
                if(!target.available())targets[i]=null;
            }
            // the shortcut tiles (followers, activity, monetization) are taken out at the owner's
            // word (2.3.0): TikTok's own notification rows stay where they are
            if (header != null) { header.restore(); header = null; }
            // Shortcuts are additional entry points. Native notification rows
            // always remain in the list, including rows first bound on scroll.
            top(list.getRootView());
            compose(list);
            topology=topology(list);
        }

        void chat(View block) {
            View row = named(block, "w0w");
            if (row == null) return;
            Saved saved = keep(row);
            if (!(row.getBackground() instanceof FlatRipple)) row.setBackground(new FlatRipple(Accent.colour()));
            size(row, null, dp(row, 76));
            View nickname = named(row, "user_name");
            // Use the same role colour as native binding and the theme sweep.
            // Otherwise those passes and MD3 alternate the name's foreground.
            text(nickname, 16.5f, AppearanceColors.forView(nickname, ink), true);
            text(named(row, "mv1"), 14, muted, false);
            View portrait = named(row, "w0u"), content = named(row, "wip");
            if (portrait != null) margin(portrait, 16);
            if (content != null) margin(content, 14);
            dividers(row, 0);
            View time = named(row, "mv7");
            View title = named(row, "user_name");
            if (time instanceof TextView && time.getVisibility() == View.VISIBLE) {
                text(time, 12, muted, false);
                keep(time);
                if (title != null && time.getWidth() > 0 && row.getWidth() > 0) {
                    int[] base = new int[2], at = new int[2], top = new int[2];
                    row.getLocationInWindow(base); time.getLocationInWindow(at); title.getLocationInWindow(top);
                    float x = row.getWidth() - dp(row, 16) - time.getWidth() - (at[0] - base[0] - time.getTranslationX());
                    float y = top[1] - (at[1] - time.getTranslationY());
                    if (Math.abs(time.getTranslationX() - x) > .5f) time.setTranslationX(x);
                    if (Math.abs(time.getTranslationY() - y) > .5f) time.setTranslationY(y);
                    unclip(time, row);
                    if (title instanceof TextView) {
                        int trailing = 0;
                        View line = named(row, "zee");
                        if (line instanceof ViewGroup) {
                            ViewGroup group = (ViewGroup) line;
                            for (int i=group.indexOfChild(title)+1;i<group.getChildCount();i++) {
                                View child=group.getChildAt(i);
                                if(child.getVisibility()!=View.GONE)trailing+=child.getWidth();
                            }
                        }
                        int available=row.getWidth()-dp(row,24)-time.getWidth()-(top[0]-base[0])-trailing;
                        TextView name=(TextView)title;
                        int original=keep(title).maxWidth;
                        int width=Math.min(original<0?Integer.MAX_VALUE:original,Math.max(dp(row,24),available));
                        if(name.getMaxWidth()!=width)name.setMaxWidth(width);
                    }
                }
            } else if (title instanceof TextView) {
                Saved original=keep(title);TextView name=(TextView)title;
                if(original.maxWidth>=0&&name.getMaxWidth()!=original.maxWidth)name.setMaxWidth(original.maxWidth);
                else if(original.maxWidth<0&&name.getMaxEms()!=original.maxEms)name.setMaxEms(original.maxEms);
            }
            // TikTok centres these in the entire row. Keep their native handlers,
            // but place the visible camera/badge below the timestamp.
            int right = dp(row,16);
            for (String name : new String[]{"gdi", "gdm"}) {
                View action = named(row,name);
                if(action==null||!action.isShown()||action.getWidth()<=0||action.getHeight()<=0)continue;
                keep(action);unclip(action,row);
                int[] base=new int[2],at=new int[2];row.getLocationInWindow(base);action.getLocationInWindow(at);
                float x=row.getWidth()-right-action.getWidth()-(at[0]-base[0]-action.getTranslationX());
                float y=dp(row,76)-dp(row,10)-action.getHeight()-(at[1]-base[1]-action.getTranslationY());
                if(Math.abs(action.getTranslationX()-x)>.5f)action.setTranslationX(x);
                if(Math.abs(action.getTranslationY()-y)>.5f)action.setTranslationY(y);
                right+=action.getWidth()+dp(row,8);
            }
        }

        void unclip(View view,View limit) {
            Object parent=view.getParent();
            while(parent instanceof ViewGroup&&parent!=limit){
                ViewGroup group=(ViewGroup)parent;keep(group);group.setClipChildren(false);group.setClipToPadding(false);parent=group.getParent();
            }
        }

        void margin(View view, int left) {
            if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
            keep(view);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            int margin = dp(view, left);
            if (params.leftMargin != margin || params.getMarginStart() != margin) {
                params.leftMargin = margin;params.setMarginStart(margin);view.setLayoutParams(params);
            }
        }

        void text(View view, float sp, int colour, boolean bold) {
            if (!(view instanceof TextView)) return;
            keep(view);TextView text = (TextView) view;
            if (text.getCurrentTextColor() != colour) text.setTextColor(colour);
            float wanted = sp * text.getResources().getDisplayMetrics().scaledDensity;
            if (Math.abs(text.getTextSize() - wanted) > .5f) text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            if (text.getMaxLines() != 1) text.setMaxLines(1);
            if (bold && (text.getTypeface() == null || !text.getTypeface().isBold()))
                text.setTypeface(Typeface.create(text.getTypeface(), Typeface.BOLD));
        }

        void dividers(View view, int depth) {
            if (!(view instanceof ViewGroup) || depth > 6) return;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getClass() == View.class && child.getHeight() > 0 && child.getHeight() <= dp(child, 1)) {
                    keep(child);if (child.getAlpha() != 0) child.setAlpha(0);
                } else dividers(child, depth + 1);
            }
        }

        void stories(View block) {
            if (!(block instanceof ViewGroup)) return;
            ViewGroup strip = (ViewGroup) block;
            keep(strip);
            // One-item strips must retain the inbox viewport, not the width of
            // their first cell. Frames, badges and notes extend past that cell.
            if(strip.getParent()==list.get())size(strip,ViewGroup.LayoutParams.MATCH_PARENT,null);
            strip.setClipChildren(false);strip.setClipToPadding(false);strip.setClipToOutline(false);
            for (int i = 0; i < strip.getChildCount(); i++) {
                View cell = strip.getChildAt(i), avatar = named(cell, "wq0");
                if (avatar == null) continue;
                // Keep TikTok's measured geometry for ordinary, self and LIVE
                // cells. Resizing only one avatar type clipped it and made the
                // unmodified LIVE cells look much larger.
                keep(cell);unclip(avatar,strip);
                if(cell instanceof ViewGroup)((ViewGroup)cell).setClipToOutline(false);
                if(avatar instanceof ViewGroup){
                    ViewGroup group=(ViewGroup)avatar;
                    for(int j=0;j<group.getChildCount();j++){
                        View child=group.getChildAt(j);
                        if(child.getClass().getName().equals("X.11Wv")){keep(child);child.setTag(STORY_RING,Boolean.TRUE);}
                    }
                }
            }
        }

        void top(View root) {
            View bar = named(root, "l8s"), group = named(bar, "l7a"), add = named(bar, "fha"), search = named(bar, "kou");
            if (!(bar instanceof RelativeLayout) || group == null || add == null || search == null) return;
            text(named(group, "title"), 28, ink, true);
            if (group.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                Saved saved = keep(group);
                if (saved.rules == null) saved.rules = ((RelativeLayout.LayoutParams) group.getLayoutParams()).getRules().clone();
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) group.getLayoutParams();
                if (params.getRule(RelativeLayout.ALIGN_PARENT_START) == 0) {
                    params.removeRule(RelativeLayout.CENTER_IN_PARENT);params.removeRule(RelativeLayout.CENTER_HORIZONTAL);
                    params.addRule(RelativeLayout.ALIGN_PARENT_START);params.addRule(RelativeLayout.CENTER_VERTICAL);
                    params.leftMargin = dp(group, 20);params.setMarginStart(dp(group, 20));group.setLayoutParams(params);
                }
            }
            View actions = search.getParent() instanceof View ? (View) search.getParent() : null;
            if (actions != null && add.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                if (actions.getId() == View.NO_ID) actions.setId(View.generateViewId());
                Saved saved = keep(add);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) add.getLayoutParams();
                if (saved.rules == null) saved.rules = params.getRules().clone();
                if (params.getRule(RelativeLayout.START_OF) != actions.getId()) {
                    params.removeRule(RelativeLayout.ALIGN_PARENT_START);params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    params.addRule(RelativeLayout.START_OF, actions.getId());params.addRule(RelativeLayout.CENTER_VERTICAL);
                    params.leftMargin = 0;params.setMarginStart(0);add.setLayoutParams(params);
                }
            }
        }

        void compose(ViewGroup list) {
            View root = list.getRootView();
            FrameLayout content=composeHost(list);
            if(fab!=null&&fab.getParent()!=content){
                if(fab.getParent() instanceof ViewGroup)((ViewGroup)fab.getParent()).removeView(fab);
                fab=null;
            }
            if(content==null)return;
            View scope=list,nativeButton=null;
            for(int i=0;i<12&&scope!=null&&scope.getId()!=android.R.id.content;i++){
                nativeButton=named(scope,"fha");if(nativeButton!=null)break;
                scope=scope.getParent() instanceof View?(View)scope.getParent():null;
            }
            composeButton=new WeakReference<>(nativeButton);
            if (fab == null) {
                Context context = list.getContext();
                fab = new LinearLayout(context);fab.setGravity(Gravity.CENTER);fab.setPadding(dp(list, 20),0,dp(list,20),0);
                fabIcon = new ImageView(context);fab.addView(fabIcon, new LinearLayout.LayoutParams(dp(list,24),dp(list,24)));
                fabLabel = label(context, Local.of("Новый чат"), 15);fabLabel.setPadding(dp(list,10),0,0,0);fab.addView(fabLabel);
                fab.setContentDescription(Local.of("Новый чат"));
                fab.setOnClickListener(v -> {
                    ViewGroup owner=this.list.get();View button=composeButton.get();
                    if(owner!=null&&visible(owner)&&button!=null&&button.isShown()
                            &&button.getRootView()==owner.getRootView())button.performClick();
                });
                Motion.press(fab);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(list,56),Gravity.END|Gravity.BOTTOM);
                params.rightMargin = dp(list,16);params.bottomMargin = dp(list,16);content.addView(fab,params);
            }
            composeVisibility(list);
            int fill = NavBar.mix(page, Accent.colour()|0xff000000, NavBar.bright(page)<.5f?.48f:.22f), on = Md3.ink(fill);
            background(fab, fill, dp(list,18));fabLabel.setTextColor(on);fabIcon.setImageDrawable(SettingsGlyph.make("edit",on));
            View nav = named(root, "on6");
            if (nav != null && nav.isShown()) {
                int[] at = new int[2], base = new int[2];nav.getLocationInWindow(at);content.getLocationInWindow(base);
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fab.getLayoutParams();
                int bottom = Math.max(dp(list,16),content.getHeight()-(at[1]-base[1])+dp(list,16));
                if (params.bottomMargin != bottom) {params.bottomMargin=bottom;fab.setLayoutParams(params);}
            }
        }

        FrameLayout composeHost(ViewGroup list){
            // Stay below the fragment's own container. The activity content is
            // shared with chats, the feed and every other TikTok page.
            Object parent=list.getParent();
            if(!(parent instanceof FrameLayout))return null;
            FrameLayout host=(FrameLayout)parent;
            return host==list.getRootView()||host.getId()==android.R.id.content?null:host;
        }

        void composeVisibility(ViewGroup list){
            if(fab==null)return;
            View button=composeButton.get();
            boolean shown=button!=null&&button.isAttachedToWindow()&&button.isShown()&&button.isEnabled()
                    &&button.getRootView()==list.getRootView()&&visible(list);
            fab.setVisibility(shown?View.VISIBLE:View.GONE);
        }

        void restore() {
            if (header != null) {header.restore();header=null;}
            if (fab != null && fab.getParent() instanceof ViewGroup) ((ViewGroup)fab.getParent()).removeView(fab);
            fab=null;
            composeButton.clear();
            for (WeakReference<View> held : changed) {
                View view=held.get();if(view==null)continue;
                Object saved=view.getTag(SAVED);if(saved instanceof Saved)((Saved)saved).restore(view);
                view.setTag(SAVED,null);
            }
            changed.clear();
            folders.clear();
        }
    }

    /** The four shortcuts live inside the existing filter header, outside RecyclerView's managed children. */
    private static final class Header {
        final Session session;
        final WeakReference<ViewGroup> row;
        final LinearLayout panel, grid;
        final TextView heading;
        final int base;
        final View.OnLayoutChangeListener layout;
        Header(Session session, ViewGroup row) {
            this.session=session;this.row=new WeakReference<>(row);
            Saved saved=session.keep(row);
            base=Math.max(dp(row,44),saved.height>0?saved.height:row.getHeight());
            panel=new LinearLayout(row.getContext());panel.setTag(HEADER,true);panel.setOrientation(LinearLayout.VERTICAL);
            grid=new LinearLayout(row.getContext());grid.setPadding(dp(row,16),dp(row,10),dp(row,16),0);
            panel.addView(grid,new LinearLayout.LayoutParams(-1,dp(row,90)));
            for(int i=0;i<4;i++){
                Tile tile=new Tile(row.getContext(),i,session);session.tiles[i]=tile;
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(row,80),1);
                if(i>0)params.leftMargin=dp(row,8);grid.addView(tile,params);
            }
            heading=label(row.getContext(),Local.of("Чаты"),13);heading.setTypeface(Typeface.create(heading.getTypeface(),Typeface.BOLD));
            heading.setGravity(Gravity.BOTTOM);heading.setPadding(dp(row,20),0,0,dp(row,2));
            panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(row,32)));
            row.addView(panel,new ViewGroup.LayoutParams(-1,dp(row,122)));
            layout=(v,l,t,r,b,ol,ot,or,ob)->place();row.addOnLayoutChangeListener(layout);
        }
        void place(){
            ViewGroup row=this.row.get();if(row==null)return;
            int height=extra(row),width=row.getWidth();
            panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            panel.layout(0,base,width,base+height);
        }
        void update(){
            ViewGroup row=this.row.get();if(row==null)return;
            Saved saved=session.keep(row);int extra=extra(row);size(row,null,base+extra);
            grid.setVisibility(extra>dp(row,32)?View.VISIBLE:View.GONE);
            if(row.getPaddingBottom()!=saved.bottom+extra)row.setPadding(saved.left,saved.top,saved.right,saved.bottom+extra);
            row.setClipChildren(false);row.setClipToPadding(false);heading.setTextColor(session.primary);
            for(int i=0;i<4;i++)session.tiles[i].update();
            place();
        }
        int extra(View row){for(Target target:session.targets)if(target!=null)return dp(row,122);return dp(row,32);}
        void restore(){ViewGroup row=this.row.get();if(row==null)return;row.removeOnLayoutChangeListener(layout);row.removeView(panel);Object saved=row.getTag(SAVED);if(saved instanceof Saved)((Saved)saved).restore(row);}
    }

    private static final class Tile extends FrameLayout {
        final int kind;final Session session;final TextView caption,count;final ImageView icon;
        Tile(Context context,int kind,Session session){
            super(context);this.kind=kind;this.session=session;setContentDescription(Local.of(LABELS[kind]));
            LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER_HORIZONTAL);body.setPadding(0,dp(this,10),0,0);
            icon=new ImageView(context);icon.setPadding(dp(this,10),dp(this,10),dp(this,10),dp(this,10));
            icon.setImageDrawable(SettingsGlyph.make(GLYPHS[kind],Color.WHITE));icon.setBackground(shape(iconColour(),dp(this,14)));
            body.addView(icon,new LinearLayout.LayoutParams(dp(this,40),dp(this,40)));
            caption=label(context,Local.of(LABELS[kind]),11.5f);caption.setGravity(Gravity.CENTER);caption.setMaxLines(1);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams words=new LinearLayout.LayoutParams(-1,-2);words.topMargin=dp(this,6);body.addView(caption,words);addView(body,new FrameLayout.LayoutParams(-1,-1));
            count=label(context,"",10);count.setGravity(Gravity.CENTER);count.setPadding(dp(this,5),0,dp(this,5),0);
            FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,dp(this,16),Gravity.TOP|Gravity.END);badge.topMargin=dp(this,6);badge.rightMargin=dp(this,8);addView(count,badge);
            setOnClickListener(v->{Target target=session.targets[kind];if(target!=null&&!target.open())Popup.show(this,Local.of(LABELS[kind]),Local.of("Откройте входящие ещё раз"),null,null);});Motion.press(this);
        }
        void update(){
            Target target=session.targets[kind];setVisibility(target==null?View.GONE:View.VISIBLE);
            background(icon,iconColour(),dp(this,14));
            background(this,session.surface,dp(this,20));caption.setTextColor(session.muted);
            String unread=target==null?"":target.unread();
            if(!unread.contentEquals(count.getText()))count.setText(unread);
            count.setVisibility(unread.isEmpty()||"0".equals(unread)?View.GONE:View.VISIBLE);
            background(count,session.primary,dp(this,8));count.setTextColor(session.onPrimary);
        }
        int iconColour(){return kind==1?Accent.colour():Accent.swap(COLOURS[kind]);}
    }

    private static final class Target {
        final WeakReference<ViewGroup> list;final WeakReference<View> row;final WeakReference<Object> adapter;final int kind,position,type;
        Target(ViewGroup list,View row,View block,int kind){
            this.list=new WeakReference<>(list);this.row=new WeakReference<>(row);this.kind=kind;
            int at=-1,viewType=-1;Object source=null;
            try{at=((Number)list.getClass().getMethod("getChildAdapterPosition",View.class).invoke(list,block)).intValue();source=list.getClass().getMethod("getAdapter").invoke(list);if(at>=0)viewType=((Number)source.getClass().getMethod("getItemViewType",int.class).invoke(source,at)).intValue();}catch(Throwable ignored){}
            position=at;type=viewType;adapter=new WeakReference<>(source);
        }
        boolean matches(View view){return noticeKind(view)==kind;}
        Object currentAdapter(){
            ViewGroup list=this.list.get();Object known=adapter.get();if(list==null||known==null)return null;
            try{return list.getClass().getMethod("getAdapter").invoke(list)==known?known:null;}catch(Throwable ignored){return null;}
        }
        boolean nativeReady(){View view=row.get();return matches(view)&&view.isAttachedToWindow()&&(adapter.get()==null||currentAdapter()!=null);}
        boolean rebindable(){
            Object source=currentAdapter();if(source==null||type<0)return false;
            try{
                int many=((Number)source.getClass().getMethod("getItemCount").invoke(source)).intValue();
                for(int n=-1;n<Math.min(many,20);n++){
                    int at=n<0?position:n;if(at>=0&&at<many&&((Number)source.getClass().getMethod("getItemViewType",int.class).invoke(source,at)).intValue()==type)return true;
                }
            }catch(Throwable ignored){}
            return false;
        }
        boolean available(){return nativeReady()||rebindable();}
        String unread(){View view=row.get();return matches(view)?unreadCount(named(view,"nhs")):"";}
        boolean open(){
            View view=row.get();if(nativeReady()&&view.hasOnClickListeners())return view.performClick();
            ViewGroup list=this.list.get();if(list==null||type<0)return false;
            try{
                Object adapter=currentAdapter();if(adapter==null)return false;
                int many=((Number)adapter.getClass().getMethod("getItemCount").invoke(adapter)).intValue();
                for(int n=-1;n<Math.min(many,20);n++){
                    int at=n<0?position:n;if(at<0||at>=many)continue;
                    int current=((Number)adapter.getClass().getMethod("getItemViewType",int.class).invoke(adapter,at)).intValue();if(current!=type)continue;
                    Object holder=adapter.getClass().getMethod("createViewHolder",ViewGroup.class,int.class).invoke(adapter,list,current);
                    for(java.lang.reflect.Method method:adapter.getClass().getMethods())if(method.getName().equals("bindViewHolder")&&method.getParameterTypes().length==2){method.invoke(adapter,holder,at);break;}
                    View bound=(View)holder.getClass().getField("itemView").get(holder);View target=named(bound,"uxm");
                    if(matches(target))return target.performClick();
                }
            }catch(Throwable error){Diary.detail("inbox shortcut: "+error);}
            return false;
        }
    }

    private static final class FlatRipple extends RippleDrawable {
        FlatRipple(int accent){super(ColorStateList.valueOf((accent&0xffffff)|0x18000000),new ColorDrawable(Color.TRANSPARENT),new ColorDrawable(Color.WHITE));}
    }

    private static final class Saved {
        final int width,height,minimum,minWidth,left,top,right,bottom,visibility;final float alpha,x,y,scaleX,scaleY;
        final Drawable background;final boolean clipChildren,clipPadding,clipOutline;
        final float textSize;final int textColour,maxLines,maxWidth,maxEms,gravity,layoutGravity;final boolean fontPadding;final Typeface typeface;final int[] margins;
        int[] rules;
        Saved(View view){
            ViewGroup.LayoutParams params=view.getLayoutParams();width=params==null?-2:params.width;height=params==null?-2:params.height;
            minimum=view.getMinimumHeight();left=view.getPaddingLeft();top=view.getPaddingTop();right=view.getPaddingRight();bottom=view.getPaddingBottom();
            minWidth=view.getMinimumWidth();scaleX=view.getScaleX();scaleY=view.getScaleY();
            layoutGravity=params instanceof LinearLayout.LayoutParams?((LinearLayout.LayoutParams)params).gravity:-1;
            visibility=view.getVisibility();alpha=view.getAlpha();x=view.getTranslationX();y=view.getTranslationY();background=view.getBackground();
            clipChildren=view instanceof ViewGroup&&((ViewGroup)view).getClipChildren();clipPadding=view instanceof ViewGroup&&((ViewGroup)view).getClipToPadding();
            clipOutline=view.getClipToOutline();
            if(params instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)params;margins=new int[]{p.leftMargin,p.topMargin,p.rightMargin,p.bottomMargin,p.getMarginStart(),p.getMarginEnd()};}else margins=null;
            TextView text=view instanceof TextView?(TextView)view:null;textSize=text==null?0:text.getTextSize();textColour=text==null?0:text.getCurrentTextColor();maxLines=text==null?1:text.getMaxLines();typeface=text==null?null:text.getTypeface();
            maxWidth=text==null?Integer.MAX_VALUE:text.getMaxWidth();gravity=text==null?0:text.getGravity();fontPadding=text!=null&&text.getIncludeFontPadding();
            maxEms=text==null?-1:text.getMaxEms();
        }
        void restore(View view){
            ViewGroup.LayoutParams params=view.getLayoutParams();if(params!=null){params.width=width;params.height=height;
                if(margins!=null&&params instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)params;p.leftMargin=margins[0];p.topMargin=margins[1];p.rightMargin=margins[2];p.bottomMargin=margins[3];p.setMarginStart(margins[4]);p.setMarginEnd(margins[5]);}
                if(rules!=null&&params instanceof RelativeLayout.LayoutParams){RelativeLayout.LayoutParams p=(RelativeLayout.LayoutParams)params;for(int i=0;i<rules.length;i++){p.removeRule(i);if(rules[i]!=0)p.addRule(i,rules[i]);}}
                if(params instanceof LinearLayout.LayoutParams)((LinearLayout.LayoutParams)params).gravity=layoutGravity;
                view.setLayoutParams(params);
            }
            view.setMinimumHeight(minimum);view.setPadding(left,top,right,bottom);view.setAlpha(alpha);view.setVisibility(visibility);view.setTranslationX(x);view.setTranslationY(y);
            view.setMinimumWidth(minWidth);view.setScaleX(scaleX);view.setScaleY(scaleY);view.setTag(STORY_RING,null);
            Object nativeBack=view.getTag(NATIVE_BG);view.setBackground(nativeBack instanceof Object[]?(Drawable)((Object[])nativeBack)[0]:background);
            view.setTag(NATIVE_BG,null);view.setTag(PAINTED_BG,null);
            if(view instanceof ViewGroup){((ViewGroup)view).setClipChildren(clipChildren);((ViewGroup)view).setClipToPadding(clipPadding);}
            view.setClipToOutline(clipOutline);
            if(view instanceof TextView){TextView text=(TextView)view;text.setTextSize(TypedValue.COMPLEX_UNIT_PX,textSize);Object nativeColour=text.getTag(NATIVE_INK);text.setTextColor(nativeColour instanceof Integer?(Integer)nativeColour:textColour);text.setTag(NATIVE_INK,null);text.setTag(PAINTED_INK,null);text.setMaxLines(maxLines);if(maxWidth>=0)text.setMaxWidth(maxWidth);else text.setMaxEms(maxEms);text.setGravity(gravity);text.setIncludeFontPadding(fontPadding);text.setTypeface(typeface);}
        }
    }

    private static void size(View view,Integer width,Integer height){ViewGroup.LayoutParams p=view.getLayoutParams();if(p==null)return;boolean changed=false;if(width!=null&&p.width!=width){p.width=width;changed=true;}if(height!=null&&p.height!=height){p.height=height;changed=true;}if(changed)view.setLayoutParams(p);}
    private static boolean visible(View view){return ProfileLayout.onScreen(view,view.getRootView());}
    private static int topology(ViewGroup list){int hash=list.getChildCount();for(int i=0;i<list.getChildCount();i++)hash=31*hash+System.identityHashCode(list.getChildAt(i));return hash;}
    private static int noticeKind(View row){
        if(row==null)return -1;
        View title=named(row,"brn"),preview=named(row,"nhq");
        if(words(title).isEmpty())title=named(row,"brj");
        if(words(preview).isEmpty())preview=named(row,"nhr");
        return category(words(title),words(preview));
    }
    private static int dp(View view,float value){return Math.round(value*view.getResources().getDisplayMetrics().density);}
    private static View named(View root,String name){if(root==null)return null;Integer id=ids.get(name);if(id==null){id=root.getResources().getIdentifier(name,"id",root.getContext().getPackageName());if(id!=0)ids.put(name,id);}return id==0?null:root.findViewById(id);}
    private static String unreadCount(View view){
        if(view==null||view.getVisibility()!=View.VISIBLE)return "";
        String text=words(view);if(!text.isEmpty())return text;
        try{
            int count=((Number)view.getClass().getMethod("getCount").invoke(view)).intValue();
            if(count<=0)return "";
            // These native badges draw their text themselves. Copy that text
            // when available, including compact counts and dot-only variants.
            try{
                Object drawable=view.getClass().getField("LL").get(view);
                if(drawable!=null)for(String field:new String[]{"LJII","LJFF"}){
                    try{
                        Object drawn=drawable.getClass().getField(field).get(drawable);
                        if(drawn instanceof CharSequence)return drawn.toString();
                    }catch(ReflectiveOperationException ignored){}
                }
            }catch(ReflectiveOperationException ignored){}
            // Tux calls this maxCount, but it is the number of digits: 2 means 99+.
            int digits=((Number)view.getClass().getMethod("getMaxCount").invoke(view)).intValue();
            if(digits>=1&&digits<=9){
                int limit=1;for(int i=0;i<digits;i++)limit*=10;limit--;
                if(count>limit)return limit+"+";
            }
            return String.valueOf(count);
        }catch(Throwable ignored){return "";}
    }
    private static String words(View view){if(view instanceof TextView){CharSequence text=((TextView)view).getText();return text==null?"":text.toString().trim();}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){String text=words(group.getChildAt(i));if(!text.isEmpty())return text;}}return "";}
    private static HorizontalScrollView scroller(View view,int depth){if(view instanceof HorizontalScrollView)return(HorizontalScrollView)view;if(depth<5&&view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){HorizontalScrollView found=scroller(group.getChildAt(i),depth+1);if(found!=null)return found;}}return null;}
    private static TextView label(Context context,String words,float sp){TextView text=new TextView(context);text.setText(words);text.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);Fonts.apply(text);return text;}
    private static GradientDrawable shape(int colour,float radius){GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(radius);return shape;}
    private static void background(View view,int colour,float radius){Drawable drawable=view.getBackground();if(drawable instanceof RippleDrawable){android.graphics.drawable.LayerDrawable layers=(android.graphics.drawable.LayerDrawable)drawable;for(int i=0;i<layers.getNumberOfLayers();i++)if(layers.getId(i)!=android.R.id.mask){if(layers.getDrawable(i)instanceof GradientDrawable){GradientDrawable shape=(GradientDrawable)layers.getDrawable(i);shape.setColor(colour);shape.setCornerRadius(radius);}else layers.setDrawable(i,shape(colour,radius));return;}}if(drawable instanceof GradientDrawable){GradientDrawable shape=(GradientDrawable)drawable;shape.setColor(colour);shape.setCornerRadius(radius);}else view.setBackground(shape(colour,radius));}

    /** Keep TikTok's selection colour available to ChatFolders after painting its chips. */
    static int nativeInk(TextView text){
        int current=text.getCurrentTextColor();Object painted=text.getTag(PAINTED_INK),nativeColour=text.getTag(NATIVE_INK);
        if(painted instanceof Integer&&((Integer)painted)==current&&nativeColour instanceof Integer)return(Integer)nativeColour;
        text.setTag(NATIVE_INK,current);return current;
    }

    static Drawable nativeBackground(View view){
        Drawable current=view.getBackground();Object painted=view.getTag(PAINTED_BG),nativeBack=view.getTag(NATIVE_BG);
        if(painted==current&&nativeBack instanceof Object[])return(Drawable)((Object[])nativeBack)[0];
        view.setTag(NATIVE_BG,new Object[]{current});return current;
    }

    /** Native tabs may reset their text colours after the slower folder scan.
     * Repair them before that frame is drawn, retaining the native selection signal. */
    static void folderFrame(ViewGroup list){
        if(list==null||!enabled()||!visible(list))return;
        Object held=list.getTag(SESSION);if(!(held instanceof Session))return;
        Session session=(Session)held;
        for(Map.Entry<View,Boolean> entry:session.folders.entrySet()){
            View chip=entry.getKey();if(!(chip instanceof ViewGroup)||!chip.isShown()||!chip.isAttachedToWindow())continue;
            ViewGroup group=(ViewGroup)chip;boolean rebuild=false,first=true;
            for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);if(!(child instanceof TextView))continue;
                TextView text=(TextView)child;Object ink=text.getTag(PAINTED_INK);
                if(!(ink instanceof Integer)){rebuild=true;break;}
                if(text.getCurrentTextColor()!=(Integer)ink){nativeInk(text);text.setTextColor((Integer)ink);}
                if(!first&&!text.getText().toString().trim().isEmpty()
                        &&(!(text.getTag(PAINTED_BG) instanceof Drawable)||text.getBackground()!=text.getTag(PAINTED_BG))){rebuild=true;break;}
                first=false;
            }
            if(rebuild)folder(chip,entry.getValue());
        }
    }

    static void folder(View chip,boolean selected){
        if(!enabled())return;
        if(!(chip instanceof ViewGroup))return;
        Session session=null;View ancestor=chip;
        for(int i=0;i<12&&ancestor!=null;i++){Object held=ancestor.getTag(SESSION);if(held instanceof Session){session=(Session)held;break;}ancestor=ancestor.getParent() instanceof View?(View)ancestor.getParent():null;}
        if(session==null)return;
        session.folders.put(chip,selected);
        session.keep(chip);nativeBackground(chip);
        ViewGroup group=(ViewGroup)chip;
        int page=Md3.behind(chip),ink=Md3.ink(page),fill=selected?NavBar.mix(page,Accent.colour()|0xff000000,.3f):Color.TRANSPARENT;
        if(chip.getBackground()!=chip.getTag(PAINTED_BG))chip.setBackground(shape(fill,dp(chip,10)));
        background(chip,fill,dp(chip,10));
        chip.setTag(PAINTED_BG,chip.getBackground());
        Drawable background=chip.getBackground();if(background instanceof GradientDrawable)((GradientDrawable)background).setStroke(selected?0:Math.max(1,dp(chip,1)),NavBar.mix(page,ink,.28f));
        boolean first=true;
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);if(!(child instanceof TextView))continue;
            TextView text=(TextView)child;session.keep(text);nativeInk(text);
            int colour=ink;
            if(!first&&!text.getText().toString().trim().isEmpty()){
                int primary=NavBar.mix(Accent.colour()|0xff000000,NavBar.bright(page)<.5f?Color.WHITE:page,.56f);
                background(text,primary,dp(chip,8));colour=Md3.ink(primary);
                text.setTag(PAINTED_BG,text.getBackground());
                size(text,ViewGroup.LayoutParams.WRAP_CONTENT,dp(chip,18));
                text.setMinimumHeight(0);text.setMinimumWidth(dp(chip,18));
                text.setPadding(dp(chip,4),0,dp(chip,4),0);text.setGravity(Gravity.CENTER);text.setIncludeFontPadding(false);
                if(Math.abs(text.getTextSize()-11*text.getResources().getDisplayMetrics().scaledDensity)>.5f)text.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                if(text.getMaxLines()!=1)text.setMaxLines(1);
                if(text.getLayoutParams() instanceof LinearLayout.LayoutParams){
                    LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)text.getLayoutParams();
                    if(params.gravity!=Gravity.CENTER_VERTICAL){params.gravity=Gravity.CENTER_VERTICAL;text.setLayoutParams(params);}
                }
            } else if(!first)text.setTag(PAINTED_BG,null);
            if(text.getCurrentTextColor()!=colour)text.setTextColor(colour);text.setTag(PAINTED_INK,colour);first=false;
        }
    }
}
