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
// Animated letter entrance span with Rainbow / Color Glow (1.5 seconds)
// =========================================================================
public final class AnimatedSpan extends ReplacementSpan {
    public final String text;
    public final long startTime;
    public final Config config;
    public final EditText view;
    public final int charOffset;
    public int at;
    public final Main main;
    int width = -1;
    float sizeTextSize = -1.0f;
    float sizeScaleX = -1.0f;
    float sizeSpacing = -1.0f;
    android.graphics.Typeface sizeFace;
    final float density;
    boolean cleanedUp;
    long lastDraw;
    long virtualElapsed;
    static final int SPAN_INTERMEDIATE = 0x80000;
    static final ArrayList<AnimatedSpan> DONE = new ArrayList<AnimatedSpan>();
    static boolean flushPosted;
    static java.lang.reflect.Method QUIET_REMOVE;
    static final Runnable FLUSH = new Runnable() {
        @Override
        public void run() {
            flushPosted = false;
            ArrayList<AnimatedSpan> batch = new ArrayList<AnimatedSpan>(DONE);
            DONE.clear();
            for (int i = 0; i < batch.size(); i++) {
                AnimatedSpan span = batch.get(i);
                Editable text = span.view.getText();
                if (text != null) {
                    detach(text, span);
                }
            }
        }
    };
    final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Shader gradient;
    float gradientX;
    float gradientWidth;
    int gradientTop;
    int gradientBottom;
    static long lastInvalidate;
    static int lastInvalidateView;

    // Свои параметры каждой буквы: задержка, длительность с разбросом, сторона въезда и наклона
    public final long delayMs;
    final long entranceMs;
    final int slideDir;
    final float tiltSign;

    public AnimatedSpan(Main main, EditText view, String text, Config config, long startTime, int charOffset,
                        long delayMs) {
        this.main = main;
        this.view = view;
        this.text = text;
        this.config = config;
        this.startTime = startTime;
        this.charOffset = charOffset;
        this.delayMs = Math.max(0L, delayMs);
        this.density = view.getResources().getDisplayMetrics().density;

        Random random = new Random(startTime * 31L + charOffset * 131L + text.hashCode());
        float spread = config.jitter / 100.0f;
        float factor = 1.0f + spread * (random.nextFloat() * 2.0f - 1.0f);
        this.entranceMs = Math.max(60L, Math.round(config.duration * factor));
        this.slideDir = config.slideDir == Config.DIR_RANDOM ? random.nextInt(4) : config.slideDir;
        float sign;
        switch (config.tiltDir) {
            case Config.TILT_RIGHT:
                sign = 1.0f;
                break;
            case Config.TILT_ALTERNATE:
                sign = (charOffset & 1) == 0 ? -1.0f : 1.0f;
                break;
            case Config.TILT_RANDOM:
                sign = random.nextBoolean() ? 1.0f : -1.0f;
                break;
            default:
                sign = -1.0f;
                break;
        }
        this.tiltSign = sign;
    }

    // Сколько буква живёт в списке активных: задержка + появление + свечение + запас
    public long lifeMs() {
        return delayMs + entranceMs + (config.glow ? config.glowMs : 0L) + 4000L;
    }

    // Снимаем спан без уведомлений: иначе TextView пересобирает layout на каждую букву (фризы).
    // Ширина обычного текста равна ширине спана, поэтому layout остаётся валидным.
    static void detach(Editable text, AnimatedSpan span) {
        if (text.getSpanStart(span) < 0) return;
        try {
            if (QUIET_REMOVE == null) {
                QUIET_REMOVE = android.text.Spannable.class.getMethod("removeSpan", Object.class, int.class);
            }
            QUIET_REMOVE.invoke(text, span, SPAN_INTERMEDIATE);
        } catch (Throwable error) {
            text.removeSpan(span);
        }
    }

    public boolean finished() {
        return cleanedUp;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            fm.ascent = metrics.ascent;
            fm.descent = metrics.descent;
            fm.top = metrics.top;
            fm.bottom = metrics.bottom;
            fm.leading = metrics.leading;
        }
        // getSize дёргается на каждой отрисовке: меряем заново только если шрифт изменился
        float textSize = paint.getTextSize();
        float scaleX = paint.getTextScaleX();
        float spacing = paint.getLetterSpacing();
        android.graphics.Typeface face = paint.getTypeface();
        if (width < 0 || textSize != sizeTextSize || scaleX != sizeScaleX
            || spacing != sizeSpacing || face != sizeFace) {
            width = (int) Math.ceil(paint.measureText(text, start, end));
            sizeTextSize = textSize;
            sizeScaleX = scaleX;
            sizeSpacing = spacing;
            sizeFace = face;
        }
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        // Собственные часы анимации: если кадры подвисли, буква не «перепрыгивает» в конец,
        // а доигрывает движение и свечение плавно с того места, где остановилась
        if (lastDraw == 0L) {
            virtualElapsed = 0L;
        } else {
            long dt = now - lastDraw;
            virtualElapsed += dt < 0L ? 0L : Math.min(dt, 34L);
        }
        lastDraw = now;
        long elapsed = virtualElapsed - delayMs;
        long entranceMs = this.entranceMs;
        long glowMs = config.glow ? (long) config.glowMs : 0L;
        long totalMs = entranceMs + glowMs;

        if (elapsed < 0L) {
            // Очередь буквы ещё не пришла: место занято, но буква пока не видна
            requestFrame(now);
            return;
        }
        if (elapsed >= totalMs) {
            canvas.drawText(text, start, end, x, y, paint);
            postCleanup();
            return;
        }

        int origAlpha = paint.getAlpha();
        int origColor = paint.getColor();
        int accent = (main != null && config.glow && config.glowMode == Config.GLOW_ACCENT) ? main.accentColor() : 0;
        float w = width > 0 ? width : paint.measureText(text, start, end);

        if (elapsed < entranceMs) {
            // Phase 1: Letter entrance (falling down + gentle scale + fade)
            float progress = elapsed / (float) entranceMs;
            float t = Easing.apply(config.easing, progress);
            float shown = Motion.clamp(t);

            int save = canvas.save();
            try {
                float slide = config.slide ? config.slideDp * density : 0.0f;
                float lift = paint.getTextSize() / 3.0f;
                float cx = x + w * 0.5f;
                // Точка, вокруг которой буква растёт и наклоняется: центр, линия строки или верх
                float cy;
                if (config.pivot == 1) {
                    cy = y;
                } else if (config.pivot == 2) {
                    cy = y - paint.getTextSize() * 0.7f;
                } else {
                    cy = y - lift;
                }

                if (slide > 0.0f) {
                    Motion.slide(canvas, slideDir, slide * (1.0f - t));
                }
                if (config.tilt > 0) {
                    // Буква «встаёт» из наклона; на пружине/перелёте чуть качается за вертикаль
                    canvas.rotate(tiltSign * config.tilt * (1.0f - t), cx, cy);
                }
                if (config.shake > 0) {
                    // Дрожь: быстро затухающее покачивание из стороны в сторону
                    canvas.translate((float) Math.sin(progress * Math.PI * 7.0) * config.shake * density * (1.0f - progress), 0.0f);
                }
                if (config.squash > 0) {
                    // Сжатие: буква падает приплюснутой и «выпрямляется» (на пружине — желе)
                    float sq = config.squash / 100.0f * (1.0f - t);
                    canvas.scale(1.0f - sq * 0.45f, 1.0f + sq, cx, y);
                }
                Motion.transform(canvas, config, cx, cy, t);

                int currentAlpha = (int) (origAlpha * Math.max(config.fadeStart / 100.0f, shown));
                paint.setAlpha(currentAlpha);

                if (config.glow) {
                    int glowColor = config.getGlowColor(now, charOffset, accent, origColor);
                    paint.setColor(glowColor);
                }

                if (config.blur) {
                    // Размытие уходит к моменту blurEnd% от времени появления
                    float sharpT = Motion.clamp(progress / (config.blurEnd / 100.0f));
                    Motion.drawBlurred(canvas, paint, glowPaint, text, start, end, x, y,
                        1.0f - Easing.smooth(sharpT), config.blurDp * density, currentAlpha, config.blurMode);
                } else {
                    canvas.drawText(text, start, end, x, y, paint);
                }
            } finally {
                paint.setAlpha(origAlpha);
                paint.setColor(origColor);
                canvas.restoreToCount(save);
            }
        } else {
            // Phase 2: Letter glowing for 1.5 seconds after falling!
            float glowProgress = (elapsed - entranceMs) / (float) glowMs;
            float glowFade = 1.0f - Motion.clamp(glowProgress);
            int glowColor = config.getGlowColor(now, charOffset, accent, origColor);

            // Аура без BlurMaskFilter (блюр на каждую букву каждый кадр сильно роняет FPS):
            // два дешёвых обводочных прохода с мягкой прозрачностью
            if (config.aura && glowFade > 0.12f) {
                glowPaint.set(paint);
                glowPaint.setColor(glowColor);
                glowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                glowPaint.setStrokeJoin(Paint.Join.ROUND);
                float wide = Math.max(1.0f, 3.2f * density * glowFade);
                glowPaint.setStrokeWidth(wide);
                glowPaint.setAlpha((int) (origAlpha * glowFade * 0.18f));
                canvas.drawText(text, start, end, x, y, glowPaint);
                glowPaint.setStrokeWidth(wide * 0.5f);
                glowPaint.setAlpha((int) (origAlpha * glowFade * 0.30f));
                canvas.drawText(text, start, end, x, y, glowPaint);
            }

            // Draw main letter with glow color or gradient
            int textColor = Motion.mixColors(origColor, glowColor, glowFade);
            paint.setColor(textColor);
            paint.setAlpha(origAlpha);

            if (config.gradientText) {
                Shader oldShader = paint.getShader();
                paint.setShader(gradient(x, top, w, bottom));
                canvas.drawText(text, start, end, x, y, paint);
                paint.setShader(oldShader);
            } else {
                canvas.drawText(text, start, end, x, y, paint);
            }
            paint.setColor(origColor);
        }

        requestFrame(now);
    }

    // Один запрос перерисовки на кадр, а не по одному на каждую букву
    void requestFrame(long now) {
        int viewId = System.identityHashCode(view);
        if (now != lastInvalidate || viewId != lastInvalidateView) {
            lastInvalidate = now;
            lastInvalidateView = viewId;
            view.postInvalidateOnAnimation();
        }
    }

    Shader gradient(float x, int top, float w, int bottom) {
        if (gradient == null || gradientX != x || gradientTop != top
            || gradientWidth != w || gradientBottom != bottom) {
            gradient = new LinearGradient(x, top, x + w, bottom, 0xFFFE2C55, 0xFF25F4EE, Shader.TileMode.CLAMP);
            gradientX = x;
            gradientTop = top;
            gradientWidth = w;
            gradientBottom = bottom;
        }
        return gradient;
    }

    public void cancel() {
        cleanedUp = true;
    }

    void postCleanup() {
        if (cleanedUp) return;
        cleanedUp = true;
        DONE.add(this);
        if (!flushPosted) {
            flushPosted = true;
            view.post(FLUSH);
        }
    }
}
