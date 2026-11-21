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
// Particle simulation with Two-Color sparks & Palettes
// =========================================================================
public final class Particle {
    static final float TWO_PI = (float) (Math.PI * 2.0);
    static final float SPREAD_X = 1.3f;
    static final float SPREAD_Y = 1.25f;
    static final int[] SAKURA = {0xFFFFD7E4, 0xFFFFC2D6, 0xFFFFAECB, 0xFFFB98BC};

    public float x;
    public float y;
    public float vx;
    public float vy;
    public float life;
    public float size;
    public float rotation;
    public float stretch;
    public float flicker;
    public float flickerPhase;
    public float time;
    public int color;
    public int style;
    public String text;
    public float delay;
    public float expand;

    // Полёт к точке (вихрь в кнопку отправки)
    boolean homing;
    float tx;
    float ty;
    float pullK;
    float swirlK;
    float spinDir;
    float arrive;

    // Полёт по кривой к точке назначения (тип полёта при отправке)
    boolean gliding;
    float gx0;
    float gy0;
    float gx1;
    float gy1;
    float glideFrames;
    int glideCurve;
    float glideArc;
    float glideTurns;
    float glideDir;

    float decay;
    float gravity;
    float drag;
    float wind;
    float spin;
    float spinDamping;
    float wobble;
    float wobblePhase;
    float wobbleSpeed;
    float turbulence;
    float turbulenceFx;
    float turbulenceFy;
    float turbulencePx;
    float turbulencePy;
    boolean bounded;
    float minX;
    float maxX;
    float maxY;
    float restitution;

    public void init(Random random, int style, float x, float y, int color, float textSize, String text) {
        this.style = style;
        homing = false;
        gliding = false;
        this.x = x;
        this.y = y;
        this.color = color;
        this.text = text;
        life = 1.0f;
        delay = 0.0f;
        expand = 0.0f;
        time = 0.0f;
        rotation = random.nextFloat() * 360.0f;
        stretch = 1.0f;
        flicker = 0.0f;
        flickerPhase = random.nextFloat() * TWO_PI;
        wind = 0.0f;
        spinDamping = 1.0f;
        wobble = 0.0f;
        wobblePhase = random.nextFloat() * TWO_PI;
        wobbleSpeed = 0.08f;
        turbulence = 0.0f;
        turbulencePx = random.nextFloat() * TWO_PI;
        turbulencePy = random.nextFloat() * TWO_PI;
        bounded = false;
        float base = Math.max(1.0f, textSize);

        switch (style) {
            case Config.STYLE_SNOW:
                snow(random, base);
                break;
            case Config.STYLE_SAKURA:
                sakura(random, base);
                break;
            case Config.STYLE_LETTERS:
                letters(random, base);
                break;
            case Config.STYLE_FALL:
                fall(random, base);
                break;
            case Config.STYLE_SAND:
                sand(random, base);
                break;
            case Config.STYLE_SMOKE:
                smoke(random, base);
                break;
            case Config.STYLE_BUBBLES:
                bubbles(random, base);
                break;
            case Config.STYLE_SPARKS:
                sparks(random, base);
                break;
            case Config.STYLE_CONFETTI:
                confetti(random, base);
                break;
            case Config.STYLE_HEART:
                heart(random, base);
                break;
            default:
                dust(random, base);
                break;
        }
    }

    // Конфетти: плоские листочки, кувыркаются и падают
    void confetti(Random random, float base) {
        vx = range(random, -1.0f, 1.0f);
        vy = -range(random, 2.0f, 4.0f);
        size = Math.max(4.0f, range(random, base / 4.2f, base / 2.4f));
        decay = range(random, 0.008f, 0.014f);
        gravity = 0.12f;
        drag = 0.035f;
        spin = range(random, -12.0f, 12.0f);
        spinDamping = 0.99f;
        wobble = range(random, 0.2f, 0.7f);
        wobbleSpeed = range(random, 0.08f, 0.16f);
        turbulence = 0.02f;
        turbulenceFx = range(random, 0.06f, 0.14f);
        turbulenceFy = range(random, 0.05f, 0.12f);
    }

    // Сердечки: всплывают, покачиваясь
    void heart(Random random, float base) {
        vx = range(random, -0.9f, 0.9f);
        vy = -range(random, 1.2f, 3.2f);
        size = Math.max(5.0f, range(random, base / 4.6f, base / 2.7f));
        decay = range(random, 0.009f, 0.016f);
        gravity = -0.004f;
        drag = 0.028f;
        rotation = range(random, -25.0f, 25.0f);
        spin = range(random, -1.6f, 1.6f);
        spinDamping = 0.99f;
        wobble = range(random, 0.4f, 1.1f);
        wobbleSpeed = range(random, 0.05f, 0.10f);
        turbulence = 0.02f;
        turbulenceFx = range(random, 0.05f, 0.12f);
        turbulenceFy = range(random, 0.04f, 0.10f);
    }

    // Ручная настройка полёта для эффектов отправки
    public void launch(float vx, float vy, float gravity, float drag, float decay, float turbulence, float stretch) {
        this.vx = vx;
        this.vy = vy;
        this.gravity = gravity;
        this.drag = drag;
        this.decay = decay;
        if (turbulence >= 0.0f) this.turbulence = turbulence;
        if (stretch > 0.0f && style == Config.STYLE_SPARKS) this.stretch = stretch;
    }

    // Лететь по спирали к точке (tx, ty): pull — скорость, swirl — закрутка, dir ±1 — сторона вращения
    public void home(float tx, float ty, float pull, float swirl, float dir, float arrive) {
        this.homing = true;
        this.tx = tx;
        this.ty = ty;
        this.pullK = pull;
        this.swirlK = swirl;
        this.spinDir = dir;
        this.arrive = Math.max(2.0f, arrive);
        this.vx = 0.0f;
        this.vy = 0.0f;
        this.gravity = 0.0f;
        this.drag = 0.0f;
        this.decay = 0.004f;
        this.wind = 0.0f;
    }

    // Общие ручки: скорость разлёта, время жизни и ветер
    public void tune(float speed, float life, float windAdd) {
        vx *= speed;
        vy *= speed;
        decay /= Math.max(0.2f, life);
        wind += windAdd * 0.00018f;
    }

    public void push(float outwardX, float outwardY) {
        vx += outwardX * 1.5f;
        vy += outwardY * 0.5f;
    }

    public void bound(float left, float right, float bottom) {
        minX = left;
        maxX = right;
        maxY = bottom;
        bounded = true;
    }

    public float alpha() {
        float value = Motion.clamp(life);
        if (style == Config.STYLE_FALL) {
            return Math.min(1.0f, value / 0.2f);
        }
        return value * value * (3.0f - 2.0f * value);
    }

    // Частица летит по спирали к точке (tx, ty) и гаснет у неё.
    // Скорость задаётся положением цели, а не догоняет её с запаздыванием: при запаздывании
    // закрученная частица разворачивалась наружу и улетала от кнопки. Шаг ограничен долей
    // расстояния (с учётом закрутки), поэтому частица не перелетает точку, а закрутка у самой
    // кнопки стихает и частица входит в неё прямо.
    boolean stepHoming(float scale) {
        float dx = tx - x;
        float dy = ty - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= arrive) return false;
        time += scale;
        float nx = dx / dist;
        float ny = dy / dist;
        float swirl = swirlK * Math.min(1.0f, dist / (arrive * 10.0f));
        float ramp = Math.min(1.0f, time / 18.0f);
        float speed = pullK * (1.0f + time * 0.03f);
        speed = Math.min(speed, dist * 0.4f / (scale * (float) Math.sqrt(1.0f + swirl * swirl)));
        vx = (nx - ny * spinDir * swirl) * speed * ramp;
        vy = (ny + nx * spinDir * swirl) * speed * ramp;
        x += vx * scale;
        y += vy * scale;
        rotation += spin * scale;
        life -= decay * scale;
        // у самой точки частица гаснет и уменьшается
        float fade = Math.min(1.0f, (dist - arrive) / (arrive * 5.0f));
        if (life > fade) life = fade;
        return life > 0.0f;
    }

    // Частица плавно летит из текущей точки в (x1, y1) за frames шагов.
    // curve — кривая Easing, arc — изгиб пути, turns > 0 — полёт спиралью (вихрь), dir — сторона закрутки
    public void glide(float x1, float y1, float frames, int curve, float arc, float turns, float dir) {
        gliding = true;
        gx0 = x;
        gy0 = y;
        gx1 = x1;
        gy1 = y1;
        glideFrames = Math.max(1.0f, frames);
        glideCurve = curve;
        glideArc = arc;
        glideTurns = turns;
        glideDir = dir;
        vx = 0.0f;
        vy = 0.0f;
        gravity = 0.0f;
        drag = 0.0f;
        wind = 0.0f;
    }

    boolean stepGliding(float scale) {
        time += scale;
        float prevX = x;
        float prevY = y;
        float t = Math.min(1.0f, time / glideFrames);
        if (glideTurns > 0.0f) {
            // Спираль: вектор от точки назначения поворачивается и плавно сжимается
            float k = Easing.smooth(t);
            float angle = glideDir * glideTurns * 6.2831853f * k;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float rx = gx0 - gx1;
            float ry = gy0 - gy1;
            x = gx1 + (rx * cos - ry * sin) * (1.0f - k);
            y = gy1 + (rx * sin + ry * cos) * (1.0f - k);
        } else {
            float e = Easing.apply(glideCurve, t);
            float dx = gx1 - gx0;
            float dy = gy1 - gy0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float px = len > 0.0f ? -dy / len : 0.0f;
            float py = len > 0.0f ? dx / len : 0.0f;
            float bend = glideArc * len * 0.3f * (float) Math.sin(Math.PI * t);
            x = gx0 + dx * e + px * bend;
            y = gy0 + dy * e + py * bend;
        }
        // скорость нужна рисовальщикам: искры ориентируются по направлению полёта
        vx = (x - prevX) / scale;
        vy = (y - prevY) / scale;
        rotation += spin * scale;
        // частица гаснет в последней трети пути
        life = 1.0f - Easing.smooth(Math.max(0.0f, (t - 0.6f) / 0.4f));
        return t < 1.0f;
    }

    public boolean step(float scale) {
        if (delay > 0.0f) {
            // зерно ещё держит форму буквы и ждёт своей очереди
            delay -= scale;
            return true;
        }
        if (homing) {
            return stepHoming(scale);
        }
        if (gliding) {
            return stepGliding(scale);
        }
        time += scale;
        wobblePhase += wobbleSpeed * scale;
        vx += wind * scale;
        if (turbulence != 0.0f) {
            vx += (float) Math.sin(time * turbulenceFx + turbulencePx) * turbulence * scale;
            vy += (float) Math.cos(time * turbulenceFy + turbulencePy) * turbulence * 0.6f * scale;
        }
        float sway = wobble == 0.0f ? 0.0f : (float) Math.sin(wobblePhase) * wobble;
        x += (vx + sway) * scale;
        y += vy * scale;
        if (style == Config.STYLE_SAKURA && wobble != 0.0f) {
            y += (float) Math.cos(wobblePhase * 2.0f) * wobble * 0.18f * scale;
            rotation += (float) Math.cos(wobblePhase) * 1.9f * scale;
        }
        vy += gravity * scale;
        float damping = (float) Math.pow(1.0f - Math.min(0.5f, drag), scale);
        vx *= damping;
        vy *= 1.0f - (1.0f - damping) * 0.55f;
        rotation += spin * scale;
        if (spinDamping < 1.0f) {
            spin *= (float) Math.pow(spinDamping, scale);
        }
        if (bounded && maxX > minX) {
            collide(scale);
        }
        life -= decay * scale;
        return life > 0.0f;
    }

    void collide(float scale) {
        float half = size * 0.32f;
        if (x - half < minX) {
            x = minX + half;
            if (vx < 0.0f) vx = -vx * restitution;
        } else if (x + half > maxX) {
            x = maxX - half;
            if (vx > 0.0f) vx = -vx * restitution;
        }
        if (y + half <= maxY) return;
        y = maxY - half;
        if (vy > 0.0f) {
            vy = vy < gravity * 2.5f ? 0.0f : -vy * restitution;
        }
        vx *= (float) Math.pow(0.965f, scale);
        float roll = (float) Math.toDegrees(vx / Math.max(1.0f, half));
        spin += (roll - spin) * Math.min(1.0f, 0.25f * scale);
    }

    void dust(Random random, float base) {
        float angle = random.nextFloat() * TWO_PI;
        float force = range(random, 0.6f, 2.6f);
        vx = (float) Math.cos(angle) * force * SPREAD_X;
        vy = (float) Math.sin(angle) * force * SPREAD_Y * 0.6f - range(random, 0.5f, 1.3f);
        size = Math.max(1.5f, range(random, base / 11.0f, base / 5.5f));
        decay = range(random, 0.014f, 0.032f);
        gravity = 0.012f;
        drag = 0.050f;
        wind = range(random, -0.010f, 0.010f);
        turbulence = 0.055f;
        turbulenceFx = range(random, 0.11f, 0.23f);
        turbulenceFy = range(random, 0.09f, 0.19f);
        flicker = range(random, 0.25f, 0.50f);
        spin = range(random, -4.0f, 4.0f);
    }

    void snow(Random random, float base) {
        vx = range(random, -0.8f, 0.8f) * SPREAD_X;
        vy = range(random, -0.6f, 0.25f) * SPREAD_Y;
        size = Math.max(2.0f, range(random, base / 9.0f, base / 5.5f));
        decay = range(random, 0.007f, 0.015f);
        gravity = 0.014f;
        drag = 0.025f;
        wind = range(random, 0.004f, 0.014f);
        wobble = range(random, 0.45f, 1.30f);
        wobbleSpeed = range(random, 0.050f, 0.090f);
        spin = range(random, -2.5f, 2.5f);
        spinDamping = 0.998f;
        turbulence = 0.012f;
        turbulenceFx = range(random, 0.06f, 0.14f);
        turbulenceFy = range(random, 0.05f, 0.12f);
    }

    void sakura(Random random, float base) {
        color = SAKURA[random.nextInt(SAKURA.length)];
        vx = range(random, -0.9f, 1.2f) * SPREAD_X * 0.8f + range(random, 0.05f, 0.30f);
        vy = range(random, -1.0f, 0.15f) * SPREAD_Y * 0.55f;
        size = Math.max(2.6f, range(random, base / 6.8f, base / 4.4f));
        decay = range(random, 0.006f, 0.013f);
        gravity = 0.030f;
        drag = 0.030f;
        wind = range(random, 0.004f, 0.014f);
        wobble = range(random, 0.8f, 1.8f);
        wobbleSpeed = range(random, 0.045f, 0.075f);
        spin = range(random, -2.2f, 2.2f);
        spinDamping = 0.985f;
        stretch = range(random, 1.45f, 2.0f);
        turbulence = 0.008f;
        turbulenceFx = range(random, 0.05f, 0.12f);
        turbulenceFy = range(random, 0.04f, 0.10f);
    }

    void letters(Random random, float base) {
        float angle = (float) (-Math.PI / 2.0) + range(random, -1.1f, 1.1f);
        float force = range(random, 1.6f, 3.8f);
        vx = (float) Math.cos(angle) * force * SPREAD_X;
        vy = (float) Math.sin(angle) * force * SPREAD_Y;
        size = Math.max(4.0f, base * range(random, 0.50f, 0.72f));
        decay = range(random, 0.014f, 0.028f);
        gravity = 0.09f;
        drag = 0.015f;
        wind = range(random, -0.010f, 0.010f);
        spin = range(random, -14.0f, 14.0f);
        spinDamping = 0.992f;
        turbulence = 0.008f;
        turbulenceFx = range(random, 0.08f, 0.16f);
        turbulenceFy = range(random, 0.07f, 0.14f);
    }

    void fall(Random random, float base) {
        vx = range(random, -0.05f, 0.05f) * base * 1.4f;
        vy = -range(random, 0.09f, 0.18f) * base;
        size = Math.max(6.0f, base);
        decay = range(random, 0.0045f, 0.0065f);
        gravity = base * 0.011f;
        drag = 0.012f;
        rotation = 0.0f;
        spin = range(random, -5.0f, 5.0f);
        spinDamping = 0.995f;
        restitution = range(random, 0.35f, 0.50f);
        bounded = true;
    }

    // Песок: зёрна несёт лёгким ветром вправо и вверх, как рассыпающуюся фигуру
    void sand(Random random, float base) {
        vx = range(random, 0.5f, 2.2f);
        vy = range(random, -1.3f, 0.35f);
        size = Math.max(1.2f, base);
        decay = range(random, 0.018f, 0.038f);
        gravity = 0.006f;
        drag = 0.035f;
        wind = range(random, 0.004f, 0.016f);
        turbulence = 0.06f;
        turbulenceFx = range(random, 0.10f, 0.22f);
        turbulenceFy = range(random, 0.09f, 0.18f);
        spin = range(random, -3.0f, 3.0f);
    }

    // Дым: зёрна стоят в форме буквы, затем отрываются, поднимаются, клубятся и тают
    void smoke(Random random, float base) {
        vx = range(random, -0.30f, 0.30f);
        vy = -range(random, 0.35f, 1.10f);
        size = Math.max(1.5f, base);
        decay = range(random, 0.011f, 0.019f);
        gravity = -0.0035f;
        drag = 0.040f;
        wind = range(random, -0.003f, 0.006f);
        wobble = 0.45f;
        wobbleSpeed = range(random, 0.05f, 0.10f);
        turbulence = 0.09f;
        turbulenceFx = range(random, 0.05f, 0.12f);
        turbulenceFy = range(random, 0.04f, 0.10f);
    }

    // Настройки дыма: подъём, разрастание клубов и завихрения
    public void smokeTune(float rise, float spread, float swirl) {
        vy *= rise;
        gravity *= rise;
        expand = spread;
        turbulence *= swirl;
        wobble *= swirl;
    }

    // Пузыри: лёгкие пустые шарики всплывают, покачиваясь
    void bubbles(Random random, float base) {
        vx = range(random, -0.35f, 0.35f);
        vy = -range(random, 0.55f, 1.40f);
        size = Math.max(4.0f, range(random, base / 5.5f, base / 2.8f));
        decay = range(random, 0.010f, 0.018f);
        gravity = -0.004f;
        drag = 0.030f;
        wobble = range(random, 0.35f, 1.00f);
        wobbleSpeed = range(random, 0.06f, 0.11f);
        turbulence = 0.02f;
        turbulenceFx = range(random, 0.05f, 0.12f);
        turbulenceFy = range(random, 0.04f, 0.10f);
    }

    // Искры (из оригинального TextAnimationCore): быстрые вытянутые штрихи, летят вдоль скорости
    void sparks(Random random, float base) {
        float angle = random.nextFloat() * TWO_PI;
        float force = range(random, 2.2f, 5.9f);
        vx = (float) Math.cos(angle) * force * SPREAD_X * 0.8f;
        vy = (float) Math.sin(angle) * force * SPREAD_Y * 0.8f - 1.4f;
        size = Math.max(1.5f, range(random, base / 10.0f, base / 10.0f + base / 9.0f));
        decay = range(random, 0.024f, 0.056f);
        gravity = 0.045f;
        drag = 0.018f;
        wind = range(random, -0.009f, 0.009f);
        spin = range(random, -12.0f, 12.0f);
        stretch = range(random, 1.8f, 3.4f);
        flicker = range(random, 0.35f, 0.60f);
    }

    static float range(Random random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }
}
