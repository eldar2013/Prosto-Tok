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

public final class Ghosts {
    public static final int LIMIT = 120;
    final ArrayList<Ghost> items = new ArrayList<Ghost>();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void add(Ghost ghost, long now) {
        if (items.size() < LIMIT) {
            ghost.time = now;
            items.add(ghost);
        }
    }

    public void clear() {
        items.clear();
    }

    public boolean draw(EditText view, Canvas canvas, Config config, long now) {
        if (items.isEmpty()) {
            return false;
        }
        TextPaint source = view.getPaint();
        paint.set(source);
        int alpha = source.getAlpha();
        float density = view.getResources().getDisplayMetrics().density;
        float slide = config.slide ? config.slideDp * density : 0.0f;
        float lift = source.getTextSize() / 3.0f;

        for (int i = items.size() - 1; i >= 0; i--) {
            Ghost ghost = items.get(i);
            float raw = (now - ghost.time) / (float) (ghost.fadeMs > 0 ? ghost.fadeMs : config.duration);
            if (raw >= 1.0f) {
                items.remove(i);
                continue;
            }
            float fade = 1.0f - Math.max(0.0f, raw);
            float progress = Easing.apply(config.easing, fade);
            float shown = Motion.clamp(progress);

            int save = canvas.save();
            Motion.slide(canvas, config.slideDir, slide * (1.0f - progress));
            Motion.transform(canvas, config, ghost.x + ghost.width / 2.0f, ghost.baseline - lift, progress);

            int sharp = (int) (fade * alpha);
            if (sharp > 0) {
                if (config.blur && config.blurErase) {
                    // Стёртая буква расплывается, пока исчезает
                    Motion.drawBlurred(canvas, paint, blurPaint, ghost.text, 0, ghost.text.length(),
                        ghost.x, ghost.baseline, Easing.smooth(Motion.clamp(raw)),
                        config.blurDp * density, sharp, config.blurMode);
                } else {
                    paint.setAlpha(sharp);
                    canvas.drawText(ghost.text, ghost.x, ghost.baseline, paint);
                }
            }
            canvas.restoreToCount(save);
        }
        return !items.isEmpty();
    }
}
