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
// Motion math & matrix transformation
// =========================================================================
public final class Motion {
    public static final float SLIDE_DP = 8.0f;
    public static final float SCALE_FROM = 0.88f;
    public static final float SPRING_SCALE_FROM = 0.35f;
    static final SparseArray<BlurMaskFilter> FILTERS = new SparseArray<BlurMaskFilter>();

    Motion() {}

    public static float clamp(float value) {
        return value < 0.0f ? 0.0f : Math.min(value, 1.0f);
    }

    public static void transform(Canvas canvas, Config config, float cx, float cy, float progress) {
        if (!config.scale) {
            return;
        }
        canvas.translate(cx, cy);
        float from = config.scaleStart / 100.0f;
        float scale = Math.max(0.05f, from + (1.0f - from) * progress);
        // Квантуем масштаб: глифы одного размера берутся из кэша, а не растеризуются заново каждый кадр
        scale = Math.round(scale * 32.0f) / 32.0f;
        canvas.scale(scale, scale);
        canvas.translate(-cx, -cy);
    }

    // Направление въезда: 0 сверху, 1 снизу, 2 слева, 3 справа
    public static void slide(Canvas canvas, int dir, float dist) {
        switch (dir) {
            case 1:
                canvas.translate(0.0f, dist);
                break;
            case 2:
                canvas.translate(-dist, 0.0f);
                break;
            case 3:
                canvas.translate(dist, 0.0f);
                break;
            default:
                canvas.translate(0.0f, -dist);
                break;
        }
    }

    // Пружина, перелёт и отскок стартуют с маленькой буквы и «выпрыгивают» за 100%
    public static float scaleFrom(int easing) {
        return easing >= 3 ? SPRING_SCALE_FROM : SCALE_FROM;
    }

    public static BlurMaskFilter getBlurMaskFilter(float radius) {
        int key = Math.max(1, Math.round(radius * 2.0f));
        BlurMaskFilter filter = FILTERS.get(key);
        if (filter == null) {
            filter = new BlurMaskFilter(key / 2.0f, BlurMaskFilter.Blur.NORMAL);
            FILTERS.put(key, filter);
        }
        return filter;
    }

    // Число ступеней размытия: радиусы берутся из кэша, а не создаются заново на каждом кадре
    static final int BLUR_STEPS = 8;

    // Рисует букву с размытием: amount 0 — чёткая, 1 — размыта на maxRadius.
    // base — краска текста (её альфа выставляется здесь), tmp — запасная краска для размытой копии.
    public static void drawBlurred(Canvas canvas, Paint base, Paint tmp, CharSequence text,
                                   int start, int end, float x, float y,
                                   float amount, float maxRadius, int alpha, int mode) {
        int step = Math.round(clamp(amount) * BLUR_STEPS);
        if (step <= 0 || maxRadius < 0.5f) {
            base.setAlpha(alpha);
            canvas.drawText(text, start, end, x, y, base);
            return;
        }
        float level = step / (float) BLUR_STEPS;
        float radius = Math.max(0.5f, maxRadius * level);
        tmp.set(base);
        if (mode == Config.BLUR_LIGHT) {
            // Лёгкий режим: два мягких обводочных прохода вместо настоящего размытия — работает везде и быстро
            tmp.setMaskFilter(null);
            tmp.setStyle(Paint.Style.FILL_AND_STROKE);
            tmp.setStrokeJoin(Paint.Join.ROUND);
            tmp.setStrokeWidth(radius);
            tmp.setAlpha((int) (alpha * 0.22f));
            canvas.drawText(text, start, end, x, y, tmp);
            tmp.setStrokeWidth(radius * 0.5f);
            tmp.setAlpha((int) (alpha * 0.30f));
            canvas.drawText(text, start, end, x, y, tmp);
        } else {
            tmp.setMaskFilter(getBlurMaskFilter(radius));
            tmp.setAlpha(alpha);
            canvas.drawText(text, start, end, x, y, tmp);
        }
        // Чёткая копия проступает по мере того, как размытие уходит
        float sharp = (1.0f - level) * (1.0f - level);
        int sharpAlpha = (int) (alpha * sharp);
        if (sharpAlpha > 0) {
            base.setAlpha(sharpAlpha);
            canvas.drawText(text, start, end, x, y, base);
        }
    }

    public static int mixColors(int from, int to, float ratio) {
        ratio = clamp(ratio);
        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = Math.round(a1 + (a2 - a1) * ratio);
        int r = Math.round(r1 + (r2 - r1) * ratio);
        int g = Math.round(g1 + (g2 - g1) * ratio);
        int b = Math.round(b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
