package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The thanks, as the whole screen of space: names written in chalk among the stars.
 *
 * A tap on a name sets off something of that person's own; holding it opens
 * where the person lives. The ways to support drift through the lower sky,
 * each a planet in a lane of its own, and open their page when tapped.
 */
final class ThanksSpace extends View {

    /** Someone thanked: what is written, the line under it, and what holding the name does. */
    static final class Person {
        final String name, role;
        final Runnable hold;
        final int egg;
        Person(String name, String role, int egg, Runnable hold) {
            this.name = name; this.role = role; this.egg = egg; this.hold = hold;
        }
    }

    /** A way to support: its name and what tapping it opens. */
    static final class Planet {
        final String name;
        final int colour;
        final Runnable open;
        Planet(String name, int colour, Runnable open) { this.name = name; this.colour = colour; this.open = open; }
    }

    static final int EGG_TILCAYO = 0, EGG_CONSTELLATION = 1, EGG_SCATTER = 2, EGG_LOTUS = 3, EGG_GHOST = 4, EGG_HEART = 5;

    private final List<Person> people;
    private final List<Planet> planets;
    private final String hint, supportTitle;
    private final float d;
    private final Random random = new Random(7);
    /** Room kept at the top for the status bar and the close button. */
    private int top;

    private final Paint chalk = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private final Path path = new Path();

    private float[] starX, starY, starSize, starPhase;
    private final RectF[] nameBoxes;
    private final float[] nameX, nameY, nameTilt, nameSize;
    private final RectF[] planetBoxes;
    private Shader space;
    private final long born = SystemClock.uptimeMillis();

    // what is happening right now
    private final List<float[]> bits = new ArrayList<>();      // x, y, vx, vy, life, kind, colour, size, spin, angle
    private final List<float[]> cats = new ArrayList<>();      // x, y, vx, vy, spin, angle, size
    private final List<float[]> shards = new ArrayList<>();    // x, y, vx, vy, spin, angle, size, quarter, life
    private final List<float[]> rings = new ArrayList<>();     // x, y, born, colour
    private long tilcayoAt = -1, flashAt = -1, shakeAt = -1;
    private long ghostAt = -1;
    private float ghostY;
    private Bitmap tilcayo;
    private long shooting = -1;
    private float shootX, shootY;
    private final long[] eggAt;
    /** Whether an egg has already let go of its petals or its dust. */
    private final boolean[] released;
    private float[][] scatter;                                  // per letter of the scattered name: dx, dy, spin

    ThanksSpace(Context context, List<Person> people, List<Planet> planets, String hint, String supportTitle) {
        super(context);
        this.people = people;
        this.planets = planets;
        this.hint = hint;
        this.supportTitle = supportTitle;
        d = context.getResources().getDisplayMetrics().density;
        top = Math.round(56 * d);
        Typeface hand = hand(context);
        chalk.setTypeface(hand);
        chalk.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        chalk.setShadowLayer(4 * d, 0, 0, 0x40FFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        nameBoxes = new RectF[people.size()];
        nameX = new float[people.size()];
        nameY = new float[people.size()];
        nameTilt = new float[people.size()];
        nameSize = new float[people.size()];
        eggAt = new long[people.size()];
        released = new boolean[people.size()];
        for (int i = 0; i < people.size(); i++) {
            nameBoxes[i] = new RectF();
            nameTilt[i] = (random.nextFloat() - .5f) * 8f;
            eggAt[i] = -1;
        }
        planetBoxes = new RectF[planets.size()];
        for (int i = 0; i < planets.size(); i++) planetBoxes[i] = new RectF();
        setClickable(true);
        setContentDescription(hint);
    }

    /** Room at the top the page must leave alone: the status bar, and what is drawn over it. */
    void setTopInset(int px) {
        top = px;
        requestLayout();
    }

    private static Typeface hand(Context context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26)
                return new Typeface.Builder(context.getAssets(), "margyt/fonts/caveat.ttf")
                        .setFontVariationSettings("'wght' 600").build();
            return Typeface.createFromAsset(context.getAssets(), "margyt/fonts/caveat.ttf");
        } catch (Throwable error) {
            return Typeface.create("casual", Typeface.NORMAL);
        }
    }

    /** White with holes in it, the way chalk catches on a board. */
    private static Bitmap grain() {
        int size = 96;
        Random noise = new Random(3);
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            int alpha = noise.nextFloat() < .16f ? 70 + noise.nextInt(70) : 205 + noise.nextInt(50);
            pixels[i] = (alpha << 24) | 0xF4F7F2;
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightSpec) : Math.round(720 * d);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        space = new LinearGradient(0, 0, w * .3f, h, new int[] {0xFF070A16, 0xFF0B1730, 0xFF0A1220},
                null, Shader.TileMode.CLAMP);
        int count = Math.round(w * h / (90 * d * 90 * d)) + 40;
        starX = new float[count]; starY = new float[count]; starSize = new float[count]; starPhase = new float[count];
        Random place = new Random(11);
        for (int i = 0; i < count; i++) {
            starX[i] = place.nextFloat() * w;
            starY[i] = place.nextFloat() * h;
            starSize[i] = (.5f + place.nextFloat() * 1.6f) * d;
            starPhase[i] = place.nextFloat() * 6.28f;
        }
        // one name to a line, zigzagging, each made to fit the width
        float margin = 22 * d;
        float from = top + 56 * d, to = h * .62f;
        float step = (to - from) / Math.max(1, people.size());
        for (int i = 0; i < people.size(); i++) {
            float size = 34 * d;
            chalk.setTextSize(size);
            float width = chalk.measureText(people.get(i).name);
            if (width > w - 2 * margin) { size *= (w - 2 * margin) / width; width = w - 2 * margin; }
            nameSize[i] = size;
            float room = Math.max(0, w - 2 * margin - width);
            float lean = i % 2 == 0 ? .1f + place.nextFloat() * .3f : .55f + place.nextFloat() * .4f;
            nameX[i] = margin + room * lean;
            nameY[i] = from + step * i + size;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        long now = SystemClock.uptimeMillis();
        float t = (now - born) / 1000f;
        canvas.save();
        if (shakeAt >= 0 && now - shakeAt < 500) {
            float k = 1 - (now - shakeAt) / 500f;
            canvas.translate((random.nextFloat() - .5f) * 18 * d * k, (random.nextFloat() - .5f) * 18 * d * k);
        }

        fill.setShader(space);
        canvas.drawRect(-30 * d, -30 * d, w + 30 * d, h + 30 * d, fill);
        fill.setShader(null);
        nebula(canvas, w * .85f, h * .2f, w * .6f, 0x302B6B5A);
        nebula(canvas, w * .1f, h * .75f, w * .7f, 0x284A2D7A);

        for (int i = 0; i < starX.length; i++) {
            float a = .35f + .65f * (.5f + .5f * (float) Math.sin(t * 1.3f + starPhase[i]));
            fill.setColor(Color.argb(Math.round(255 * a), 235, 245, 240));
            canvas.drawCircle(starX[i], starY[i], starSize[i], fill);
        }
        shootingStar(canvas, now, w, h);

        chalk.setTextSize(16 * d);
        canvas.drawText(hint, 22 * d, top + 22 * d, chalk);

        for (int i = 0; i < people.size(); i++) drawName(canvas, i, now);
        drawEggs(canvas, now);

        // the lower sky: ways to support, each in a lane of its own
        float lanes = h * .68f;
        chalk.setTextSize(28 * d);
        canvas.drawText(supportTitle, 22 * d, lanes, chalk);
        float laneStep = (h - 36 * d - (lanes + 30 * d)) / Math.max(1, planets.size());
        for (int i = 0; i < planets.size(); i++)
            drawPlanet(canvas, i, t, w, lanes + 30 * d + laneStep * (i + .5f));

        drawGhost(canvas, now, w);
        drawCats(canvas, now, w, h);
        drawShards(canvas, h);
        drawRings(canvas, now);
        drawBits(canvas);
        canvas.restore();
        if (flashAt >= 0) {
            float p = (now - flashAt) / 420f;
            if (p >= 1) flashAt = -1;
            else {
                fill.setColor(Color.argb(Math.round(230 * (1 - p) * (1 - p)), 255, 250, 235));
                canvas.drawRect(0, 0, w, h, fill);
            }
        }
        postInvalidateOnAnimation();
    }

    private void nebula(Canvas canvas, float x, float y, float radius, int colour) {
        fill.setShader(new RadialGradient(x, y, radius, colour, colour & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, fill);
        fill.setShader(null);
    }

    private void shootingStar(Canvas canvas, long now, int w, int h) {
        if (shooting < 0 || now - shooting > 900) {
            if (random.nextFloat() < .005f) {
                shooting = now;
                shootX = w * (.3f + random.nextFloat() * .7f);
                shootY = h * random.nextFloat() * .5f;
            }
            return;
        }
        float p = (now - shooting) / 900f;
        float x = shootX - p * 300 * d, y = shootY + p * 150 * d;
        line.setShader(new LinearGradient(x, y, x + 80 * d, y - 40 * d, 0xCCFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        line.setStrokeWidth(2 * d);
        canvas.drawLine(x, y, x + 80 * d, y - 40 * d, line);
        line.setShader(null);
    }

    private void drawName(Canvas canvas, int i, long now) {
        Person person = people.get(i);
        float x = nameX[i], y = nameY[i];
        chalk.setTextSize(nameSize[i]);
        float width = chalk.measureText(person.name);
        nameBoxes[i].set(x - 12 * d, y - nameSize[i] * 1.05f, x + width + 12 * d, y + 24 * d);
        canvas.save();
        canvas.rotate(nameTilt[i], x + width / 2, y - nameSize[i] / 3);
        if (people.get(i).egg == EGG_SCATTER && eggAt[i] >= 0) drawScattered(canvas, i, now, x, y);
        else canvas.drawText(person.name, x, y, chalk);
        chalk.setTextSize(Math.min(16 * d, nameSize[i] * .5f));
        String role = person.role.length() > 40 ? person.role.substring(0, 38) + "…" : person.role;
        canvas.drawText(role, x + 2 * d, y + 20 * d, chalk);
        canvas.restore();
    }

    /** The letters of a name fly apart and come back on a spring. */
    private void drawScattered(Canvas canvas, int i, long now, float x, float y) {
        String name = people.get(i).name;
        float p = (now - eggAt[i]) / 2200f;
        if (p >= 1) { eggAt[i] = -1; canvas.drawText(name, x, y, chalk); return; }
        float out = p < .35f ? (float) Math.sin(p / .35f * Math.PI / 2) : 1f;
        float back = p < .35f ? 0 : (float) (1 - Math.exp(-7 * (p - .35f)) * Math.cos(16 * (p - .35f)));
        float k = out * (1 - back);
        float cursor = x;
        for (int c = 0; c < name.length(); c++) {
            String letter = name.substring(c, c + 1);
            float lw = chalk.measureText(letter);
            float[] s = scatter[c % scatter.length];
            canvas.save();
            canvas.translate(s[0] * k, s[1] * k);
            canvas.rotate(s[2] * k, cursor + lw / 2, y - nameSize[i] / 3);
            canvas.drawText(letter, cursor, y, chalk);
            canvas.restore();
            cursor += lw;
        }
    }

    private void drawPlanet(Canvas canvas, int i, float t, int w, float y) {
        Planet planet = planets.get(i);
        float radius = 20 * d;
        chalk.setTextSize(19 * d);
        float label = chalk.measureText(planet.name);
        float span = w + radius * 4 + label;
        float x = ((t * 26 * d + i * span * .37f) % span) - radius * 2;
        y += 5 * d * (float) Math.sin(t * .9f + i);
        box.set(x - radius * 1.7f, y - radius * .5f, x + radius * 1.7f, y + radius * .5f);
        line.setStrokeWidth(2.2f * d);
        line.setColor(0x99FFFFFF);
        // the ring passes behind the planet at the top and in front of it at the bottom
        canvas.drawArc(box, 180, 180, false, line);
        fill.setShader(new RadialGradient(x - radius * .35f, y - radius * .35f, radius * 1.4f,
                planet.colour | 0xFF000000, darker(planet.colour), Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, fill);
        fill.setShader(null);
        canvas.drawArc(box, 0, 180, false, line);
        chalk.setTextSize(19 * d);
        canvas.drawText(planet.name, x + radius * 2.1f, y + 6 * d, chalk);
        planetBoxes[i].set(x - radius * 1.8f, y - radius * 1.5f, x + radius * 2.2f + label, y + radius * 1.5f);
    }

    private static int darker(int colour) {
        return Color.argb(255, Color.red(colour) / 3, Color.green(colour) / 3, Color.blue(colour) / 3);
    }

    // ------------------------------------------------------------ touch

    private int pressed = -1;
    private boolean held;
    private final Runnable hold = this::held;

    private void held() {
        if (pressed >= 0 && pressed < people.size() && people.get(pressed).hold != null) {
            held = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            people.get(pressed).hold.run();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                held = false;
                pressed = -1;
                for (int i = 0; i < nameBoxes.length; i++) if (nameBoxes[i].contains(x, y)) pressed = i;
                if (pressed >= 0) postDelayed(hold, 520);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);
                if (held) return true;
                if (pressed >= 0 && nameBoxes[pressed].contains(x, y)) {
                    egg(pressed);
                    return true;
                }
                for (int i = 0; i < planetBoxes.length; i++) {
                    if (planetBoxes[i].contains(x, y)) {
                        sparks(planetBoxes[i].left + 34 * d, planetBoxes[i].centerY(), planets.get(i).colour, 16);
                        Runnable open = planets.get(i).open;
                        if (open != null) postDelayed(open, 220);
                        return true;
                    }
                }
                sparks(x, y, 0xFFFFFFFF, 6);
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                pressed = -1;
                return true;
            default:
                return true;
        }
    }

    // ------------------------------------------------------------ the eggs

    private void egg(int i) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        long now = SystemClock.uptimeMillis();
        switch (people.get(i).egg) {
            case EGG_TILCAYO:
                // nothing at first; then they come, more and more, and then they all go off
                if (tilcayoAt < 0) tilcayoAt = now + 900;
                break;
            case EGG_SCATTER:
                scatter = new float[people.get(i).name.length() + 1][];
                for (int c = 0; c < scatter.length; c++)
                    scatter[c] = new float[] {(random.nextFloat() - .5f) * 220 * d, (random.nextFloat() - .6f) * 180 * d,
                            (random.nextFloat() - .5f) * 540};
                eggAt[i] = now;
                break;
            case EGG_GHOST:
                ghostAt = now;
                ghostY = nameBoxes[i].centerY();
                break;
            default:
                eggAt[i] = now;
                released[i] = false;
        }
    }

    private void drawEggs(Canvas canvas, long now) {
        for (int i = 0; i < people.size(); i++) {
            if (eggAt[i] < 0) continue;
            int egg = people.get(i).egg;
            float since = (now - eggAt[i]) / 1000f;
            RectF at = nameBoxes[i];
            if (egg == EGG_CONSTELLATION) { if (!constellation(canvas, at, since)) eggAt[i] = -1; }
            else if (egg == EGG_LOTUS) { if (!lotus(canvas, i, at, since)) eggAt[i] = -1; }
            else if (egg == EGG_HEART) { if (!chalkHeart(canvas, i, at, since)) eggAt[i] = -1; }
        }
    }

    /**
     * Claude's spark, drawn in the stars: eight rays grow out of a point, their
     * ends become stars, it glows, and the stars go out one by one.
     */
    private boolean constellation(Canvas canvas, RectF at, float since) {
        if (since > 3.4f) return false;
        float cx = Math.min(getWidth() - 90 * d, at.right + 60 * d), cy = at.centerY();
        int rays = 8;
        float grow = Math.min(1f, since / .9f);
        float fade = since < 2.4f ? 1f : 1f - (since - 2.4f);
        for (int r = 0; r < rays; r++) {
            double a = Math.PI * 2 * r / rays - Math.PI / 2;
            float length = (r % 2 == 0 ? 74 : 52) * d * ease(Math.min(1f, Math.max(0f, grow * 1.4f - r * .05f)));
            float ex = cx + (float) Math.cos(a) * length, ey = cy + (float) Math.sin(a) * length;
            line.setStrokeWidth(3 * d);
            line.setColor(Color.argb(Math.round(220 * fade), 217, 119, 87));
            canvas.drawLine(cx, cy, ex, ey, line);
            float twinkle = .7f + .3f * (float) Math.sin(since * 9 + r);
            float alive = since < 2.4f ? 1f : Math.max(0f, 1f - (since - 2.4f - r * .08f) * 2.5f);
            fill.setColor(Color.argb(Math.round(255 * alive), 255, 226, 200));
            canvas.drawCircle(ex, ey, 4.5f * d * twinkle * (grow < 1 ? grow : 1), fill);
        }
        nebula(canvas, cx, cy, 70 * d * grow, Color.argb(Math.round(110 * fade), 217, 119, 87));
        return true;
    }

    /** A lotus opens behind the name, then lets its petals fall. */
    private boolean lotus(Canvas canvas, int i, RectF at, float since) {
        if (since > 4.2f) return false;
        float cx = at.centerX(), cy = at.centerY() + 6 * d;
        float open = ease(Math.min(1f, since / 1.1f));
        if (since < 1.9f) {
            for (int layer = 2; layer >= 0; layer--) {
                int petals = 6 + layer * 2;
                float size = (54 - layer * 13) * d * open;
                int colour = layer == 0 ? 0xFFFFE3EF : layer == 1 ? 0xFFF7A8CC : 0xFFE0679E;
                for (int p = 0; p < petals; p++) {
                    float angle = 360f * p / petals + layer * 14 + since * 12;
                    canvas.save();
                    canvas.rotate(angle, cx, cy);
                    box.set(cx - size * .26f, cy - size, cx + size * .26f, cy);
                    fill.setColor((colour & 0x00FFFFFF) | 0xE0000000);
                    canvas.drawOval(box, fill);
                    canvas.restore();
                }
            }
            fill.setColor(0xFFFFE89A);
            canvas.drawCircle(cx, cy, 7 * d * open, fill);
        } else if (!released[i]) {
            released[i] = true;
            // the flower lets go: every petal becomes one falling on its own
            for (int p = 0; p < 22; p++) {
                double a = random.nextFloat() * Math.PI * 2;
                float r = random.nextFloat() * 50 * d;
                bits.add(new float[] {cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r,
                        (random.nextFloat() - .5f) * 1.6f * d, (random.nextFloat() * .6f) * d, 1f, 2,
                        p % 3 == 0 ? 0xFFFFE3EF : 0xFFF7A8CC, (7 + random.nextFloat() * 5) * d,
                        (random.nextFloat() - .5f) * 8, random.nextFloat() * 360});
            }
        }
        return true;
    }

    /** A heart drawn round the name in chalk, which beats twice and crumbles to dust. */
    private boolean chalkHeart(Canvas canvas, int i, RectF at, float since) {
        if (since > 3.6f) return false;
        float cx = at.centerX(), cy = at.centerY() - 4 * d;
        float s = Math.max(at.width() * .62f, 70 * d);
        path.reset();
        path.moveTo(cx, cy + s * .62f);
        path.cubicTo(cx - s * 1.25f, cy - s * .05f, cx - s * .55f, cy - s * .95f, cx, cy - s * .32f);
        path.cubicTo(cx + s * .55f, cy - s * .95f, cx + s * 1.25f, cy - s * .05f, cx, cy + s * .62f);
        float beat = since > 1.2f && since < 2.4f ? 1 + .08f * (float) Math.abs(Math.sin((since - 1.2f) * Math.PI * 2.5)) : 1f;
        if (since < 2.6f) {
            PathMeasure measure = new PathMeasure(path, false);
            Path drawn = new Path();
            measure.getSegment(0, measure.getLength() * ease(Math.min(1f, since / 1.1f)), drawn, true);
            canvas.save();
            canvas.scale(beat, beat, cx, cy);
            line.setStrokeWidth(5 * d);
            line.setShader(chalk.getShader());
            line.setColor(0xFFFFB3C9);
            canvas.drawPath(drawn, line);
            line.setShader(null);
            canvas.restore();
        } else if (!released[i]) {
            released[i] = true;
            PathMeasure measure = new PathMeasure(path, false);
            float[] pos = new float[2];
            for (int k = 0; k < 70; k++) {
                measure.getPosTan(measure.getLength() * k / 70f, pos, null);
                bits.add(new float[] {pos[0], pos[1], (random.nextFloat() - .5f) * 1.2f * d, random.nextFloat() * .8f * d, 1f, 3,
                        0xFFFFD6E2, (1.4f + random.nextFloat() * 2.2f) * d, 0, 0});
            }
        }
        return true;
    }

    private static float ease(float p) {
        return 1 - (1 - p) * (1 - p) * (1 - p);
    }

    /** Sparks from a point. */
    private void sparks(float x, float y, int colour, int count) {
        for (int k = 0; k < count; k++) {
            double a = random.nextFloat() * Math.PI * 2;
            float speed = (1.5f + random.nextFloat() * 4f) * d;
            bits.add(new float[] {x, y, (float) Math.cos(a) * speed, (float) Math.sin(a) * speed, 1f, 0, colour,
                    (1.6f + random.nextFloat() * 2.6f) * d, 0, 0});
        }
    }

    /** kind 0 a spark, 2 a falling petal, 3 chalk dust. */
    private void drawBits(Canvas canvas) {
        for (int i = bits.size() - 1; i >= 0; i--) {
            float[] b = bits.get(i);
            b[0] += b[2]; b[1] += b[3];
            if (b[5] == 0) { b[2] *= .95f; b[3] = b[3] * .95f + .06f * d; b[4] -= .02f; }
            else if (b[5] == 2) { b[2] += (float) Math.sin(b[9] / 30f) * .05f * d; b[3] = Math.min(b[3] + .02f * d, 1.4f * d); b[9] += b[8]; b[4] -= .005f; }
            else { b[3] += .08f * d; b[4] -= .012f; }
            if (b[4] <= 0) { bits.remove(i); continue; }
            fill.setColor(((int) b[6] & 0x00FFFFFF) | (Math.round(255 * Math.min(1f, b[4] * 1.4f)) << 24));
            if (b[5] == 2) {
                box.set(b[0] - b[7], b[1] - b[7] * .42f, b[0] + b[7], b[1] + b[7] * .42f);
                canvas.save();
                canvas.rotate(b[9], b[0], b[1]);
                canvas.drawOval(box, fill);
                canvas.restore();
            } else canvas.drawCircle(b[0], b[1], b[7], fill);
        }
    }

    private void drawRings(Canvas canvas, long now) {
        for (int i = rings.size() - 1; i >= 0; i--) {
            float[] r = rings.get(i);
            float p = (now - r[2]) / 700f;
            if (p >= 1) { rings.remove(i); continue; }
            line.setStrokeWidth(6 * d * (1 - p));
            line.setColor(((int) r[3] & 0x00FFFFFF) | (Math.round(220 * (1 - p)) << 24));
            canvas.drawCircle(r[0], r[1], 20 * d + p * 260 * d, line);
        }
    }

    private void drawGhost(Canvas canvas, long now, int w) {
        if (ghostAt < 0) return;
        float p = (now - ghostAt) / 3200f;
        if (p >= 1) { ghostAt = -1; return; }
        float x = -60 * d + p * (w + 120 * d);
        float y = ghostY + 14 * d * (float) Math.sin(p * 18);
        int alpha = Math.round(220 * Math.min(1f, Math.min(p * 6, (1 - p) * 6)));
        float s = 24 * d;
        path.reset();
        path.moveTo(x - s, y + s);
        path.lineTo(x - s, y - s * .2f);
        path.cubicTo(x - s, y - s * 1.5f, x + s, y - s * 1.5f, x + s, y - s * .2f);
        path.lineTo(x + s, y + s);
        for (int k = 0; k < 4; k++) {
            float x0 = x + s - k * s / 2, wobble = (float) Math.sin(now / 120f + k) * 3 * d;
            path.quadTo(x0 - s / 4, y + s * .7f + wobble, x0 - s / 2, y + s);
        }
        path.close();
        fill.setColor((alpha << 24) | 0xF2F4FF);
        canvas.drawPath(path, fill);
        fill.setColor((alpha << 24) | 0x0B1730);
        canvas.drawCircle(x - s * .35f, y - s * .25f, 3.5f * d, fill);
        canvas.drawCircle(x + s * .35f, y - s * .25f, 3.5f * d, fill);
    }

    /**
     * The tilcayos: a trickle, then a flood that fills the sky, bouncing off
     * everything; then all at once a white flash, a ring of fire from each of
     * them, and every tilcayo in four pieces flying off, with the sky shaking.
     */
    private void drawCats(Canvas canvas, long now, int w, int h) {
        if (tilcayoAt < 0 || now < tilcayoAt) return;
        if (tilcayo == null) tilcayo = TilcayoRun.sprite(getContext());
        if (tilcayo == null) { tilcayoAt = -1; return; }
        float since = (now - tilcayoAt) / 1000f;
        if (since < 4.4f) {
            int want = Math.min(80, Math.round((float) Math.pow(2.7, since) * 2));
            while (cats.size() < want) {
                float size = (36 + random.nextFloat() * 34) * d;
                cats.add(new float[] {random.nextFloat() * (w - size), -size - random.nextFloat() * 80 * d,
                        (random.nextFloat() - .5f) * 8 * d, (2 + random.nextFloat() * 4) * d,
                        (random.nextFloat() - .5f) * 9, random.nextFloat() * 360, size});
            }
        } else {
            explode();
            return;
        }
        // just before they go: they shiver
        float tremble = since > 3.8f ? (since - 3.8f) / .6f : 0;
        Rect from = new Rect(0, 0, tilcayo.getWidth(), tilcayo.getHeight());
        float aspect = tilcayo.getHeight() / (float) tilcayo.getWidth();
        for (float[] c : cats) {
            c[0] += c[2]; c[1] += c[3]; c[3] += .38f * d; c[5] += c[4];
            float size = c[6];
            if (c[1] + size * aspect > h) { c[1] = h - size * aspect; c[3] = -Math.abs(c[3]) * .84f - 2 * d; }
            if (c[0] < 0) { c[0] = 0; c[2] = Math.abs(c[2]); }
            if (c[0] + size > w) { c[0] = w - size; c[2] = -Math.abs(c[2]); }
            float jx = (random.nextFloat() - .5f) * 6 * d * tremble, jy = (random.nextFloat() - .5f) * 6 * d * tremble;
            canvas.save();
            canvas.rotate(c[5], c[0] + size / 2, c[1] + size * aspect / 2);
            box.set(c[0] + jx, c[1] + jy, c[0] + size + jx, c[1] + size * aspect + jy);
            canvas.drawBitmap(tilcayo, from, box, sprite);
            canvas.restore();
        }
    }

    private void explode() {
        long now = SystemClock.uptimeMillis();
        float aspect = tilcayo.getHeight() / (float) tilcayo.getWidth();
        for (float[] c : cats) {
            float size = c[6], cx = c[0] + size / 2, cy = c[1] + size * aspect / 2;
            for (int q = 0; q < 4; q++) {
                double a = Math.PI / 4 + Math.PI / 2 * q + (random.nextFloat() - .5f) * .6f;
                float speed = (5 + random.nextFloat() * 7) * d;
                shards.add(new float[] {cx, cy, (float) Math.cos(a) * speed, (float) Math.sin(a) * speed - 3 * d,
                        (random.nextFloat() - .5f) * 24, c[5], size, q, 1f});
            }
            sparks(cx, cy, 0xFFFFC14D, 5);
            sparks(cx, cy, 0xFFFF6A3D, 4);
            if (rings.size() < 14) rings.add(new float[] {cx, cy, now + random.nextInt(160), 0xFFFFD27A});
        }
        cats.clear();
        tilcayoAt = -1;
        flashAt = now;
        shakeAt = now;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    /** The four pieces of each tilcayo, flying and falling and fading. */
    private void drawShards(Canvas canvas, int h) {
        if (shards.isEmpty() || tilcayo == null) return;
        int bw = tilcayo.getWidth() / 2, bh = tilcayo.getHeight() / 2;
        float aspect = tilcayo.getHeight() / (float) tilcayo.getWidth();
        Rect from = new Rect();
        for (int i = shards.size() - 1; i >= 0; i--) {
            float[] s = shards.get(i);
            s[0] += s[2]; s[1] += s[3]; s[3] += .3f * d; s[2] *= .99f; s[5] += s[4]; s[8] -= .012f;
            if (s[8] <= 0 || s[1] > h + 100 * d) { shards.remove(i); continue; }
            int q = (int) s[7];
            from.set((q % 2) * bw, (q / 2) * bh, (q % 2) * bw + bw, (q / 2) * bh + bh);
            float half = s[6] / 2;
            sprite.setAlpha(Math.round(255 * Math.min(1f, s[8] * 1.5f)));
            canvas.save();
            canvas.rotate(s[5], s[0], s[1]);
            box.set(s[0] - half / 2, s[1] - half * aspect / 2, s[0] + half / 2, s[1] + half * aspect / 2);
            canvas.drawBitmap(tilcayo, from, box, sprite);
            canvas.restore();
        }
        sprite.setAlpha(255);
    }
}
