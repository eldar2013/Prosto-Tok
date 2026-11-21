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
// Floating & liquid caret
// =========================================================================
public final class Caret {
    public static final long IDLE = -1L;
    public static final long NEXT_FRAME = 0L;

    static final float WIDTH_DP = 2.6f;
    static final float STRETCH_DP = 14.0f;
    static final long BLINK_PERIOD = 1000L;
    static final long BLINK_FADE = 200L;
    static final long JUMP_MS = 260L;
    static final long DIVE_MS = 340L;
    static final long PULSE_MS = 190L;
    static final long STRETCH_MS = 420L;

    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    float x = Float.NaN;
    float y;
    float motion;
    long frame;
    int offset = -1;
    int jumpOffset = -1;
    long jumpTime;
    long diveTime;
    long pulseTime;
    long stretchTime;
    float boost;
    long anchor;
    boolean focused;
    long period = BLINK_PERIOD;
    final RectF halo = new RectF();
    // Веса четырёх форм (палочка, прямоугольник, подчёркивание, блок): сумма всегда 1, форма — их смесь
    final float[] shapeW = new float[4];
    final float[] shapeL = new float[4];
    final float[] shapeT = new float[4];
    final float[] shapeR = new float[4];
    final float[] shapeB = new float[4];
    final float[] shapeRad = new float[4];
    static final float[] SHAPE_ALPHA = {1.0f, 1.0f, 1.0f, 0.55f};
    boolean shapeInit;
    // Ширина буквы под курсором: подчёркивание и блок ложатся ровно на неё
    float charW = Float.NaN;
    // Жидкие «ручки» выделения (портировано из оригинального TextAnimationCore)
    static final long SELECTION_MS = 360L;
    int selLo = -1;
    int selHi = -1;
    long selTime;
    float selStartX = Float.NaN;
    float selStartY;
    float selEndX = Float.NaN;
    float selEndY;
    float selStartSnap;
    float selEndSnap;
    long selFrame;
    final float[] selMetricsA = new float[5];
    final float[] selMetricsB = new float[5];

    public void resetSelection() {
        selLo = -1;
        selHi = -1;
        selTime = 0L;
        selStartX = Float.NaN;
        selEndX = Float.NaN;
        selStartSnap = 0.0f;
        selEndSnap = 0.0f;
        selFrame = 0L;
    }

    public void reset() {
        resetSelection();
        shapeInit = false;
        charW = Float.NaN;
        x = Float.NaN;
        motion = 0.0f;
        frame = 0L;
        offset = -1;
        jumpOffset = -1;
        jumpTime = 0L;
        diveTime = 0L;
        pulseTime = 0L;
        stretchTime = 0L;
        boost = 0.0f;
        focused = false;
    }

    public void typed(CharSequence text, int from, int to, long now) {
        boolean jumped = false;
        boolean dived = false;
        for (int i = from; i < to && !(jumped && dived); i++) {
            char c = text.charAt(i);
            if (c == ' ' && !jumped) {
                jumpOffset = i;
                jumpTime = now;
                jumped = true;
            } else if (c == '\n' && !dived) {
                diveTime = now;
                dived = true;
            }
        }
    }

    public void erased(long now) {
        pulseTime = now;
    }

    public void wake(long now) {
        anchor = now;
    }

    public long draw(EditText view, Canvas canvas, Config config, int color, boolean active, long now) {
        if (!active) {
            if (focused) reset();
            return IDLE;
        }
        if (!focused) {
            focused = true;
            anchor = now;
        }
        period = Math.max(500L, config.blinkMs);
        Layout layout = view.getLayout();
        CharSequence text = view.getText();
        int start = view.getSelectionStart();
        if (layout == null || start < 0 || start != view.getSelectionEnd()) {
            return IDLE;
        }
        resetSelection();
        int current = Math.min(start, text.length());
        if (current != offset) {
            int delta = Math.abs(current - offset);
            if (offset >= 0 && delta > 2) {
                boost = Math.min(1.0f, delta / 18.0f);
                stretchTime = now;
            }
            offset = current;
            anchor = now;
        }

        float left = view.getCompoundPaddingLeft();
        float padding = view.getTotalPaddingTop();
        int line = layout.getLineForOffset(current);
        float targetX = left + layout.getPrimaryHorizontal(current);
        float top = padding + layout.getLineTop(line);
        float bottom = padding + layout.getLineBottom(line);
        float targetY = (top + bottom) / 2.0f;
        float lineHeight = Math.max(1.0f, bottom - top);

        float dt = frame == 0L ? 16.0f : Math.min(50.0f, now - frame);
        frame = now;
        float follow = Math.min(1.0f, config.caretSpeed / 100.0f * dt / 16.0f);
        float charTarget = lineHeight * 0.5f;
        if (current < text.length() && text.charAt(current) != '\n' && current + 1 <= text.length()
            && layout.getLineForOffset(current + 1) == line) {
            float cw = Math.abs(layout.getPrimaryHorizontal(current + 1) - layout.getPrimaryHorizontal(current));
            if (cw > 1.0f) charTarget = cw;
        }
        charTarget = Math.max(lineHeight * 0.25f, Math.min(lineHeight * 1.2f, charTarget));
        charW = Float.isNaN(charW) ? charTarget : charW + (charTarget - charW) * Math.min(1.0f, 0.4f * dt / 16.0f);
        if (Float.isNaN(x)) {
            x = targetX;
            y = targetY;
        } else {
            x += (targetX - x) * follow;
            y += (targetY - y) * follow;
        }
        float dx = targetX - x;
        float dy = targetY - y;
        float distance = (float) Math.hypot(dx, dy);
        motion += (distance - motion) * Math.min(1.0f, 0.32f * dt / 16.0f);

        float density = view.getResources().getDisplayMetrics().density;
        float width = config.caretWidth / 10.0f * density;
        float reach = STRETCH_DP * density;
        float vx = x;
        float vy = y;
        float jump = 0.0f;
        float dive = 0.0f;
        float pulse = 0.0f;
        float lunge = 0.0f;

        if (config.liquid) {
            jump = config.caretJump && jumpOffset >= 0 && jumpOffset <= text.length() ? wave(jumpTime, JUMP_MS, now) : 0.0f;
            dive = config.caretDive ? wave(diveTime, DIVE_MS, now) : 0.0f;
            pulse = config.caretPulse ? wave(pulseTime, PULSE_MS, now) : 0.0f;
            lunge = wave(stretchTime, STRETCH_MS, now) * boost;
            if (jump > 0.0f) {
                int jumpLine = layout.getLineForOffset(jumpOffset);
                float jumpBottom = padding + layout.getLineBottom(jumpLine);
                float jumpHeight = jumpBottom - padding - layout.getLineTop(jumpLine);
                vx += (left + layout.getPrimaryHorizontal(jumpOffset) - vx) * jump;
                vy += (jumpBottom - jumpHeight * 0.08f - vy) * jump;
            }
            vy += lineHeight * 0.28f * dive;
        }

        float travel = config.liquid ? Math.min(1.0f, motion / Math.max(width, reach)) : 0.0f;
        float stretch = config.liquid
            ? Math.min(reach * (1.0f + lunge * 0.70f), motion * (0.46f + lunge * 0.28f) + width * (1.4f * jump + 1.8f * lunge))
            : 0.0f;
        stretch *= config.caretStretch / 100.0f;
        float w = width * Math.max(0.55f, 1.0f + travel * 0.22f + jump * 0.28f + dive * 0.10f + lunge * 0.14f - pulse * 0.34f);
        float direction = Math.abs(dx) > 0.1f ? Math.signum(dx) : (targetX >= vx ? 1.0f : -1.0f);
        float l = vx - w / 2.0f - (direction < 0.0f ? stretch : 0.0f);
        float r = vx + w / 2.0f + (direction >= 0.0f ? stretch : 0.0f);
        float inset = lineHeight * (0.08f + travel * 0.18f + jump * 0.10f + dive * 0.08f + pulse * 0.14f
            + (1.0f - config.caretHeight / 100.0f) * 0.5f);
        float shift = (vy - targetY) * 0.12f;
        float t = top + inset + shift;
        float b = bottom - inset + shift;
        float radius = w;

        if (jump > 0.0f) {
            float size = Math.max(width * 2.7f, lineHeight * 0.36f);
            l += (vx - size / 2.0f - l) * jump;
            r += (vx + size / 2.0f - r) * jump;
            t += (vy - size - t) * jump;
            b += (vy - b) * jump;
            radius += (size / 2.0f - radius) * jump;
        }
        // Палочка — это то, что посчитано выше (вместе с жидкой деформацией и прыжком)
        float strL = direction < 0.0f ? stretch : 0.0f;
        float strR = direction >= 0.0f ? stretch : 0.0f;
        float bar = Math.max(2.0f * density, width * 1.1f);
        float body = vx - w / 2.0f;
        shapeL[0] = l; shapeT[0] = t; shapeR[0] = r; shapeB[0] = b; shapeRad[0] = radius;
        // Прямоугольник: чуть шире палочки, углы почти острые
        shapeL[1] = vx - w * 0.75f - strL; shapeT[1] = t; shapeR[1] = vx + w * 0.75f + strR; shapeB[1] = b;
        shapeRad[1] = 2.0f * density;
        // Подчёркивание: полоска внизу строки шириной с букву под курсором
        shapeL[2] = body - strL; shapeT[2] = b - bar; shapeR[2] = body + charW + strR; shapeB[2] = b;
        shapeRad[2] = bar / 2.0f;
        // Блок: полупрозрачный прямоугольник на всю букву
        shapeL[3] = body - strL; shapeT[3] = t; shapeR[3] = body + charW + strR; shapeB[3] = b;
        shapeRad[3] = 3.0f * density;

        // Плавный переход между формами: веса тянутся к выбранной форме по экспоненте
        int want = config.caretShape;
        if (!shapeInit) {
            for (int i = 0; i < 4; i++) shapeW[i] = i == want ? 1.0f : 0.0f;
            shapeInit = true;
        }
        float ease = 1.0f - (float) Math.exp(-dt / Math.max(20.0f, config.caretMorph));
        boolean morphing = false;
        float sum = 0.0f;
        for (int i = 0; i < 4; i++) {
            float goal = i == want ? 1.0f : 0.0f;
            float diff = goal - shapeW[i];
            if (Math.abs(diff) < 0.003f) {
                shapeW[i] = goal;
            } else {
                shapeW[i] += diff * ease;
                morphing = true;
            }
            sum += shapeW[i];
        }
        float mL = 0.0f, mT = 0.0f, mR = 0.0f, mB = 0.0f, mRad = 0.0f, mA = 0.0f;
        for (int i = 0; i < 4; i++) {
            float k = shapeW[i] / sum;
            mL += shapeL[i] * k;
            mT += shapeT[i] * k;
            mR += shapeR[i] * k;
            mB += shapeB[i] * k;
            mRad += shapeRad[i] * k;
            mA += SHAPE_ALPHA[i] * k;
        }
        // Прыжок на пробеле всегда превращает курсор в круглую каплю, какая бы форма ни была выбрана
        float ball = jump > 0.001f ? Math.min(1.0f, jump * 6.0f) : 0.0f;
        if (ball > 0.0f) {
            mL += (l - mL) * ball;
            mT += (t - mT) * ball;
            mR += (r - mR) * ball;
            mB += (b - mB) * ball;
            mRad += (radius - mRad) * ball;
            mA += (1.0f - mA) * ball;
        }
        rect.set(mL, mT, mR, mB);
        radius = mRad;
        float shapeAlpha = mA;

        boolean moving = distance > 0.5f || motion > 0.35f || jump > 0.001f || dive > 0.001f || pulse > 0.001f || lunge > 0.001f || morphing;
        boolean blinking = config.blink && view.hasWindowFocus();
        float alpha = blinking && !moving ? blink(now) : hold(now);
        if (alpha > 0.004f) {
            if (config.caretGlow > 0) {
                // Мягкое свечение: два широких полупрозрачных слоя без BlurMaskFilter
                float g = config.caretGlow / 100.0f;
                paint.setColor(color);
                for (int k = 2; k >= 1; k--) {
                    float grow = k * 2.4f * density * g;
                    halo.set(rect.left - grow, rect.top - grow, rect.right + grow, rect.bottom + grow);
                    paint.setAlpha(Math.round(alpha * shapeAlpha * Color.alpha(color) * 0.16f * g));
                    canvas.drawRoundRect(halo, radius + grow, radius + grow, paint);
                }
            }
            paint.setColor(color);
            paint.setAlpha(Math.round(alpha * shapeAlpha * Color.alpha(color)));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        expire(now);
        boolean settling = Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f || motion > 0.2f
            || morphing || Math.abs(charTarget - charW) > 0.4f
            || jumpTime != 0L || diveTime != 0L || pulseTime != 0L || stretchTime != 0L;
        if (settling || config.caretColor == 6) {
            return NEXT_FRAME;
        }
        return blinking ? untilFade(now) : IDLE;
    }

    // x, верх, низ, центр и высота строки для смещения offset (координаты как у курсора)
    static boolean metrics(EditText view, Layout layout, int offset, float[] out) {
        try {
            int at = Math.max(0, Math.min(offset, view.getText().length()));
            int line = layout.getLineForOffset(at);
            float padding = view.getTotalPaddingTop();
            float top = padding + layout.getLineTop(line);
            float bottom = padding + layout.getLineBottom(line);
            out[0] = view.getCompoundPaddingLeft() + layout.getPrimaryHorizontal(at);
            out[1] = top;
            out[2] = bottom;
            out[3] = (top + bottom) / 2.0f;
            out[4] = Math.max(1.0f, bottom - top);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    // Выделение: на границах вместо системной подсветки-ручек растут две «капли», которые
    // тянутся за движением, прилипают при смене границы и слипаются, когда выделение узкое
    public long drawSelection(EditText view, Canvas canvas, Config config, int color, boolean active, long now) {
        Layout layout = view.getLayout();
        int a = view.getSelectionStart();
        int b = view.getSelectionEnd();
        if (!active || layout == null || a < 0 || b < 0 || a == b) {
            resetSelection();
            return IDLE;
        }
        int length = view.getText().length();
        int lo = Math.min(length, Math.min(a, b));
        int hi = Math.min(length, Math.max(a, b));
        if (lo == hi) {
            resetSelection();
            return IDLE;
        }
        if (!metrics(view, layout, lo, selMetricsA) || !metrics(view, layout, hi, selMetricsB)) {
            return IDLE;
        }
        boolean startMoved = selLo >= 0 && selLo != lo;
        boolean endMoved = selHi >= 0 && selHi != hi;
        if (selLo != lo || selHi != hi) {
            selStartSnap = startMoved ? -1.0f : (endMoved ? 0.35f : 0.0f);
            selEndSnap = endMoved ? 1.0f : (startMoved ? -0.35f : 0.0f);
            selLo = lo;
            selHi = hi;
            selTime = now;
        }
        float dt = selFrame == 0L ? 16.0f : Math.min(50.0f, now - selFrame);
        selFrame = now;
        float t = selTime == 0L ? 1.0f : Math.min(1.0f, (now - selTime) / (float) SELECTION_MS);
        float bell = (float) Math.sin(t * Math.PI);
        if (t >= 1.0f) {
            selStartSnap = 0.0f;
            selEndSnap = 0.0f;
        }
        float density = view.getResources().getDisplayMetrics().density;
        float width = Math.max(1.0f, config.caretWidth / 10.0f * density);
        float stretchK = config.selStretch / 100.0f;
        float sideK = config.selSide / 100.0f;
        float merge = Math.min(1.15f, 0.35f * stretchK + 0.22f * sideK) + 0.88f;
        float follow = Math.max(0.12f, Math.min(0.42f, 0.30f - stretchK * 0.055f));
        // Одинаковая скорость на любой частоте кадров
        follow = 1.0f - (float) Math.pow(1.0f - follow, dt / 16.0f);
        if (Float.isNaN(selStartX)) {
            selStartX = selMetricsA[0];
            selStartY = selMetricsA[3];
        } else {
            selStartX += (selMetricsA[0] - selStartX) * follow;
            selStartY += (selMetricsA[3] - selStartY) * follow;
        }
        if (Float.isNaN(selEndX)) {
            selEndX = selMetricsB[0];
            selEndY = selMetricsB[3];
        } else {
            selEndX += (selMetricsB[0] - selEndX) * follow;
            selEndY += (selMetricsB[3] - selEndY) * follow;
        }
        float gapX = selMetricsB[0] - selMetricsA[0];
        float gapY = selMetricsB[3] - selMetricsA[3];
        float reach = Math.max(18.0f * density, Math.max(selMetricsA[4], selMetricsB[4]) * (sideK * 0.45f + 1.2f));
        float closeness = Math.max(0.0f, 1.0f - Math.min(1.0f, (float) Math.hypot(gapX, gapY) / reach)) * merge;
        float offA = selMetricsA[0] - selStartX;
        float offB = selMetricsB[0] - selEndX;
        handle(canvas, selMetricsA, selStartX, selStartY, color, width, bell, closeness, selStartSnap, -1.0f, offA,
            Math.abs(offA), stretchK, sideK);
        handle(canvas, selMetricsB, selEndX, selEndY, color, width, bell, closeness, selEndSnap, 1.0f, offB,
            Math.abs(offB), stretchK, sideK);
        boolean settling = t < 1.0f || Math.abs(offA) > 0.4f || Math.abs(offB) > 0.4f
            || Math.abs(selStartY - selMetricsA[3]) > 0.4f || Math.abs(selEndY - selMetricsB[3]) > 0.4f;
        return settling ? NEXT_FRAME : IDLE;
    }

    void handle(Canvas canvas, float[] m, float x, float y, int color, float width, float bell,
                        float closeness, float snap, float side, float offset, float absOffset,
                        float stretchK, float sideK) {
        float h = m[4];
        float centerY = (m[1] + m[2]) / 2.0f;
        float near = Math.min(1.0f, Math.abs(offset) / Math.max(width, h * 0.22f));
        float w = Math.max(1.0f, (Math.min(1.0f, absOffset / Math.max(width, h * 0.25f)) * (0.46f * stretchK + 0.34f)
            + 1.08f + bell * 0.18f + (0.28f * sideK + 0.18f) * closeness) * width);
        float ext = Math.min((0.78f * stretchK + 0.34f + 0.34f * sideK) * h,
            (0.72f * stretchK + 0.38f) * absOffset + width * bell * (stretchK * 1.25f + 0.55f));
        float cx = x + side * w * (closeness * (sideK * 0.3f + 0.18f) + 0.14f + near * sideK * 0.22f)
            + bell * snap * width * (0.65f * sideK + 1.25f) + sideK * offset * 0.16f;
        float lean = (y - centerY) * 0.24f;
        float top = m[1] + (0.10f - 0.03f * bell) * h + lean;
        float bottom = m[2] - h * (0.10f - 0.018f * bell) + lean;
        float trail = (near * 0.3f + 0.25f) * ext;
        float half = w / 2.0f;
        float left = cx - half - (side < 0.0f ? ext : trail);
        float right = cx + half + (side <= 0.0f ? trail : ext);
        rect.set(left, top, right, bottom);
        paint.setColor(color);
        paint.setAlpha(Color.alpha(color));
        canvas.drawRoundRect(rect, w, w, paint);
    }

    float hold(long now) {
        anchor = now;
        return 1.0f;
    }

    float blink(long now) {
        if (now < anchor) return hold(now);
        long half = period / 2L;
        long steady = half - BLINK_FADE;
        long phase = (now - anchor) % period;
        if (phase < steady) return 1.0f;
        if (phase < half) return 1.0f - Easing.smooth((phase - steady) / (float) BLINK_FADE);
        if (phase < half + steady) return 0.0f;
        return Easing.smooth((phase - half - steady) / (float) BLINK_FADE);
    }

    long untilFade(long now) {
        long steady = period / 2L - BLINK_FADE;
        long phase = Math.max(0L, now - anchor) % (period / 2L);
        return phase < steady ? steady - phase : NEXT_FRAME;
    }

    void expire(long now) {
        if (jumpTime != 0L && now - jumpTime >= JUMP_MS) {
            jumpTime = 0L;
            jumpOffset = -1;
        }
        if (diveTime != 0L && now - diveTime >= DIVE_MS) diveTime = 0L;
        if (pulseTime != 0L && now - pulseTime >= PULSE_MS) pulseTime = 0L;
        if (stretchTime != 0L && now - stretchTime >= STRETCH_MS) {
            stretchTime = 0L;
            boost = 0.0f;
        }
    }

    static float wave(long start, long duration, long now) {
        if (start == 0L) return 0.0f;
        float progress = (now - start) / (float) duration;
        return progress >= 1.0f ? 0.0f : (float) Math.sin(Math.max(0.0f, progress) * Math.PI);
    }
}
