package cat.narezany.margyt;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Adds a sibling of the native row, keeping TikTok's child indices and listeners intact. */
final class ProfileChips {
    private ProfileChips() {}

    private static final int STATE = 0x7e0c05c2, OWN = 0x7e0c05c3;

    private static final class State {
        final LinearLayout outer, mods;
        final View nativeRow;
        final ViewGroup.LayoutParams params;

        State(LinearLayout outer, LinearLayout mods, View nativeRow) {
            this.outer = outer;
            this.mods = mods;
            this.nativeRow = nativeRow;
            params = nativeRow.getLayoutParams();
        }
    }

    static boolean own(View view) { return Boolean.TRUE.equals(view.getTag(OWN)); }

    static HorizontalScrollView empty(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setTag(OWN, Boolean.TRUE);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(new LinearLayout(context), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    static LinearLayout mods(HorizontalScrollView scroll) {
        State state = state(scroll);
        if (state != null) return state.mods;
        View nativeRow = scroll.getChildAt(0);
        if (!(nativeRow instanceof LinearLayout)) return null;
        Context context = scroll.getContext();
        LinearLayout outer = new LinearLayout(context), mods = new LinearLayout(context);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        mods.setOrientation(LinearLayout.HORIZONTAL);
        mods.setGravity(Gravity.CENTER_VERTICAL);
        state = new State(outer, mods, nativeRow);
        scroll.removeView(nativeRow);
        outer.addView(mods, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int height = state.params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : state.params.height;
        outer.addView(nativeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height));
        scroll.addView(outer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setTag(STATE, state);
        return mods;
    }

    static LinearLayout peek(HorizontalScrollView scroll) {
        State state = state(scroll);
        return state == null ? null : state.mods;
    }

    private static State state(HorizontalScrollView scroll) {
        Object held = scroll.getTag(STATE);
        if (!(held instanceof State)) return null;
        State state = (State) held;
        return scroll.getChildAt(0) == state.outer && state.nativeRow.getParent() == state.outer ? state : null;
    }

    static void restore(HorizontalScrollView scroll) {
        State state = state(scroll);
        scroll.setTag(STATE, null);
        if (state == null) return;
        scroll.removeView(state.outer);
        state.outer.removeView(state.nativeRow);
        scroll.addView(state.nativeRow, state.params);
    }
}
