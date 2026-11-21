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
// Easing curves
// =========================================================================
public final class Easing {
    public static final int COUNT = 8;
    static final int LINEAR = 1;
    static final int CUBIC = 2;
    static final int BACK = 3;
    static final int ELASTIC = 4;
    static final int BOUNCE = 5;
    static final int EXPO = 6;
    static final int S_CURVE = 7;

    Easing() {}

    public static float apply(int curve, float t) {
        t = Motion.clamp(t);
        switch (curve) {
            case LINEAR:
                return t;
            case CUBIC: {
                float inv = 1.0f - t;
                return 1.0f - inv * inv * inv;
            }
            case BACK: {
                float c1 = 1.70158f;
                float inv = t - 1.0f;
                return 1.0f + (c1 + 1.0f) * inv * inv * inv + c1 * inv * inv;
            }
            case ELASTIC:
                if (t <= 0.0f || t >= 1.0f) {
                    return t;
                }
                return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (2.0 * Math.PI / 3.0)) + 1.0);
            case BOUNCE:
                return bounce(t);
            case EXPO:
                // Резкий старт и долгое мягкое замедление
                return t >= 1.0f ? 1.0f : (float) (1.0 - Math.pow(2.0, -10.0 * t));
            case S_CURVE:
                // Медленно начинается, разгоняется и мягко останавливается
                return t * t * (3.0f - 2.0f * t);
            default: {
                float inv = 1.0f - t;
                return 1.0f - inv * inv * inv * inv * inv;
            }
        }
    }

    public static float smooth(float t) {
        t = Motion.clamp(t);
        return t * t * (3.0f - 2.0f * t);
    }

    static float bounce(float t) {
        float n = 7.5625f;
        float d = 2.75f;
        if (t < 1.0f / d) {
            return n * t * t;
        }
        if (t < 2.0f / d) {
            t -= 1.5f / d;
            return n * t * t + 0.75f;
        }
        if (t < 2.5f / d) {
            t -= 2.25f / d;
            return n * t * t + 0.9375f;
        }
        t -= 2.625f / d;
        return n * t * t + 0.984375f;
    }
}
