package cat.narezany.margyt;

import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

/** Roles supplied by the native component attaching a view, independent of its optional contents. */
public final class ProfileParts {
    private ProfileParts() {}

    static final int BIO = 0x7e0c05c0, TOOLS = 0x7e0c05c1;

    /** Called before HeaderBaseContainerComponent attaches one of its native children. */
    public static void attaching(Object component, View child, ViewGroup parent) {
        if (component == null || parent == null) return;
        String name = component.getClass().getName();
        if (name.startsWith("com.ss.android.ugc.profile.platform.business.header.business.bio.container.ProfileHeaderBio"))
            parent.setTag(BIO, Boolean.TRUE);
        else if (name.startsWith("com.ss.android.ugc.profile.platform.business.header.business.advancedfeature.assemble.ProfileHeaderAdvancedFeature"))
            parent.setTag(TOOLS, Boolean.TRUE);
    }

    static View find(ViewGroup column, int role) {
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            if (contains(child, role, 0)) return child;
        }
        return null;
    }

    private static boolean contains(View view, int role, int depth) {
        if (Boolean.TRUE.equals(view.getTag(role))) return true;
        if (depth < 10 && view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (contains(group.getChildAt(i), role, depth + 1)) return true;
        }
        return false;
    }

    static HorizontalScrollView scroll(View view) { return scroll(view, 0); }

    private static HorizontalScrollView scroll(View view, int depth) {
        if (view instanceof HorizontalScrollView) return (HorizontalScrollView) view;
        if (depth < 6 && view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                HorizontalScrollView found = scroll(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }
}
