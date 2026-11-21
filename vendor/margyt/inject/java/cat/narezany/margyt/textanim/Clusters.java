package cat.narezany.margyt.textanim;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ReplacementSpan;
import android.os.SystemClock;
import android.text.InputType;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;


import java.lang.ref.WeakReference;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;

// =========================================================================
// Character clusters & emoji detection
// =========================================================================
public final class Clusters {
    final BreakIterator iterator = BreakIterator.getCharacterInstance();
    String text = "";

    public void reset(String value) {
        text = value;
        iterator.setText(value);
    }

    public int floor(int offset) {
        if (offset <= 0) return 0;
        if (offset >= text.length()) return text.length();
        if (iterator.isBoundary(offset)) return offset;
        int previous = iterator.preceding(offset);
        return previous == BreakIterator.DONE ? offset : previous;
    }

    public int ceil(int offset) {
        if (offset <= 0) return 0;
        if (offset >= text.length()) return text.length();
        if (iterator.isBoundary(offset)) return offset;
        int next = iterator.following(offset);
        return next == BreakIterator.DONE ? offset : next;
    }

    public int next(int offset, int limit) {
        int next = iterator.following(offset);
        return next == BreakIterator.DONE || next > limit ? limit : next;
    }

    public static boolean blank(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean emoji(CharSequence text, int start, int end) {
        if (text instanceof Spanned && ((Spanned) text).getSpans(start, end, ReplacementSpan.class).length > 0) {
            return true;
        }
        for (int i = start; i < end; ) {
            int cp = Character.codePointAt(text, i);
            if ((cp >= 0x1F000 && cp <= 0x1FAFF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0x2B00 && cp <= 0x2BFF)
                || (cp >= 0xE0020 && cp <= 0xE007F)
                || cp == 0x200D || cp == 0xFE0F || cp == 0x20E3) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}
