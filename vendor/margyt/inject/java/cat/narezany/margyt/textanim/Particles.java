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

public final class Particles {
    static final int LIMIT = 1400;
    static final int MASK_PAD = 4;
    static final int CONTOURS = 16;
    static final int RIB = 0xFFE0679A;
    static final int[] SEND_COLORS = {0xFFFE2C55, 0xFF25F4EE, 0xFFFFFFFF};
    static final float SEND_SPREAD = 1.7f;
    static final float SEND_FORCE_MIN = 2.6f;
    static final float SEND_FORCE_RANGE = 3.6f;
    // Тип полёта: 0 — как у эффекта, 1..8 — кривые Easing, 9 — вихрь
    static final int MOTION_PRESET = 0;
    static final int MOTION_VORTEX = 9;
    // За сколько шагов (≈ 640 мс) частица пролетела бы путь эффекта; задаёт дальность полёта
    static final float GLIDE_REF_FRAMES = 40.0f;
    // Минимальная дальность полёта в dp, чтобы почти неподвижные частицы тоже летели
    static final float GLIDE_MIN_PX = 60.0f;

    // Эффекты отправки
    public static final int SEND_CLASSIC = 0;
    public static final int SEND_RISE = 1;
    public static final int SEND_BLAST = 2;
    public static final int SEND_SWIRL = 3;
    public static final int SEND_COMET = 4;
    public static final int SEND_FIREWORK = 5;
    public static final int SEND_CONFETTI = 6;
    public static final int SEND_HEARTS = 7;
    public static final int SEND_RAIN = 8;
    public static final int SEND_VORTEX = 9;
    public static final int SEND_PRESETS = 10;

    static final String[] SEND_NAMES_RU = {
        "Из кнопки (классика)", "Взлёт букв", "Взрыв букв", "Вихрь из букв", "Кометы",
        "Салют", "Конфетти", "Сердечки", "Звёздный дождь", "Вихрь в кнопку"
    };

    public static final int SEND_PALETTE_COUNT = 18;
    static final String[] PALETTE_NAMES_RU = {
        "Как при стирании", "TikTok", "Неон", "Огонь", "Радуга", "Цвет текста", "Золото", "Лёд",
        "Закат", "Конфетные", "Матрица", "Океан", "Сакура", "Лаванда", "Telegram", "Токсик",
        "Монохром", "Конфетти-микс"
    };
    // Индексы 1..17 (0 = как у искр, 4 = радуга, 5 = цвет текста считаются отдельно)
    static final int[][] PALETTES = {
        null,
        {0xFFFE2C55, 0xFF25F4EE, 0xFFFFFFFF},
        {0xFF9C27B0, 0xFF00E5FF, 0xFF651FFF},
        {0xFFFF1744, 0xFFFF6D00, 0xFFFFD600},
        null,
        null,
        {0xFFFFD700, 0xFFFFC107, 0xFFFFF59D, 0xFFFFFFFF},
        {0xFFE1F5FE, 0xFF81D4FA, 0xFF29B6F6, 0xFFFFFFFF},
        {0xFFFF5E62, 0xFFFF9966, 0xFFFFC371, 0xFF8E2DE2},
        {0xFFFF9AD5, 0xFF9AE6FF, 0xFFFFF59A, 0xFFB9A0FF, 0xFF9AFFC8},
        {0xFF00FF41, 0xFF00C853, 0xFFB9F6CA},
        {0xFF0077B6, 0xFF00B4D8, 0xFF90E0EF, 0xFFCAF0F8},
        {0xFFFFD7E4, 0xFFFFAECB, 0xFFFB98BC, 0xFFFFFFFF},
        {0xFFB39DDB, 0xFFCE93D8, 0xFFE1BEE7, 0xFF7E57C2},
        {0xFF2AABEE, 0xFF229ED9, 0xFFFFFFFF, 0xFF8FD3FF},
        {0xFFC6FF00, 0xFF76FF03, 0xFF00E676, 0xFF1DE9B6},
        {0xFFFFFFFF, 0xFFCFD8DC, 0xFF90A4AE},
        {0xFFFF1744, 0xFFFFD600, 0xFF00E676, 0xFF00B0FF, 0xFFD500F9, 0xFFFF9100}
    };

    public static String[] sendPresetNames(boolean translate) {
        String[] out = new String[SEND_NAMES_RU.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = translate ? Strings.t(SEND_NAMES_RU[i]) : SEND_NAMES_RU[i];
        }
        return out;
    }

    // Во что превращаются буквы при отправке
    static final String[] SHAPE_NAMES_RU = {
        "Как в эффекте", "Точки", "Штрихи-искры", "Снежинки", "Лепестки сакуры", "Пузыри",
        "Сердечки", "Конфетти", "Буквы"
    };
    public static final int SEND_SHAPE_COUNT = 9;
    int sendShape;
    int sendSwirl = 100;
    int sendDir;
    // Тип полёта, его длительность, изгиб пути и волна вылета букв
    int sendMotion;
    int sendDuration = 600;
    int sendArc;
    int sendWave = 100;
    float sendDensity = 3.0f;

    public void setSendMotion(int motion, int duration, int arc, int wave) {
        sendMotion = motion;
        sendDuration = duration;
        sendArc = arc;
        sendWave = wave;
    }

    // Тип полёта: частица из своей точки плавно летит по выбранной кривой к точке назначения.
    // Точка назначения берётся по направлению и силе запуска, которые задал эффект.
    void routeSend(Particle particle) {
        if (sendMotion == MOTION_PRESET || particle.homing) return;
        float frames = Math.max(1.0f, sendDuration / 16.0f);
        float dx = particle.vx * GLIDE_REF_FRAMES;
        float dy = particle.vy * GLIDE_REF_FRAMES;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float minLen = GLIDE_MIN_PX * sendDensity;
        if (len < minLen) {
            float ang = len > 0.0f ? (float) Math.atan2(dy, dx) : random.nextFloat() * 6.2831f;
            dx = (float) Math.cos(ang) * minLen;
            dy = (float) Math.sin(ang) * minLen;
        }
        if (sendMotion == MOTION_VORTEX) {
            float dir = sendDir == 2 ? (random.nextBoolean() ? 1.0f : -1.0f) : (sendDir == 0 ? -1.0f : 1.0f);
            particle.glide(particle.x + dx, particle.y + dy, frames, Easing.S_CURVE,
                0.0f, sendSwirl / 100.0f, dir);
        } else {
            particle.glide(particle.x + dx, particle.y + dy, frames, sendMotion - 1,
                sendArc / 100.0f, 0.0f, 1.0f);
        }
    }

    public void setSendOptions(int shape, int swirl, int dir) {
        sendShape = shape;
        sendSwirl = swirl;
        sendDir = dir;
    }

    int pick(int fallback) {
        switch (sendShape) {
            case 1: return Config.STYLE_DUST;
            case 2: return Config.STYLE_SPARKS;
            case 3: return Config.STYLE_SNOW;
            case 4: return Config.STYLE_SAKURA;
            case 5: return Config.STYLE_BUBBLES;
            case 6: return Config.STYLE_HEART;
            case 7: return Config.STYLE_CONFETTI;
            case 8: return Config.STYLE_LETTERS;
            default: return fallback;
        }
    }

    public static String[] sendShapeNames(boolean translate) {
        String[] out = new String[SHAPE_NAMES_RU.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = translate ? Strings.t(SHAPE_NAMES_RU[i]) : SHAPE_NAMES_RU[i];
        }
        return out;
    }

    public static String[] sendPaletteNames(boolean translate) {
        String[] out = new String[PALETTE_NAMES_RU.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = translate ? Strings.t(PALETTE_NAMES_RU[i]) : PALETTE_NAMES_RU[i];
        }
        return out;
    }

    int sendColor(Config config, int palette, int textColor) {
        if (palette <= 0) return config.getSparkColor(random, textColor);
        if (palette == 4) return Config.rainbowColor(random.nextFloat() * 360.0f);
        if (palette == 5) return textColor;
        int[] set = PALETTES[Math.min(palette, PALETTES.length - 1)];
        return set[random.nextInt(set.length)];
    }

    final Particle[] pool = new Particle[LIMIT];
    final Random random = new Random();
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path petal = petal();
    final Path heartPath = heart();
    final Path outline = new Path();
    final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final java.util.HashMap<String, int[]> masks = new java.util.HashMap<String, int[]>();
    final PathMeasure measure = new PathMeasure();
    final float[] points = new float[Config.MAX_AMOUNT * 2];
    final float[] position = new float[2];
    final int[] location = new int[2];
    int count;

    public Particles() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        letter.setTextAlign(Paint.Align.CENTER);
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void clear() {
        for (int i = 0; i < count; i++) {
            if (pool[i] != null) pool[i].text = null;
        }
        count = 0;
    }

    public boolean burst(EditText view, Config config, List<Ghost> cells) {
        if (cells.isEmpty() || count >= LIMIT) return false;
        TextPaint paint = view.getPaint();
        letter.setTypeface(paint.getTypeface());
        view.getLocationInWindow(location);
        float density = view.getResources().getDisplayMetrics().density;
        float ox = location[0] - view.getScrollX();
        float oy = location[1] - view.getScrollY();
        float minX = location[0];
        float maxX = location[0] + view.getWidth();
        float maxY = location[1] + view.getHeight() - density;
        float textSize = paint.getTextSize();
        int baseColor = paint.getColor();
        boolean fall = config.style == Config.STYLE_FALL;
        boolean glyphStyle = config.style == Config.STYLE_SAND || config.style == Config.STYLE_SMOKE;
        float sparkBase = fall ? textSize : textSize * config.sparkSize / 100.0f;
        int per = fall ? 1 : config.amount;
        int before = count;

        for (int c = 0; c < cells.size() && count < LIMIT; c++) {
            Ghost cell = cells.get(c);
            if (glyphStyle) {
                glyph(paint, config, cell, ox, oy, baseColor);
                continue;
            }
            int budget = Math.min(per, LIMIT - count);
            float width = Math.max(1.0f, cell.width);
            float top = cell.baseline - textSize * 0.78f;
            float height = Math.max(1.0f, textSize * 0.82f);
            float cx = cell.x + width * 0.5f;
            float cy = top + height * 0.55f;
            int sampled = fall ? 0 : sample(paint, cell, budget);

            for (int j = 0; j < budget; j++) {
                float px;
                float py;
                if (fall) {
                    px = cx;
                    py = cell.baseline - textSize * 0.35f;
                } else if (j < sampled) {
                    px = points[j * 2];
                    py = points[j * 2 + 1];
                } else {
                    px = cell.x + width * (j + 0.5f) / budget + (random.nextFloat() - 0.5f) * width * 0.6f;
                    py = top + random.nextFloat() * height;
                }
                int pColor = config.getSparkColor(random, baseColor);
                Particle particle = acquire();
                particle.init(random, config.style, px + ox, py + oy, pColor, sparkBase, cell.text);
                particle.tune(config.partSpeed / 100.0f, config.partLife / 100.0f, config.wind);
                if (fall) {
                    particle.bound(minX, maxX, maxY);
                } else {
                    particle.push((px - cx) / Math.max(1.0f, width * 0.5f), (py - cy) / Math.max(1.0f, height * 0.5f));
                }
            }
        }
        return count > before;
    }

    // Эффект отправки. Буквы-пресеты берут точки с контура букв, остальные летят из кнопки.
    public boolean burstSend(EditText view, Config config, List<Ghost> cells, float bx, float by,
                             int preset, int palette, int amount, int speed) {
        if (count >= LIMIT) return false;
        float spd = speed / 100.0f;
        float density = view.getResources().getDisplayMetrics().density;
        sendDensity = density;
        boolean fromLetters = preset == SEND_RISE || preset == SEND_BLAST || preset == SEND_SWIRL
            || preset == SEND_COMET || preset == SEND_HEARTS || preset == SEND_RAIN || preset == SEND_VORTEX;
        if (fromLetters && cells != null && !cells.isEmpty()) {
            return lettersSend(view, config, cells, bx, by, preset, palette, amount, spd);
        }
        if (fromLetters) preset = SEND_CLASSIC;
        return buttonSend(config, bx, by, preset, palette, amount, spd, 14.0f * density,
            view.getPaint().getColor());
    }

    boolean lettersSend(EditText view, Config config, List<Ghost> cells, float bx, float by, int preset,
                                int palette, int amount, float spd) {
        float density = view.getResources().getDisplayMetrics().density;
        TextPaint paint = view.getPaint();
        view.getLocationInWindow(location);
        float ox = location[0] - view.getScrollX();
        float oy = location[1] - view.getScrollY();
        float textSize = paint.getTextSize();
        int textColor = paint.getColor();
        int total = cells.size();
        int per = Math.max(1, Math.min(Config.MAX_AMOUNT, amount));
        per = Math.max(1, Math.min(per, (LIMIT - count) / Math.max(1, total)));
        int before = count;

        // Центр всего текста: от него разлетается «взрыв»
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int c = 0; c < total; c++) {
            Ghost cell = cells.get(c);
            minX = Math.min(minX, cell.x);
            maxX = Math.max(maxX, cell.x + cell.width);
            minY = Math.min(minY, cell.baseline - textSize);
            maxY = Math.max(maxY, cell.baseline);
        }
        float tcx = (minX + maxX) * 0.5f;
        float tcy = (minY + maxY) * 0.5f;
        float spanX = Math.max(1.0f, maxX - minX);

        for (int c = 0; c < total && count < LIMIT; c++) {
            Ghost cell = cells.get(c);
            float width = Math.max(1.0f, cell.width);
            float top = cell.baseline - textSize * 0.78f;
            float height = Math.max(1.0f, textSize * 0.82f);
            int budget = Math.min(per, LIMIT - count);
            int sampled = sample(paint, cell, budget);
            float rel = Math.max(0.0f, Math.min(1.0f, (cell.x + width * 0.5f - minX) / spanX));
            for (int j = 0; j < budget; j++) {
                float px;
                float py;
                if (j < sampled) {
                    px = points[j * 2];
                    py = points[j * 2 + 1];
                } else {
                    px = cell.x + width * (j + 0.5f) / budget + (random.nextFloat() - 0.5f) * width * 0.6f;
                    py = top + random.nextFloat() * height;
                }
                int color = sendColor(config, palette, textColor);
                Particle particle = acquire();
                float r1 = random.nextFloat();
                float r2 = random.nextFloat();
                switch (preset) {
                    case SEND_BLAST: {
                        particle.init(random, pick(Config.STYLE_SPARKS), px + ox, py + oy, color, textSize, cell.text);
                        particle.color = color;
                        float dx = px - tcx;
                        float dy = py - tcy;
                        float len = (float) Math.sqrt(dx * dx + dy * dy);
                        float ang = len < 0.5f ? random.nextFloat() * 6.2831f
                            : (float) Math.atan2(dy, dx) + (r1 - 0.5f) * 1.2f;
                        float force = (2.4f + r2 * 4.2f) * spd;
                        particle.launch((float) Math.cos(ang) * force * 1.25f, (float) Math.sin(ang) * force - 0.8f,
                            0.04f, 0.022f, 0.022f + r1 * 0.026f, -1.0f, -1.0f);
                        particle.delay = random.nextFloat() * 3.0f;
                        break;
                    }
                    case SEND_SWIRL: {
                        particle.init(random, pick(Config.STYLE_DUST), px + ox, py + oy, color, textSize, cell.text);
                        particle.color = color;
                        particle.launch((r1 - 0.5f) * 1.2f, -(0.9f + r2 * 1.6f) * spd,
                            -0.004f, 0.02f, 0.010f + r1 * 0.012f, 0.16f, -1.0f);
                        particle.delay = rel * 8.0f + random.nextFloat() * 4.0f;
                        break;
                    }
                    case SEND_COMET: {
                        particle.init(random, pick(Config.STYLE_SPARKS), px + ox, py + oy, color, textSize, cell.text);
                        particle.color = color;
                        particle.launch((r1 - 0.5f) * 0.8f, -(5.0f + r2 * 6.0f) * spd,
                            -0.02f, 0.010f, 0.016f + r1 * 0.020f, 0.0f, 3.6f + r2 * 2.0f);
                        particle.delay = rel * 10.0f + random.nextFloat() * 3.0f;
                        break;
                    }
                    case SEND_HEARTS: {
                        particle.init(random, pick(Config.STYLE_HEART), px + ox, py + oy, color, textSize, cell.text);
                        particle.color = color;
                        particle.vy *= spd;
                        particle.delay = rel * 9.0f + random.nextFloat() * 4.0f;
                        break;
                    }
                    case SEND_VORTEX: {
                        // Буквы рассыпаются на частицы и по спирали втягиваются в кнопку отправки
                        particle.init(random, pick(Config.STYLE_DUST), px + ox, py + oy, color, textSize, cell.text);
                        particle.color = color;
                        float dir = sendDir == 2 ? (random.nextBoolean() ? 1.0f : -1.0f) : (sendDir == 0 ? -1.0f : 1.0f);
                        particle.home(bx, by, 4.2f * density * spd * (0.8f + r1 * 0.5f),
                            sendSwirl / 100.0f, dir, 10.0f * density);
                        particle.delay = rel * 8.0f + random.nextFloat() * 4.0f;
                        break;
                    }
                    case SEND_RAIN: {
                        particle.init(random, pick(Config.STYLE_SPARKS), px + ox, py + oy, color, textSize, cell.text);
                        particle.color = color;
                        particle.launch((r1 - 0.5f) * 0.9f, (0.4f + r2 * 1.4f) * spd,
                            0.05f, 0.012f, 0.014f + r1 * 0.018f, -1.0f, 2.4f + r2 * 1.2f);
                        particle.delay = rel * 8.0f + random.nextFloat() * 3.0f;
                        break;
                    }
                    default: {
                        // Взлёт: светящиеся точки поднимаются вверх волной слева направо
                        particle.init(random, pick(Config.STYLE_DUST), px + ox, py + oy, color, textSize, cell.text);
                        particle.color = color;
                        particle.launch((r1 - 0.5f) * 0.9f, -(1.5f + r2 * 2.6f) * spd,
                            -0.010f, 0.022f, 0.014f + r1 * 0.018f, 0.04f, -1.0f);
                        particle.delay = rel * 10.0f + random.nextFloat() * 3.0f;
                        break;
                    }
                }
                routeSend(particle);
                particle.delay *= sendWave / 100.0f;
            }
        }
        return count > before;
    }

    boolean buttonSend(Config config, float cx, float cy, int preset, int palette, int amount,
                               float spd, float base, int textColor) {
        int before = count;
        int total = amount * 6 + 2;
        if (preset == SEND_FIREWORK || preset == SEND_CONFETTI) total *= 2;
        int budget = Math.min(total, LIMIT - count);
        for (int j = 0; j < budget; j++) {
            int color = sendColor(config, palette, textColor);
            if (preset == SEND_CLASSIC && palette == 0) {
                color = SEND_COLORS[random.nextInt(SEND_COLORS.length)];
            }
            Particle particle = acquire();
            float r1 = random.nextFloat();
            float r2 = random.nextFloat();
            if (preset == SEND_FIREWORK) {
                particle.init(random, Config.STYLE_SPARKS, cx, cy, color, base, null);
                float ang = (j / (float) Math.max(1, budget)) * 6.2831f + (r1 - 0.5f) * 0.35f;
                float force = (2.8f + r2 * 4.2f) * spd;
                particle.launch((float) Math.cos(ang) * force, (float) Math.sin(ang) * force,
                    0.05f, 0.028f, 0.014f + r1 * 0.016f, -1.0f, 2.2f + r2 * 1.4f);
            } else if (preset == SEND_CONFETTI) {
                particle.init(random, Config.STYLE_CONFETTI, cx, cy, color, base * 1.6f, null);
                float ang = -(float) (Math.PI / 2.0) + (r1 - 0.5f) * 1.9f;
                float force = (4.5f + r2 * 6.0f) * spd;
                particle.launch((float) Math.cos(ang) * force, (float) Math.sin(ang) * force,
                    0.13f, 0.035f, 0.008f + r2 * 0.006f, -1.0f, -1.0f);
            } else {
                particle.init(random, Config.STYLE_SPARKS, cx, cy, color, base, null);
                float ang = -(float) (Math.PI / 2.0) + (r1 - 0.5f) * SEND_SPREAD;
                float force = (SEND_FORCE_MIN + r2 * SEND_FORCE_RANGE) * spd;
                particle.vx = (float) Math.cos(ang) * force;
                particle.vy = (float) Math.sin(ang) * force;
            }
            routeSend(particle);
        }
        return count > before;
    }

    public void step(long elapsed) {
        if (elapsed <= 0) return;
        float scale = Math.max(0.25f, Math.min(3.0f, elapsed / 16.0f));
        for (int i = count - 1; i >= 0; i--) {
            if (!pool[i].step(scale)) {
                release(i);
            }
        }
    }

    public void draw(Canvas canvas) {
        for (int i = 0; i < count; i++) {
            Particle particle = pool[i];
            int alpha = Math.round(particle.alpha() * 255.0f);
            if (alpha <= 0) continue;
            switch (particle.style) {
                case Config.STYLE_SNOW:
                    snow(canvas, particle, alpha);
                    break;
                case Config.STYLE_SAKURA:
                    sakura(canvas, particle, alpha);
                    break;
                case Config.STYLE_LETTERS:
                case Config.STYLE_FALL:
                    letter(canvas, particle, alpha);
                    break;
                case Config.STYLE_SAND:
                    sand(canvas, particle, alpha);
                    break;
                case Config.STYLE_SMOKE:
                    smoke(canvas, particle, alpha);
                    break;
                case Config.STYLE_BUBBLES:
                    bubble(canvas, particle, alpha);
                    break;
                case Config.STYLE_SPARKS:
                    spark(canvas, particle, alpha);
                    break;
                case Config.STYLE_CONFETTI:
                    confetti(canvas, particle, alpha);
                    break;
                case Config.STYLE_HEART:
                    heart(canvas, particle, alpha);
                    break;
                default:
                    dust(canvas, particle, alpha);
                    break;
            }
        }
    }

    Particle acquire() {
        Particle particle = pool[count];
        if (particle == null) {
            particle = new Particle();
            pool[count] = particle;
        }
        count++;
        return particle;
    }

    void release(int index) {
        Particle particle = pool[index];
        particle.text = null;
        count--;
        pool[index] = pool[count];
        pool[count] = particle;
    }

    int sample(TextPaint paint, Ghost cell, int wanted) {
        outline.reset();
        paint.getTextPath(cell.text, 0, cell.text.length(), cell.x, cell.baseline, outline);
        if (outline.isEmpty()) return 0;
        measure.setPath(outline, false);
        float total = 0.0f;
        int contours = 0;
        do {
            total += measure.getLength();
            contours++;
        } while (contours < CONTOURS && measure.nextContour());
        if (total < 1.0f) return 0;
        measure.setPath(outline, false);
        int filled = 0;
        contours = 0;
        do {
            float length = measure.getLength();
            int share = Math.round(wanted * length / total);
            for (int i = 0; i < share && filled < wanted; i++) {
                put(filled++, random.nextFloat() * length);
            }
            contours++;
        } while (filled < wanted && contours < CONTOURS && measure.nextContour());
        if (filled == 0) return 0;
        if (filled < wanted) {
            measure.setPath(outline, false);
            float length = measure.getLength();
            while (filled < wanted) {
                put(filled++, random.nextFloat() * length);
            }
        }
        return filled;
    }

    void put(int index, float distance) {
        measure.getPosTan(distance, position, null);
        points[index * 2] = position[0];
        points[index * 2 + 1] = position[1];
    }

    // Зёрна берутся из закрашенной области буквы, поэтому в начале они складываются в саму букву
    void glyph(TextPaint paint, Config config, Ghost cell, float ox, float oy, int baseColor) {
        int[] mask = mask(paint, cell.text, cell.width);
        int total = mask.length / 2;
        if (total == 0) return;
        boolean smoke = config.style == Config.STYLE_SMOKE;
        int wanted = Math.min(total, smoke ? config.smokeDensity : config.amount * 9);
        wanted = Math.min(wanted, LIMIT - count);
        if (wanted <= 0) return;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 1; i < mask.length; i += 2) {
            minY = Math.min(minY, mask[i]);
            maxY = Math.max(maxY, mask[i]);
        }
        float spanY = Math.max(1.0f, maxY - minY);
        float grain = (float) Math.sqrt(total / (float) wanted) * 1.15f * config.sparkSize / 100.0f;
        float stride = total / (float) wanted;
        float width = Math.max(1.0f, cell.width);
        float holdSpan = 4.0f + config.smokeHold * 0.26f;
        for (int i = 0; i < wanted; i++) {
            int index = Math.min(total - 1, (int) ((i + random.nextFloat()) * stride));
            float rx = mask[index * 2];
            float ry = mask[index * 2 + 1];
            float px = cell.x + rx + random.nextFloat() - 0.5f;
            float py = cell.baseline + ry + random.nextFloat() - 0.5f;
            int color = smoke ? smokeColor(config, baseColor) : config.getSparkColor(random, baseColor);
            Particle particle = acquire();
            particle.init(random, config.style, px + ox, py + oy, color, grain, cell.text);
            particle.tune(config.partSpeed / 100.0f, config.partLife / 100.0f, config.wind);
            if (smoke) {
                particle.smokeTune(config.smokeRise / 100.0f, config.smokeSpread / 100.0f, config.smokeSwirl / 100.0f);
                // верх буквы уходит первым, низ держится дольше
                particle.delay = (ry - minY) / spanY * holdSpan + random.nextFloat() * 3.0f;
            } else {
                particle.delay = Motion.clamp(rx / width) * 13.0f + random.nextFloat() * 5.0f;
            }
        }
    }

    int smokeColor(Config config, int baseColor) {
        switch (config.smokeTone) {
            case 1:
                return 0xFFB8B8C0;
            case 2:
                return 0xFFFFA96B;
            case 3:
                return config.getSparkColor(random, baseColor);
            default:
                return baseColor;
        }
    }

    // Пиксели буквы (x, y относительно начала текста и baseline); считаем один раз и кэшируем
    int[] mask(TextPaint paint, String text, float width) {
        String key = text + '|' + paint.getTextSize() + '|' + System.identityHashCode(paint.getTypeface());
        int[] cached = masks.get(key);
        if (cached != null) return cached;
        if (masks.size() > 96) masks.clear();
        float size = paint.getTextSize();
        int w = Math.max(2, (int) Math.ceil(Math.max(width, paint.measureText(text))) + MASK_PAD * 2);
        int h = Math.max(2, (int) Math.ceil(size * 1.5f));
        int base = Math.round(size * 1.05f);
        int[] out = new int[0];
        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            glyphPaint.set(paint);
            glyphPaint.setShader(null);
            glyphPaint.setMaskFilter(null);
            glyphPaint.setColor(0xFFFFFFFF);
            canvas.drawText(text, MASK_PAD, base, glyphPaint);
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            bitmap.recycle();
            int n = 0;
            for (int i = 0; i < pixels.length; i++) {
                if ((pixels[i] >>> 24) > 110) n++;
            }
            out = new int[n * 2];
            int k = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((pixels[y * w + x] >>> 24) > 110) {
                        out[k++] = x - MASK_PAD;
                        out[k++] = y - base;
                    }
                }
            }
        } catch (Throwable ignored) {
            // нет памяти или странный шрифт: просто без зёрен
        }
        masks.put(key, out);
        return out;
    }

    void sand(Canvas canvas, Particle particle, int alpha) {
        float half = Math.max(0.7f, particle.size * (0.40f + 0.60f * particle.life) * 0.5f);
        tint(fill, particle.color, alpha);
        canvas.drawRect(particle.x - half, particle.y - half, particle.x + half, particle.y + half, fill);
    }

    void bubble(Canvas canvas, Particle particle, int alpha) {
        float r = Math.max(2.0f, particle.size * 0.5f * (0.75f + 0.25f * particle.life));
        int tone = lighten(particle.color, 0.35f);
        tint(fill, tone, alpha * 0.10f);
        canvas.drawCircle(particle.x, particle.y, r, fill);
        tint(stroke, tone, alpha * 0.85f);
        stroke.setStrokeWidth(Math.max(1.0f, r * 0.16f));
        canvas.drawCircle(particle.x, particle.y, r, stroke);
        tint(fill, 0xFFFFFFFF, alpha * 0.80f);
        canvas.drawCircle(particle.x - r * 0.35f, particle.y - r * 0.35f, Math.max(0.8f, r * 0.16f), fill);
    }

    void smoke(Canvas canvas, Particle particle, int alpha) {
        float age = 1.0f - particle.life;
        float radius = Math.max(1.2f, particle.size * 0.70f * (1.0f + particle.expand * 2.4f * age));
        // цвет букв постепенно сереет, как остывающий дым
        int tone = mix(particle.color, 0xFF8E8E98, Math.min(1.0f, age * 1.3f));
        float halo = Math.min(1.0f, age * 2.5f);
        if (halo > 0.02f) {
            tint(fill, tone, alpha * 0.20f * halo);
            canvas.drawCircle(particle.x, particle.y, radius * 1.8f, fill);
        }
        tint(fill, tone, alpha * 0.58f);
        canvas.drawCircle(particle.x, particle.y, radius, fill);
        tint(fill, lighten(tone, 0.30f), alpha * 0.26f * (0.4f + 0.6f * age));
        canvas.drawCircle(particle.x - radius * 0.15f, particle.y - radius * 0.2f, radius * 0.5f, fill);
    }

    // Штрих-искра: мягкий ореол, цветное тело вдоль направления полёта и яркая «голова»
    void spark(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(1.5f, particle.size * (0.65f * particle.life + 0.35f));
        float length = Math.max(3.0f, size * particle.stretch);
        float angle = (float) Math.atan2(particle.vy, particle.vx);
        float hx = (float) Math.cos(angle) * length * 0.5f;
        float hy = (float) Math.sin(angle) * length * 0.5f;
        int a = Math.round(alpha * flicker(particle));
        tint(stroke, lighten(particle.color, 0.40f), a * 0.28f);
        stroke.setStrokeWidth(Math.max(2.0f, size * 0.62f));
        canvas.drawLine(particle.x - hx, particle.y - hy, particle.x + hx, particle.y + hy, stroke);
        tint(stroke, particle.color, a);
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.28f));
        canvas.drawLine(particle.x - hx, particle.y - hy, particle.x + hx, particle.y + hy, stroke);
        tint(fill, lighten(particle.color, 0.85f), a * 0.9f * particle.life);
        canvas.drawCircle(particle.x + hx, particle.y + hy, Math.max(0.8f, size * 0.17f), fill);
    }

    void dust(Canvas canvas, Particle particle, int alpha) {
        int a = Math.round(alpha * flicker(particle));
        float radius = Math.max(0.8f, particle.size * (0.50f + 0.40f * (1.0f - particle.life)) * 0.5f);
        tint(fill, lighten(particle.color, 0.30f), a * 0.32f);
        canvas.drawCircle(particle.x, particle.y, radius * 2.0f, fill);
        tint(fill, particle.color, a * 0.88f);
        canvas.drawCircle(particle.x, particle.y, radius, fill);
        if (particle.life > 0.5f) {
            tint(fill, lighten(particle.color, 0.70f), a * (particle.life - 0.5f) * 1.7f);
            canvas.drawCircle(particle.x, particle.y, radius * 0.45f, fill);
        }
    }

    void snow(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(2.0f, particle.size * (0.55f + 0.45f * particle.life));
        int frost = lighten(particle.color, 0.60f);
        tint(fill, frost, alpha * 0.16f);
        canvas.drawCircle(particle.x, particle.y, size * 0.85f, fill);
        tint(stroke, frost, Math.max(32, alpha));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.13f));
        float radius = Math.max(2.0f, size * 0.5f);
        float branch = radius * 0.35f;
        float knot = radius * 0.38f;
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        for (int i = 0; i < 3; i++) {
            canvas.rotate(60.0f);
            canvas.drawLine(-radius, 0.0f, radius, 0.0f, stroke);
            canvas.drawLine(knot, 0.0f, knot - branch, -branch, stroke);
            canvas.drawLine(knot, 0.0f, knot - branch, branch, stroke);
        }
        canvas.restoreToCount(save);
        tint(fill, lighten(particle.color, 0.85f), alpha * 0.90f);
        canvas.drawCircle(particle.x, particle.y, Math.max(1.0f, size * 0.10f), fill);
    }

    void sakura(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(2.0f, particle.size * (0.55f + 0.45f * particle.life));
        float w = Math.max(2.0f, size * 0.62f);
        float h = Math.max(3.0f, size * particle.stretch);
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        tint(fill, lighten(particle.color, 0.45f), alpha * 0.22f);
        drawPetal(canvas, w * 1.30f, h * 1.24f, 0.0f);
        tint(fill, particle.color, Math.max(24, alpha));
        drawPetal(canvas, w, h, 0.0f);
        tint(fill, lighten(particle.color, 0.35f), alpha * 0.45f);
        drawPetal(canvas, w * 0.55f, h * 0.55f, -h * 0.055f);
        tint(stroke, mix(particle.color, RIB, 0.55f), Math.min(160, Math.max(28, alpha * 0.55f)));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.08f));
        canvas.drawLine(0.0f, -h * 0.28f, 0.0f, h * 0.38f, stroke);
        canvas.restoreToCount(save);
    }

    void drawPetal(Canvas canvas, float width, float height, float offset) {
        int save = canvas.save();
        canvas.translate(0.0f, offset);
        canvas.scale(width, height);
        canvas.drawPath(petal, fill);
        canvas.restoreToCount(save);
    }

    void letter(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(6.0f, particle.size);
        float shrink = particle.style == Config.STYLE_FALL ? 1.0f : 0.55f + 0.45f * particle.life;
        tint(letter, particle.color, alpha);
        letter.setTextSize(size);
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        canvas.scale(shrink, shrink);
        canvas.drawText(particle.text, 0.0f, size * 0.35f, letter);
        canvas.restoreToCount(save);
    }

    void confetti(Canvas canvas, Particle particle, int alpha) {
        float w = Math.max(3.0f, particle.size);
        float h = w * 0.55f;
        float flip = (float) Math.cos(particle.time * 0.22f + particle.flickerPhase);
        if (Math.abs(flip) < 0.12f) flip = flip < 0.0f ? -0.12f : 0.12f;
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        canvas.scale(1.0f, flip);
        tint(fill, flip > 0.0f ? particle.color : darken(particle.color, 0.25f), Math.min(255, alpha * 1.1f));
        canvas.drawRect(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f, fill);
        canvas.restoreToCount(save);
    }

    void heart(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(4.0f, particle.size * (0.70f + 0.30f * particle.life));
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        tint(fill, lighten(particle.color, 0.40f), alpha * 0.25f);
        canvas.save();
        canvas.scale(size * 1.45f, size * 1.45f);
        canvas.drawPath(heartPath, fill);
        canvas.restore();
        tint(fill, particle.color, alpha);
        canvas.scale(size, size);
        canvas.drawPath(heartPath, fill);
        canvas.restoreToCount(save);
    }

    static int darken(int color, float amount) {
        return mix(color, 0xFF000000, amount);
    }

    static Path heart() {
        Path path = new Path();
        path.moveTo(0.0f, 0.45f);
        path.cubicTo(-0.75f, -0.05f, -0.62f, -0.62f, -0.30f, -0.62f);
        path.cubicTo(-0.12f, -0.62f, 0.0f, -0.50f, 0.0f, -0.36f);
        path.cubicTo(0.0f, -0.50f, 0.12f, -0.62f, 0.30f, -0.62f);
        path.cubicTo(0.62f, -0.62f, 0.75f, -0.05f, 0.0f, 0.45f);
        path.close();
        return path;
    }

    static float flicker(Particle particle) {
        if (particle.flicker <= 0.0f) return 1.0f;
        return 0.80f + 0.20f * (float) Math.sin(particle.time * particle.flicker + particle.flickerPhase);
    }

    static void tint(Paint paint, int color, float alpha) {
        paint.setColor(color);
        paint.setAlpha(Math.max(0, Math.min(255, Math.round(alpha))));
    }

    static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }

    static int mix(int from, int to, float amount) {
        int r = (from >> 16) & 0xFF;
        int g = (from >> 8) & 0xFF;
        int b = from & 0xFF;
        r += Math.round((((to >> 16) & 0xFF) - r) * amount);
        g += Math.round((((to >> 8) & 0xFF) - g) * amount);
        b += Math.round(((to & 0xFF) - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static Path petal() {
        Path path = new Path();
        path.moveTo(0.0f, -0.5f);
        path.cubicTo(0.95f, -0.58f, 1.03f, 0.02f, 0.22f, 0.42f);
        path.cubicTo(0.10f, 0.49f, 0.04f, 0.52f, 0.0f, 0.56f);
        path.cubicTo(-0.04f, 0.52f, -0.10f, 0.49f, -0.22f, 0.42f);
        path.cubicTo(-1.03f, 0.02f, -0.95f, -0.58f, 0.0f, -0.5f);
        path.close();
        return path;
    }
}
