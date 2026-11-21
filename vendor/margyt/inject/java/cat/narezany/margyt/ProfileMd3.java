package cat.narezany.margyt;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The column under a profile's head, laid out the Material 3 way (2.2.2).
 *
 * TikTok's own parts are kept and only put in another order: the head, then
 * (on one's own profile) a row of buttons to edit and share,
 * then the bio and the chips under it as one card, then the highlights. The
 * mod's own plates ("Recommended", the frame) become chips in that row; a tap
 * on one opens the same detail window as a badge. Turned off, everything goes back in
 * the order and the look TikTok gave it.
 */
final class ProfileMd3 {

    private ProfileMd3() {}

    // tag keys of their own: the 0x4D6172.. ones are taken all over the mod
    private static final int ORDER = 0x7e0c05a0, ACTIONS = 0x7e0c05a2, CHIP = 0x7e0c05a3,
            LOOK = 0x7e0c05a4, BUTTON_ROLE = 0x7e0c05a6, TINT = 0x7e0c05a7;

    /** The column of a profile, put in order (or back in TikTok's order when `on` is false). */
    static void column(ViewGroup column, boolean on, int headId, int editId, int handleId, int collegeId, int tabsId) {
        try {
            if (on) {
                // asked on every layout of the screen; arranging moves views, which lays the
                // screen out again: done only when TikTok has changed the column since
                int before = shape(column);
                if (Integer.valueOf(before).equals(column.getTag(ARRANGED))) return;
                ProfileHead.prepare(column, headId);
                arrange(column, headId, editId, handleId, collegeId, tabsId);
                column.setTag(ARRANGED, shape(column));
            } else {
                column.setTag(ARRANGED, null);
                palettes.remove(column);
                undo(column);
                ProfileHead.restore(column);
            }
        } catch (Throwable error) {
            android.util.Log.e("MargyT-md3", "column", error);
            Diary.detail("profile md3: " + error);
        }
    }

    private static final int ARRANGED = 0x4D617291;

    /** What the column is made of: which views, in which order, which of them show, and the colours. */
    private static int shape(ViewGroup column) {
        int sig = column.getChildCount();
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            sig = 31 * sig + System.identityHashCode(child);
            sig = 31 * sig + child.getVisibility();
            if (child instanceof ViewGroup) sig = 31 * sig + ((ViewGroup) child).getChildCount();
        }
        return 31 * sig + Accent.colour();
    }

    private static void arrange(ViewGroup column, int headId, int editId, int handleId, int collegeId, int tabsId) {
        Context c = column.getContext();
        rememberOrder(column);
        View head = null, bio = ProfileParts.find(column, ProfileParts.BIO), highlights = null, actions = null, theirs = null;
        View tools = ProfileParts.find(column, ProfileParts.TOOLS), ownTools = null;
        HorizontalScrollView chips = ProfileParts.scroll(tools);
        if (tools == null) {
            for (int i = 0; i < column.getChildCount(); i++) {
                View child = column.getChildAt(i);
                if (child instanceof HorizontalScrollView && !ProfileChips.own(child)) {
                    tools = child;
                    chips = (HorizontalScrollView) child;
                    break;
                }
            }
        }
        if (chips == null || tools.getVisibility() != View.VISIBLE) {
            if (chips != null) ProfileChips.restore(chips);
            tools = null;
            chips = null;
        }
        List<View> plates = new ArrayList<>(), others = new ArrayList<>();
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            if (child.getTag(ACTIONS) != null) actions = child;
            else if (ProfileChips.own(child)) ownTools = child;
            else if (head == null && (ProfileHead.isHeader(child) || child.findViewById(headId) != null)) head = child;
            else if (ProfileVerdict.plate(child)) plates.add(child);
            else if (child == tools || child == bio) { /* identified by the native component */ }
            else if (bio == null && collegeId != 0 && child.findViewById(collegeId) != null) bio = child;
            else if (theirs == null && buttons(child)) theirs = child;
            else if (highlights == null && holdsList(child)) highlights = child;
            else others.add(child);
        }
        if (head == null) return;
        if (tools == null && !plates.isEmpty()) {
            tools = ownTools != null ? ownTools : ProfileChips.empty(c);
            chips = (HorizontalScrollView) tools;
        }
        if (ownTools != null && ownTools != tools) {
            giveBack(ownTools);
            column.removeView(ownTools);
        }

        // one's own profile: the pencil at the top is there
        View root = column.getRootView();
        View edit = ProfileHead.edit(column);
        if (edit == null) edit = editId == 0 ? null : root.findViewById(editId);
        boolean mine = edit != null && edit.getVisibility() == View.VISIBLE && edit.isAttachedToWindow() && theirs == null;
        // the pencil gives way to the button: kept there, unseen, for the button to touch
        if (mine) { edit.setAlpha(0f); pencil = new java.lang.ref.WeakReference<>(edit); }
        if (mine && actions == null) actions = actions(c, root, editId, handleId, tabsId);
        if (!mine && actions != null) { column.removeView(actions); actions = null; }

        boolean compactPlates = chips != null && chipsFor(chips, plates);

        List<View> want = new ArrayList<>();
        want.add(head);
        if (actions != null) want.add(actions);
        if (theirs != null) want.add(theirs);
        if (bio != null) want.add(bio);
        if (tools != null) want.add(tools);
        want.addAll(plates);
        if (highlights != null) want.add(highlights);
        want.addAll(others);
        boolean same = want.size() == column.getChildCount();
        for (int i = 0; same && i < want.size(); i++) same = column.getChildAt(i) == want.get(i);
        if (!same) {
            // Reorder existing siblings without detaching their native components.
            for (View v : want) {
                if (v.getParent() == null) column.addView(v);
                else if (v.getParent() == column) column.bringChildToFront(v);
            }
        }
        for (View plate : plates) plate.setVisibility(compactPlates ? View.GONE : View.VISIBLE);
        card(bio, tools, chips);
        Palette palette = palettes.get(column);
        if (palette == null) { palette = new Palette(); palettes.put(column, palette); }
        if (palette.bio == null || palette.bio.get() != bio) palette.bio = new java.lang.ref.WeakReference<>(bio);
        if (palette.chips == null || palette.chips.get() != chips) palette.chips = new java.lang.ref.WeakReference<>(chips);
        if (palette.tools == null || palette.tools.get() != tools) palette.tools = new java.lang.ref.WeakReference<>(tools);
        if (palette.actions == null || palette.actions.get() != actions) palette.actions = new java.lang.ref.WeakReference<>(actions);
        recolour(column, bio, tools, chips, actions);
    }

    /** Remember late native arrivals as well, without keeping removed blocks alive on restoration. */
    private static void rememberOrder(ViewGroup column) {
        @SuppressWarnings("unchecked") List<View> was = column.getTag(ORDER) instanceof List
                ? (List<View>) column.getTag(ORDER) : new ArrayList<>();
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            if (child.getTag(ACTIONS) != null || ProfileChips.own(child) || was.contains(child)) continue;
            int at = was.size();
            for (int k = i + 1; k < column.getChildCount(); k++) {
                int next = was.indexOf(column.getChildAt(k));
                if (next >= 0) { at = next; break; }
            }
            was.add(at, child);
        }
        for (int i = was.size() - 1; i >= 0; i--)
            if (was.get(i).getParent() != column) was.remove(i);
        column.setTag(ORDER, was);
    }

    /** All references are weak: a stored palette must not retain a closed profile through a child. */
    private static final class Palette {
        java.lang.ref.WeakReference<View> bio, tools, actions;
        java.lang.ref.WeakReference<HorizontalScrollView> chips;
    }

    private static final java.util.WeakHashMap<ViewGroup, Palette> palettes = new java.util.WeakHashMap<>();

    static void refresh() {
        for (java.util.Map.Entry<ViewGroup, Palette> entry : palettes.entrySet()) {
            ViewGroup column = entry.getKey();
            if (!column.isAttachedToWindow() || !column.isShown()) continue;
            Palette palette = entry.getValue();
            recolour(column, palette.bio.get(), palette.tools.get(), palette.chips.get(), palette.actions.get());
        }
    }

    private static void recolour(View column, View bio, View tools, HorizontalScrollView chips, View actions) {
        int fill = surface(column);
        if (bio != null && bio.getBackground() instanceof Surface) {
            Surface back = (Surface) bio.getBackground();
            surface(bio, fill, back.top, back.bottom);
        }
        if (tools != null && tools.getBackground() instanceof Surface) {
            Surface back = (Surface) tools.getBackground();
            surface(tools, fill, back.top, back.bottom);
        }
        if (actions instanceof ViewGroup) {
            ViewGroup buttons = (ViewGroup) actions;
            for (int i = 0; i < buttons.getChildCount(); i++) {
                View button = buttons.getChildAt(i);
                int buttonFill = Integer.valueOf(1).equals(button.getTag(BUTTON_ROLE))
                        ? tonal(column) : primary(column);
                tintControl(button, buttonFill);
            }
        }
        ViewGroup row = chips == null ? null : ProfileChips.peek(chips);
        if (row != null) {
            for (int i = 0; i < row.getChildCount(); i++) {
                View chip = row.getChildAt(i);
                if (chip.getTag(CHIP) != null) tintControl(chip, tonal(column));
            }
        }
    }

    private static java.lang.ref.WeakReference<View> pencil;

    /** A row of TikTok's own buttons: message, follow and the like, side by side. */
    private static boolean buttons(View view) {
        if (!(view instanceof LinearLayout) || ((LinearLayout) view).getOrientation() != LinearLayout.HORIZONTAL) return false;
        ViewGroup row = (ViewGroup) view;
        if (row.getChildCount() < 2) return false;
        Class<?> kind = row.getChildAt(0).getClass();
        for (int i = 1; i < row.getChildCount(); i++) if (row.getChildAt(i).getClass() != kind) return false;
        return true;
    }

    private static TextView text(View view) {
        TextView found = firstText(view);
        return found != null && found.getText() != null && found.getText().length() > 0 ? found : null;
    }

    private static boolean holdsList(View view) {
        if (isList(view)) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            if (isList(group.getChildAt(i))) return true;
        return false;
    }

    /** A RecyclerView, known by name: the mod is not built against androidx. */
    private static boolean isList(View view) {
        for (Class<?> type = view.getClass(); type != null && type != View.class; type = type.getSuperclass())
            if (type.getName().endsWith("RecyclerView")) return true;
        return false;
    }

    // ------------------------------------------------------------ the card

    /** The bio and the chips under it as one card: rounded on top over the bio, at the bottom under the chips. */
    private static void card(View bio, View tools, HorizontalScrollView chips) {
        View anchor = bio != null ? bio : tools;
        if (anchor == null) return;
        float d = anchor.getResources().getDisplayMetrics().density;
        int fill = surface(anchor);
        if (bio != null) {
            remember(bio);
            fillWidth(bio);
            set(bio, Math.round(12 * d), Math.round(10 * d), Math.round(16 * d), Math.round(14 * d), Math.round(16 * d),
                    tools == null ? Math.round(14 * d) : Math.round(4 * d));
            surface(bio, fill, 24 * d, tools == null ? 24 * d : 0);
        }
        if (tools != null && chips != null) {
            remember(tools);
            fillWidth(tools);
            set(tools, Math.round(12 * d), bio == null ? Math.round(10 * d) : 0, 0, bio == null ? Math.round(12 * d) : Math.round(2 * d),
                    0, Math.round(14 * d));
            surface(tools, fill, bio == null ? 24 * d : 0, 24 * d);
            tools.setClipToOutline(true);
            if (tools != chips) {
                remember(chips);
                fillWidth(chips);
            }
            View row = chips.getChildAt(0);
            if (row != null) {
                row.setPadding(Math.round(16 * d), row.getPaddingTop(), Math.round(16 * d), row.getPaddingBottom());
            }
        }
    }

    private static void fillWidth(View view) {
        ViewGroup.LayoutParams place = view.getLayoutParams();
        if (place != null && place.width != ViewGroup.LayoutParams.MATCH_PARENT) {
            place.width = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(place);
        }
    }

    /** Margins on the sides and top, padding all round. */
    private static void set(View view, int sides, int top, int padSides, int padTop, int padSidesEnd, int padBottom) {
        ViewGroup.LayoutParams given = view.getLayoutParams();
        if (given instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams place = (ViewGroup.MarginLayoutParams) given;
            if (place.leftMargin != sides || place.rightMargin != sides || place.topMargin != top) {
                place.leftMargin = sides;
                place.rightMargin = sides;
                place.topMargin = top;
                view.setLayoutParams(place);
            }
        }
        if (padSides > 0 || padSidesEnd > 0)
            view.setPadding(padSides, padTop, padSidesEnd, padBottom);
        else view.setPadding(view.getPaddingLeft(), padTop, view.getPaddingRight(), padBottom);
    }

    private static final class Surface extends GradientDrawable {
        final float top, bottom;
        int fill;

        Surface(int fill, float top, float bottom) {
            this.fill = fill;
            this.top = top;
            this.bottom = bottom;
            setColor(fill);
            setCornerRadii(new float[] {top, top, top, top, bottom, bottom, bottom, bottom});
        }
    }

    private static void surface(View view, int fill, float top, float bottom) {
        Drawable background = view.getBackground();
        if (background instanceof Surface && ((Surface) background).top == top && ((Surface) background).bottom == bottom) {
            Surface shape = (Surface) background;
            if (shape.fill != fill) { shape.fill = fill; shape.setColor(fill); }
        } else view.setBackground(new Surface(fill, top, bottom));
    }

    private static int surface(View anchor) {
        int accent = Accent.colour() | 0xFF000000;
        int page = Md3.behind(anchor);
        return NavBar.mix(NavBar.mix(page, Md3.ink(page), .05f), accent, .06f);
    }

    private static int tonal(View anchor) {
        int accent = Accent.colour() | 0xFF000000;
        return NavBar.mix(surface(anchor), accent, .24f);
    }

    private static int primary(View anchor) {
        int accent = Accent.colour() | 0xFF000000;
        return NavBar.bright(Md3.behind(anchor)) < .5f
                ? accent : NavBar.mix(surface(anchor), accent, .6f);
    }

    /** Update existing controls too: TikTok can switch its theme without recreating this profile. */
    private static void tintControl(View control, int fill) {
        int ink = Md3.ink(fill);
        // Motion.press wraps the fill in a ripple. Update its content, keeping
        // the ripple and its mask intact, even if another painter changed it.
        tintBackground(control.getBackground(), fill);
        long colours = ((long) fill << 32) | (ink & 0xffffffffL);
        if (Long.valueOf(colours).equals(control.getTag(TINT))) return;
        control.setTag(TINT, colours);
        controlInk(control, ink, control.getTag(BUTTON_ROLE) != null);
    }

    private static void tintBackground(Drawable background, int fill) {
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background;
            android.content.res.ColorStateList held = android.os.Build.VERSION.SDK_INT >= 24 ? shape.getColor() : null;
            if (held == null || held.getDefaultColor() != fill) shape.setColor(fill);
        } else if (background instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) background;
            for (int i = 0; i < layers.getNumberOfLayers(); i++)
                if (layers.getId(i) != android.R.id.mask) tintBackground(layers.getDrawable(i), fill);
        } else if (background instanceof InsetDrawable) {
            tintBackground(((InsetDrawable) background).getDrawable(), fill);
        }
    }

    private static void controlInk(View view, int ink, boolean tintIcons) {
        if (view instanceof TextView) ((TextView) view).setTextColor(ink);
        else if (view instanceof ImageView && tintIcons) ((ImageView) view).setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN);
        else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) controlInk(group.getChildAt(i), ink, tintIcons);
        }
    }

    /** What a view had before it was laid out here, to give back when this is turned off. */
    private static void remember(View view) {
        if (view.getTag(LOOK) != null) return;
        int[] margins = {0, 0, 0, 0};
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams place = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            margins = new int[] {place.leftMargin, place.topMargin, place.rightMargin, place.bottomMargin};
        }
        view.setTag(LOOK, new Object[] {margins, new int[] {view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), view.getPaddingBottom()}, view.getBackground(), view.getClipToOutline(),
                view.getLayoutParams() == null ? ViewGroup.LayoutParams.WRAP_CONTENT : view.getLayoutParams().width});
    }

    private static void giveBack(View view) {
        Object look = view.getTag(LOOK);
        if (!(look instanceof Object[])) return;
        Object[] was = (Object[]) look;
        int[] margins = (int[]) was[0], pads = (int[]) was[1];
        ViewGroup.LayoutParams place = view.getLayoutParams();
        if (place != null) {
            if (place instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginsPlace = (ViewGroup.MarginLayoutParams) place;
                marginsPlace.leftMargin = margins[0]; marginsPlace.topMargin = margins[1];
                marginsPlace.rightMargin = margins[2]; marginsPlace.bottomMargin = margins[3];
            }
            place.width = (Integer) was[4];
            view.setLayoutParams(place);
        }
        view.setPadding(pads[0], pads[1], pads[2], pads[3]);
        view.setBackground((Drawable) was[2]);
        view.setClipToOutline((Boolean) was[3]);
        view.setTag(LOOK, null);
    }

    // ------------------------------------------------------------ the chips

    /** The mod's plates as chips at the start of TikTok's row of chips. */
    private static boolean chipsFor(HorizontalScrollView scroll, List<View> plates) {
        LinearLayout row = ProfileChips.mods(scroll);
        if (row == null) return false;
        // the ones there already, by the plate each stands for
        List<View> had = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i).getTag(CHIP) != null) had.add(row.getChildAt(i));
        boolean same = had.size() == plates.size();
        for (int i = 0; same && i < had.size(); i++) same = had.get(i).getTag(CHIP) == plates.get(i);
        if (same) return true;
        for (View chip : had) row.removeView(chip);
        Context c = row.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int height = Math.round(30 * d);
        for (int i = 0; i < plates.size(); i++) {
            final View plate = plates.get(i);
            View chip = chip(c, plate);
            chip.setTag(CHIP, plate);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            place.rightMargin = Math.round(8 * d);
            place.gravity = Gravity.CENTER_VERTICAL;
            row.addView(chip, i, place);
        }
        return true;
    }

    private static View chip(Context c, final View plate) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(Math.round(10 * d), 0, Math.round(14 * d), 0);
        int fill = tonal(plate);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(12 * d);
        chip.setBackground(shape);
        Drawable picture = picture(plate);
        if (picture != null) {
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(picture.getConstantState() != null ? picture.getConstantState().newDrawable() : picture);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(Math.round(22 * d), Math.round(22 * d));
            size.rightMargin = Math.round(6 * d);
            chip.addView(icon, size);
        }
        TextView words = new TextView(c);
        TextView title = firstText(plate);
        words.setText(title == null ? "MargyT" : title.getText());
        words.setTextColor(Md3.ink(fill));
        words.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        words.setSingleLine(true);
        Fonts.apply(words);
        chip.addView(words);
        chip.setContentDescription(words.getText());
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            // Admin plates already have an action, including marked profiles.
            if (plate.hasOnClickListeners()) plate.performClick();
            else showPlate(c, plate);
        });
        Motion.press(chip);
        return chip;
    }

    /** Read the current plate, so a later server update is reflected in its window too. */
    private static void showPlate(Context context, View plate) {
        List<String> lines = new ArrayList<>();
        plateText(plate, lines);
        String title = lines.isEmpty() ? "MargyT" : lines.remove(0);
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (body.length() > 0) body.append('\n');
            body.append(line);
        }
        Drawable art = picture(plate);
        if (art != null && art.getConstantState() != null) art = art.getConstantState().newDrawable().mutate();
        else art = null;
        Popup.show(plate, title, body.toString(), null, art);
    }

    private static void plateText(View view, List<String> into) {
        if (view instanceof TextView) {
            String text = String.valueOf(((TextView) view).getText()).trim();
            if (!text.isEmpty()) into.add(text);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) plateText(group.getChildAt(i), into);
        }
    }

    private static Drawable picture(View view) {
        if (view instanceof ImageView) return ((ImageView) view).getDrawable();
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Drawable found = picture(((ViewGroup) view).getChildAt(i));
                if (found != null) return found;
            }
        return null;
    }

    private static TextView firstText(View view) {
        if (view instanceof TextView) return (TextView) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                TextView found = firstText(((ViewGroup) view).getChildAt(i));
                if (found != null) return found;
            }
        return null;
    }

    // ------------------------------------------------------------ the buttons

    /** Edit and share, for one's own profile. */
    private static View actions(final Context c, final View root, final int editId, final int handleId, final int tabsId) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(c);
        row.setTag(ACTIONS, Boolean.TRUE);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(40 * d));
        place.leftMargin = place.rightMargin = Math.round(12 * d);
        place.topMargin = Math.round(4 * d);
        row.setLayoutParams(place);
        int primary = primary(root), tonal = tonal(root);
        row.addView(button(c, "edit", Local.of("Изменить"), primary, () -> {
            View column = row.getParent() instanceof View ? (View) row.getParent() : null;
            View edit = ProfileHead.edit(column);
            tap(root, edit != null ? edit : root.findViewById(editId));
        }), weighted(d, 0));
        row.addView(button(c, "share", Local.of("Поделиться"), tonal, () -> {
            View column = row.getParent() instanceof View ? (View) row.getParent() : null;
            View handle = ProfileHead.handle(column);
            if (handle == null) handle = root.findViewById(handleId);
            String name = handle instanceof TextView ? String.valueOf(((TextView) handle).getText()).trim() : "";
            if (!name.startsWith("@")) name = "@" + name;
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "https://www.tiktok.com/" + name);
            c.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }), weighted(d, 8));
        row.getChildAt(0).setTag(BUTTON_ROLE, 0);
        row.getChildAt(1).setTag(BUTTON_ROLE, 1);
        return row;
    }

    private static LinearLayout.LayoutParams weighted(float d, int left) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        place.leftMargin = Math.round(left * d);
        return place;
    }

    private static View button(Context c, String glyph, String words, int fill, final Runnable action) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout button = new LinearLayout(c);
        button.setGravity(Gravity.CENTER);
        button.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(20 * d);
        button.setBackground(shape);
        int ink = Md3.ink(fill);
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(SettingsGlyph.make(glyph, ink));
        button.addView(icon, new LinearLayout.LayoutParams(Math.round(20 * d), Math.round(20 * d)));
        if (words != null) {
            TextView label = new TextView(c);
            label.setText(words);
            label.setTextColor(ink);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            label.setPadding(Math.round(8 * d), 0, 0, 0);
            Fonts.apply(label);
            button.addView(label);
            button.setContentDescription(words);
        }
        button.setOnClickListener(v -> {
            try { action.run(); } catch (Throwable error) { Diary.note("profile button: " + error); }
        });
        Motion.press(button);
        return button;
    }

    /**
     * A real tap on a view: TikTok's pencil answers touches, not clicks, so it
     * is touched the way a finger would, on its icon at the left.
     */
    private static void tap(View root, View target) {
        if (target == null || !target.isShown()) return;
        int[] at = new int[2], base = new int[2];
        target.getLocationInWindow(at);
        root.getLocationInWindow(base);
        float x = at[0] - base[0] + target.getWidth() / 4f, y = at[1] - base[1] + target.getHeight() / 2f;
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP, x, y, 0);
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    /** The view itself if it answers a tap, or the first one inside it that does. */
    private static View clickable(View view) {
        if (view == null) return null;
        if (view.isClickable()) return view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View found = clickable(((ViewGroup) view).getChildAt(i));
                if (found != null) return found;
            }
        return null;
    }

    private static View said(View view, String... words) {
        if (view == null) return null;
        CharSequence d = view.getContentDescription();
        if (d != null) {
            String low = d.toString().toLowerCase(java.util.Locale.ROOT);
            for (String w : words) if (low.contains(w)) return view;
        }
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View found = said(((ViewGroup) view).getChildAt(i), words);
                if (found != null) return found;
            }
        return null;
    }

    // ------------------------------------------------------------ off again

    private static void undo(ViewGroup column) {
        Object order = column.getTag(ORDER);
        if (!(order instanceof List)) return;
        if (pencil != null && pencil.get() != null) pencil.get().setAlpha(1f);
        column.setTag(ORDER, null);
        for (int i = column.getChildCount() - 1; i >= 0; i--) {
            View child = column.getChildAt(i);
            if (child.getTag(ACTIONS) != null || ProfileChips.own(child)) column.removeViewAt(i);
        }
        @SuppressWarnings("unchecked") List<View> was = (List<View>) order;
        for (View v : was) if (v.getParent() == column) column.bringChildToFront(v);
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            giveBack(child);
            HorizontalScrollView scroll = ProfileParts.scroll(child);
            if (scroll != null) {
                ProfileChips.restore(scroll);
                if (scroll != child) giveBack(scroll);
            }
            if (ProfileVerdict.plate(child)) child.setVisibility(View.VISIBLE);
        }
    }
}
