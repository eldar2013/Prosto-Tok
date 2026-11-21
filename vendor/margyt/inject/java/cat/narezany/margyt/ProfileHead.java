package cat.narezany.margyt;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Joins the old profile's separate avatar and account-info components without copying their data. */
final class ProfileHead {
    private ProfileHead() {}

    private static final int STATE = 0x7e0c05b0, HEADER = 0x7e0c05b1;

    private static final class Slot {
        final View view;
        final ViewGroup parent;
        final ViewGroup.LayoutParams params;
        final int index;

        Slot(View view) {
            this.view = view;
            parent = (ViewGroup) view.getParent();
            params = view.getLayoutParams();
            index = parent.indexOfChild(view);
        }

        void restore() {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            parent.addView(view, Math.min(index, parent.getChildCount()), params);
        }
    }

    private static final class Alignment {
        final View view;
        final int gravity;
        final float textSize;
        final int maxLines;
        final ViewGroup.LayoutParams params;

        Alignment(View view) {
            this.view = view;
            gravity = view instanceof LinearLayout ? ((LinearLayout) view).getGravity() : ((TextView) view).getGravity();
            textSize = view instanceof TextView ? ((TextView) view).getTextSize() : 0;
            maxLines = view instanceof TextView ? ((TextView) view).getMaxLines() : 0;
            params = view.getLayoutParams();
        }

        void restore() {
            if (view instanceof LinearLayout) ((LinearLayout) view).setGravity(gravity);
            else {
                ((TextView) view).setGravity(gravity);
                ((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                ((TextView) view).setMaxLines(maxLines);
            }
            view.setLayoutParams(params);
        }
    }

    private static final class State {
        final Slot avatar, info;
        final LinearLayout header;
        final View edit, handle;
        final List<Alignment> alignments = new ArrayList<>();
        final List<Slot> pieces = new ArrayList<>();

        State(View avatar, View info, LinearLayout header, View edit, View handle) {
            this.avatar = new Slot(avatar);
            this.info = new Slot(info);
            this.header = header;
            this.edit = edit;
            this.handle = handle;
        }
    }

    static boolean isHeader(View view) { return Boolean.TRUE.equals(view.getTag(HEADER)); }

    static View edit(View column) {
        Object state = column == null ? null : column.getTag(STATE);
        return state instanceof State ? ((State) state).edit : null;
    }

    static View handle(View column) {
        Object state = column == null ? null : column.getTag(STATE);
        return state instanceof State ? ((State) state).handle : null;
    }

    static void prepare(ViewGroup column, int modernHeadId) {
        if (column.getTag(STATE) instanceof State || column.findViewById(modernHeadId) != null) return;
        View avatar = null, info = null;
        int avatarId = id(column, "bms"), nameId = id(column, "t75"), statId = id(column, "t7t");
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            if (avatarId != 0 && child.findViewById(avatarId) != null) avatar = child;
            else if (nameId != 0 && statId != 0 && child.findViewById(nameId) != null && child.findViewById(statId) != null) info = child;
        }
        // The server can attach the two components on different frames.
        if (avatar == null || info == null) return;
        float d = column.getResources().getDisplayMetrics().density;
        LinearLayout header = new LinearLayout(column.getContext());
        header.setTag(HEADER, Boolean.TRUE);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        place.setMargins(Math.round(8 * d), 0, Math.round(12 * d), Math.round(4 * d));
        header.setLayoutParams(place);
        State state = new State(avatar, info, header, info.findViewById(id(column, "t9f")), info.findViewById(id(column, "t9_")));
        column.setTag(STATE, state);
        column.removeView(avatar);
        column.removeView(info);
        int width = state.avatar.params.width > 0 ? state.avatar.params.width : Math.round(128 * d);
        LinearLayout.LayoutParams portrait = new LinearLayout.LayoutParams(width, state.avatar.params.height);
        portrait.rightMargin = Math.round(4 * d);
        header.addView(avatar, portrait);
        LinearLayout details = new LinearLayout(column.getContext());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View name = info.findViewById(id(column, "t9e"));
        if (name == null) name = info.findViewById(nameId);
        LinearLayout nameLine = new LinearLayout(column.getContext());
        nameLine.setGravity(Gravity.CENTER_VERTICAL);
        details.addView(nameLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        move(name, nameLine, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), state);
        if (state.edit != null) move(state.edit, nameLine,
                new LinearLayout.LayoutParams(Math.round(42 * d), Math.round(36 * d)), state);
        if (state.handle != null) {
            move(state.handle, details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), state);
            align(state.handle, state.alignments);
        }
        LinearLayout stats = new LinearLayout(column.getContext());
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsPlace = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(44 * d));
        statsPlace.topMargin = Math.round(8 * d);
        details.addView(stats, statsPlace);
        List<View> counters = new ArrayList<>();
        counters(info, statId, counters);
        for (View counter : counters) {
            move(counter, stats, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f), state);
            align(counter, state.alignments);
            fitStat(counter, statId, id(column, "t7s"));
        }
        column.addView(header, Math.min(state.avatar.index, column.getChildCount()));
    }

    private static void move(View view, ViewGroup into, ViewGroup.LayoutParams params, State state) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        Slot slot = new Slot(view);
        state.pieces.add(slot);
        slot.parent.removeView(view);
        into.addView(view, params);
    }

    private static void counters(View view, int numberId, List<View> into) {
        if (view.getId() == numberId && view.getParent() instanceof View) {
            View cell = (View) view.getParent();
            if (!into.contains(cell)) into.add(cell);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) counters(group.getChildAt(i), numberId, into);
        }
    }

    private static void fitStat(View view, int numberId, int labelId) {
        if (view instanceof TextView && (view.getId() == numberId || view.getId() == labelId)) {
            TextView text = (TextView) view;
            text.setMaxLines(1);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, view.getId() == numberId ? 19 : 11);
            // Copy the LayoutParams before changing them: restoration needs the original object.
            ViewGroup.LayoutParams params = text.getLayoutParams();
            try {
                java.lang.reflect.Constructor<?> copy = params.getClass().getConstructor(params.getClass());
                ViewGroup.LayoutParams fitted = (ViewGroup.LayoutParams) copy.newInstance(params);
                fitted.width = ViewGroup.LayoutParams.MATCH_PARENT;
                text.setLayoutParams(fitted);
            } catch (ReflectiveOperationException ignored) {
                // The cell is still constrained by its equal-width column.
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) fitStat(group.getChildAt(i), numberId, labelId);
        }
    }

    private static void align(View view, List<Alignment> saved) {
        if (view instanceof LinearLayout) {
            saved.add(new Alignment(view));
            ((LinearLayout) view).setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        } else if (view instanceof TextView) {
            saved.add(new Alignment(view));
            ((TextView) view).setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) align(group.getChildAt(i), saved);
        }
    }

    static void restore(ViewGroup column) {
        Object held = column.getTag(STATE);
        if (!(held instanceof State)) return;
        State state = (State) held;
        for (Alignment alignment : state.alignments) alignment.restore();
        if (state.edit != null) state.edit.setAlpha(1f);
        if (state.header.getParent() == column) column.removeView(state.header);
        for (int i = state.pieces.size() - 1; i >= 0; i--) state.pieces.get(i).restore();
        // Restore the original sibling order even though the native info block was below the bio.
        state.avatar.restore();
        state.info.restore();
        column.setTag(STATE, null);
    }

    private static int id(View view, String name) {
        return view.getResources().getIdentifier(name, "id", view.getContext().getPackageName());
    }
}
