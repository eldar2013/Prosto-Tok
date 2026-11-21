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

public final class Config {
    public static final int STYLE_DUST = 0;
    public static final int STYLE_SNOW = 1;
    public static final int STYLE_SAKURA = 2;
    public static final int STYLE_LETTERS = 3;
    public static final int STYLE_FALL = 4;
    public static final int STYLE_SAND = 5;
    public static final int STYLE_SMOKE = 6;
    public static final int STYLE_BUBBLES = 7;
    public static final int STYLE_SPARKS = 8;
    // Служебные стили для эффектов отправки (в настройках стирания не выбираются)
    public static final int STYLE_CONFETTI = 9;
    public static final int STYLE_HEART = 10;
    public static final int MAX_AMOUNT = 12;

    public static final int GLOW_RAINBOW = 0;
    public static final int GLOW_TIKTOK_PINK = 1;
    public static final int GLOW_TIKTOK_CYAN = 2;
    public static final int GLOW_ACCENT = 3;
    public static final int GLOW_GOLD = 4;
    public static final int GLOW_WHITE = 5;
    public static final int GLOW_NEON_GREEN = 6;
    public static final int GLOW_PURPLE = 7;
    public static final int GLOW_CUSTOM = 8;
    public static final int CARET_CUSTOM = 9;

    public static final int SPARK_TIKTOK = 0;
    public static final int SPARK_NEON = 1;
    public static final int SPARK_FIRE = 2;
    public static final int SPARK_RAINBOW = 3;
    public static final int SPARK_TEXT = 4;
    public static final int SPARK_CUSTOM = 5;

    public static final Config DEFAULT = new Config(
        260, 4, true, true, true, STYLE_DUST, 6, true, 25, true, true, false,
        false, GLOW_RAINBOW, false, SPARK_TIKTOK, true,
        35, 8, 1500, 26, 0, 100, true, 0,
        0, 0, 0, 0, 100, 0, 100, 1000, true, true, true, 100, 100, 0,
        80, 100, 100, 100, 30, 0,
        true, 6, 80, 0, true, 25, 0, 0, 0, 0,
        110,
        true, 60, 50,
        0xFFFE2C55, 0xFF25F4EE
    );

    // Куда наклоняется буква при появлении
    public static final int TILT_LEFT = 0;
    public static final int TILT_RIGHT = 1;
    public static final int TILT_ALTERNATE = 2;
    public static final int TILT_RANDOM = 3;
    // Откуда выезжает буква: 4 — у каждой буквы своя сторона
    public static final int DIR_RANDOM = 4;
    // Качество размытия
    public static final int BLUR_SOFT = 0;
    public static final int BLUR_LIGHT = 1;

    public final int duration;
    public final int easing;
    public final boolean slide;
    public final boolean scale;
    public final boolean ghosts;
    public final boolean particles;
    public final int style;
    public final int amount;
    public final boolean caret;
    public final int caretSpeed;
    public final boolean blink;
    public final boolean liquid;
    public final boolean haptic;

    public final boolean glow;
    public final int glowMode;
    public final boolean gradientText;
    public final int sparkPalette;
    public final int scaleStart;
    public final int slideDp;
    public final int glowMs;
    public final int caretWidth;
    public final int caretColor;
    public final int sparkSize;
    public final boolean aura;
    public final int tilt;
    public final int slideDir;
    public final int squash;
    public final int shake;
    public final int caretShape;
    public final int caretHeight;
    public final int caretGlow;
    public final int caretStretch;
    public final int blinkMs;
    public final boolean caretJump;
    public final boolean caretDive;
    public final boolean caretPulse;
    public final int partSpeed;
    public final int partLife;
    public final int wind;
    public final int smokeDensity;
    public final int smokeRise;
    public final int smokeSpread;
    public final int smokeSwirl;
    public final int smokeHold;
    public final int smokeTone;

    // Размытие при появлении и стирании
    public final boolean blur;
    public final int blurDp;
    public final int blurEnd;
    public final int blurMode;
    public final boolean blurErase;
    // Прозрачность в начале, задержка между буквами, разброс скорости, наклон, точка роста
    public final int fadeStart;
    public final int stagger;
    public final int jitter;
    public final int tiltDir;
    public final int pivot;
    // За сколько мс форма курсора перетекает в другую
    public final int caretMorph;
    // Жидкие «ручки» на границах выделения: включено, растяжение и боковой вынос (в %)
    public final boolean selEffect;
    public final int selStretch;
    public final int selSide;
    // Два своих цвета (ARGB), собранных из ползунков оттенка, насыщенности и яркости
    public final int customA;
    public final int customB;

    public Config(int duration, int easing, boolean slide, boolean scale,
                  boolean ghosts, int style, int amount, boolean caret,
                  int caretSpeed, boolean blink, boolean liquid, boolean haptic,
                  boolean glow, int glowMode, boolean gradientText, int sparkPalette,
                  boolean particles, int scaleStart, int slideDp, int glowMs, int caretWidth,
                  int caretColor, int sparkSize, boolean aura, int tilt,
                  int slideDir, int squash, int shake, int caretShape, int caretHeight,
                  int caretGlow, int caretStretch, int blinkMs, boolean caretJump,
                  boolean caretDive, boolean caretPulse, int partSpeed, int partLife, int wind,
                  int smokeDensity, int smokeRise, int smokeSpread, int smokeSwirl, int smokeHold, int smokeTone,
                  boolean blur, int blurDp, int blurEnd, int blurMode, boolean blurErase,
                  int fadeStart, int stagger, int jitter, int tiltDir, int pivot, int caretMorph,
                  boolean selEffect, int selStretch, int selSide,
                  int customA, int customB) {
        this.duration = clamp(duration, 80, 800);
        this.easing = clamp(easing, 0, Easing.COUNT - 1);
        this.slide = slide;
        this.scale = scale;
        this.ghosts = ghosts;
        this.particles = particles;
        this.style = clamp(style, STYLE_DUST, STYLE_SPARKS);
        this.amount = clamp(amount, 1, MAX_AMOUNT);
        this.caret = caret;
        this.caretSpeed = clamp(caretSpeed, 5, 100);
        this.blink = blink;
        this.liquid = liquid;
        this.haptic = haptic;
        this.glow = glow;
        this.glowMode = clamp(glowMode, 0, 8);
        this.gradientText = gradientText;
        this.sparkPalette = clamp(sparkPalette, 0, 5);
        this.scaleStart = clamp(scaleStart, 20, 250);
        this.slideDp = clamp(slideDp, 0, 40);
        this.glowMs = clamp(glowMs, 200, 3000);
        this.caretWidth = clamp(caretWidth, 10, 60);
        this.caretColor = clamp(caretColor, 0, 9);
        this.sparkSize = clamp(sparkSize, 50, 200);
        this.aura = aura;
        this.tilt = clamp(tilt, 0, 60);
        this.slideDir = clamp(slideDir, 0, DIR_RANDOM);
        this.squash = clamp(squash, 0, 60);
        this.shake = clamp(shake, 0, 12);
        this.caretShape = clamp(caretShape, 0, 3);
        this.caretHeight = clamp(caretHeight, 50, 120);
        this.caretGlow = clamp(caretGlow, 0, 100);
        this.caretStretch = clamp(caretStretch, 0, 200);
        this.blinkMs = clamp(blinkMs, 500, 2500);
        this.caretJump = caretJump;
        this.caretDive = caretDive;
        this.caretPulse = caretPulse;
        this.partSpeed = clamp(partSpeed, 50, 250);
        this.partLife = clamp(partLife, 40, 250);
        this.wind = clamp(wind, -100, 100);
        this.smokeDensity = clamp(smokeDensity, 20, 160);
        this.smokeRise = clamp(smokeRise, 30, 250);
        this.smokeSpread = clamp(smokeSpread, 0, 200);
        this.smokeSwirl = clamp(smokeSwirl, 0, 200);
        this.smokeHold = clamp(smokeHold, 0, 100);
        this.smokeTone = clamp(smokeTone, 0, 3);
        this.blur = blur;
        this.blurDp = clamp(blurDp, 1, 30);
        this.blurEnd = clamp(blurEnd, 20, 100);
        this.blurMode = clamp(blurMode, BLUR_SOFT, BLUR_LIGHT);
        this.blurErase = blurErase;
        this.fadeStart = clamp(fadeStart, 0, 100);
        this.stagger = clamp(stagger, 0, 100);
        this.jitter = clamp(jitter, 0, 60);
        this.tiltDir = clamp(tiltDir, TILT_LEFT, TILT_RANDOM);
        this.pivot = clamp(pivot, 0, 2);
        this.caretMorph = clamp(caretMorph, 20, 500);
        this.selEffect = selEffect;
        this.selStretch = clamp(selStretch, 0, 240);
        this.selSide = clamp(selSide, 0, 240);
        this.customA = customA;
        this.customB = customB;
    }

    public static Config fromPrefs(SharedPreferences prefs) {
        return fromPrefs(prefs, 0);
    }

    // lite: 0 — как настроено, 1 — без размытия, частиц и ауры, 2 — ещё и без движения
    public static Config fromPrefs(SharedPreferences prefs, int lite) {
        if (prefs == null) {
            return DEFAULT;
        }
        return new Config(
            prefs.getInt("duration", 260),
            prefs.getInt("easing", 4),
            lite == 2 ? false : prefs.getBoolean("slide", true),
            lite == 2 ? false : prefs.getBoolean("scale", true),
            prefs.getBoolean("ghosts", true),
            prefs.getInt("style", STYLE_DUST),
            prefs.getInt("amount", 6),
            prefs.getBoolean("caret", true),
            prefs.getInt("caret_speed", 25),
            prefs.getBoolean("blink", true),
            prefs.getBoolean("liquid", true),
            prefs.getBoolean("haptic", false),
            prefs.getBoolean("glow", false),   // MargyT: the rainbow glow is off unless asked for
            prefs.getInt("glow_mode", GLOW_RAINBOW),
            prefs.getBoolean("gradient_text", false),
            prefs.getInt("spark_palette", SPARK_TIKTOK),
            lite > 0 ? false : prefs.getBoolean("particles", true),
            prefs.getInt("scale_start", 35),
            prefs.getInt("slide_dp", 8),
            prefs.getInt("glow_ms", 1500),
            prefs.getInt("caret_width", 26),
            prefs.getInt("caret_color", 0),
            prefs.getInt("spark_size", 100),
            lite > 0 ? false : prefs.getBoolean("aura", true),
            lite == 2 ? 0 : prefs.getInt("tilt", 0),
            prefs.getInt("slide_dir", 0),
            lite == 2 ? 0 : prefs.getInt("squash", 0),
            lite == 2 ? 0 : prefs.getInt("shake", 0),
            prefs.getInt("caret_shape", 0),
            prefs.getInt("caret_height", 100),
            prefs.getInt("caret_glow", 0),
            prefs.getInt("caret_stretch", 100),
            prefs.getInt("blink_ms", 1000),
            prefs.getBoolean("caret_jump", true),
            prefs.getBoolean("caret_dive", true),
            prefs.getBoolean("caret_pulse", true),
            prefs.getInt("part_speed", 100),
            prefs.getInt("part_life", 100),
            prefs.getInt("wind", 0),
            prefs.getInt("smoke_density", 80),
            prefs.getInt("smoke_rise", 100),
            prefs.getInt("smoke_spread", 100),
            prefs.getInt("smoke_swirl", 100),
            prefs.getInt("smoke_hold", 30),
            prefs.getInt("smoke_tone", 0),
            lite > 0 ? false : prefs.getBoolean("blur", true),
            prefs.getInt("blur_dp", 6),
            prefs.getInt("blur_end", 80),
            prefs.getInt("blur_mode", BLUR_SOFT),
            lite > 0 ? false : prefs.getBoolean("blur_erase", true),
            prefs.getInt("fade_start", 25),
            prefs.getInt("stagger", 0),
            prefs.getInt("jitter", 0),
            prefs.getInt("tilt_dir", TILT_LEFT),
            prefs.getInt("pivot", 0),
            prefs.getInt("caret_morph", 110),
            prefs.getBoolean("sel_effect", true),
            prefs.getInt("sel_stretch", 60),
            prefs.getInt("sel_side", 50),
            hsv(prefs.getInt("ca_h", 340), prefs.getInt("ca_s", 85), prefs.getInt("ca_v", 100)),
            hsv(prefs.getInt("cb_h", 180), prefs.getInt("cb_s", 85), prefs.getInt("cb_v", 100))
        );
    }

    public int getGlowColor(long now, int charOffset, int accent, int fallbackColor) {
        if (glowMode == GLOW_RAINBOW) {
            float hue = ((now * 0.16f) + (charOffset * 28.0f)) % 360.0f;
            return rainbow(hue);
        } else if (glowMode == GLOW_TIKTOK_PINK) {
            return 0xFFFE2C55;
        } else if (glowMode == GLOW_TIKTOK_CYAN) {
            return 0xFF25F4EE;
        } else if (glowMode == GLOW_ACCENT) {
            return accent != 0 ? accent : 0xFFFE2C55;
        } else if (glowMode == GLOW_GOLD) {
            return 0xFFFFD700;
        } else if (glowMode == GLOW_WHITE) {
            return 0xFFFFFFFF;
        } else if (glowMode == GLOW_NEON_GREEN) {
            return 0xFF00FF66;
        } else if (glowMode == GLOW_PURPLE) {
            return 0xFFB388FF;
        } else if (glowMode == GLOW_CUSTOM) {
            return customA;
        }
        return fallbackColor;
    }

    public int getCaretColor(int accent, int textColor) {
        switch (caretColor) {
            case 1:
                return textColor;
            case 2:
                return 0xFFFFFFFF;
            case 3:
                return 0xFFB9A8FF;
            case 4:
                return 0xFFFE2C55;
            case 5:
                return 0xFF25F4EE;
            case 6:
                return rainbow((System.currentTimeMillis() * 0.12f) % 360.0f);
            case 7:
                return 0xFFFFD700;
            case 8:
                return 0xFF00FF66;
            case 9:
                return customA;
            default:
                return accent != 0 ? accent : textColor;
        }
    }

    static final float[] HSV = new float[3];

    // Оттенок 0..359, насыщенность и яркость в процентах -> ARGB
    public static int hsv(int h, int s, int v) {
        return Color.HSVToColor(new float[]{clamp(h, 0, 359), clamp(s, 0, 100) / 100.0f, clamp(v, 20, 100) / 100.0f});
    }

    public static int rainbowColor(float hue) {
        return rainbow(hue);
    }

    static int rainbow(float hue) {
        HSV[0] = hue;
        HSV[1] = 0.90f;
        HSV[2] = 1.0f;
        return Color.HSVToColor(HSV);
    }

    public int getSparkColor(Random random, int charColor) {
        if (sparkPalette == SPARK_TIKTOK) {
            return random.nextBoolean() ? 0xFFFE2C55 : 0xFF25F4EE;
        } else if (sparkPalette == SPARK_NEON) {
            return random.nextBoolean() ? 0xFF9C27B0 : 0xFF00E5FF;
        } else if (sparkPalette == SPARK_FIRE) {
            return random.nextBoolean() ? 0xFFFF1744 : 0xFFFFD600;
        } else if (sparkPalette == SPARK_RAINBOW) {
            float hue = random.nextFloat() * 360.0f;
            return rainbow(hue);
        } else if (sparkPalette == SPARK_CUSTOM) {
            return random.nextBoolean() ? customA : customB;
        }
        return charColor;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
