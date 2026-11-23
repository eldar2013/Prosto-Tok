package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;
import android.view.animation.AnimationUtils;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * The shape a round picture is cut to.
 *
 * TikTok does not cut avatars round itself: the image loader does, and it
 * does it in three drawables that all come to the same line -- add a circle
 * to a path, then fill the path with the picture. The build sends that one
 * call here, and what is added instead is whatever shape was chosen. The
 * picture is still drawn by the loader, with its own smoothing and its own
 * border, which now follows the shape because it is built by the same call.
 *
 * It is every picture the loader cuts round, not only faces: the loader does
 * not know what it is drawing, and nearly everything round in the app is an
 * avatar.
 */
public final class AvatarShape {

    private AvatarShape() {}

    static final String KEY = "avatar_shape";
    static final String[] ALL = {
            "circle", "square", "clover"};

    /** How finely an outline given as a distance in each direction is traced. */
    private static final int POINTS = 144;

    /** -1 until the setting has been read. */
    private static volatile int chosen = -1;
    private static final Path[] UNIT = new Path[ALL.length];
    private static final Matrix PLACE = new Matrix();

    static String chosen() {
        return ALL[index()];
    }

    static void choose(Context context, String shape) {
        int found = 0;
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(shape)) found = i;
        chosen = found;
        context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, ALL[found]).apply();
    }

    static void reloadSettings() {
        chosen = -1;
        spin = -1;
    }

    private static int index() {
        int known = chosen;
        if (known >= 0) return known;
        Context context = Margy.context();
        if (context == null) return 0;  // too early to know; a circle, and ask again
        int found = 0;
        try {
            SharedPreferences prefs = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
            String name = prefs.getString(KEY, ALL[0]);
            for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(name)) found = i;
        } catch (Throwable ignored) { }
        chosen = found;
        return found;
    }

    /** What the image loader calls where it used to add a circle to its path. */
    public static void addCircle(Path path, float x, float y, float radius, Path.Direction direction) {
        int shape = index();
        if (shape > 0 && radius > 0) {
            try {
                Path unit = unit(shape);
                synchronized (PLACE) {
                    PLACE.setScale(radius, radius);
                    PLACE.postTranslate(x, y);
                    path.addPath(unit, PLACE);
                }
                synchronized (SHAPED) {
                    SHAPED.put(path, new float[]{x, y, radius});
                }
                return;
            } catch (Throwable error) {
                // a shape that cannot be drawn is a circle
            }
        }
        path.addCircle(x, y, radius, direction);
    }

    // ------------------------------------------------------- the story ring

    private static final Path RING = new Path();
    private static final Path PART = new Path();
    private static final Matrix RING_PLACE = new Matrix();
    private static final RectF INBOX_RING = new RectF();

    /**
     * What the view that draws a story ring calls where it used to draw an arc.
     *
     * The ring is not a picture and is not cut out of anything: it is a view
     * of its own over the avatar that strokes an arc round an oval, all the
     * way round for one story and in pieces for several or while one loads.
     * So the arc is what takes the avatar's shape -- the outline of the
     * chosen shape fitted to the same oval, or as much of it as the arc swept,
     * drawn with the same paint and so in the same colours.
     */
    public static void drawArc(Canvas canvas, RectF oval, float start, float sweep,
                               boolean useCentre, Paint paint) {
        Object who = takeOwner();
        if (oval != null && !useCentre && InboxMd3.storyRing(who)) {
            synchronized (RING) {
                float nativeStroke = paint.getStrokeWidth();
                float stroke = 2f * ((View) who).getResources().getDisplayMetrics().density;
                INBOX_RING.set(oval);
                float inset = (stroke - nativeStroke) / 2f;
                INBOX_RING.inset(inset, inset);
                paint.setStrokeWidth(stroke);
                try { storyArc(canvas, INBOX_RING, start, sweep, false, paint, who); }
                finally { paint.setStrokeWidth(nativeStroke); }
            }
            return;
        }
        storyArc(canvas, oval, start, sweep, useCentre, paint, who);
    }

    private static void storyArc(Canvas canvas, RectF oval, float start, float sweep,
                                 boolean useCentre, Paint paint, Object who) {
        int shape = index();
        if (shape > 0 && !useCentre && oval != null && sweep != 0) {
            try {
                synchronized (RING) {
                    RING.rewind();
                    RING_PLACE.setScale(oval.width() / 2f, oval.height() / 2f);
                    RING_PLACE.postTranslate(oval.centerX(), oval.centerY());
                    RING.addPath(unit(shape), RING_PLACE);
                    // the ring turns with the avatar, so the two stay one shape
                    float turn = 0;
                    if (spins()) {
                        turn = angle();
                        RING_PLACE.setRotate(turn, oval.centerX(), oval.centerY());
                        RING.transform(RING_PLACE);
                        again(who);
                    }
                    if (Math.abs(sweep) >= 359.5f) {
                        canvas.drawPath(RING, paint);
                        return;
                    }
                    // a piece of it: as far round the outline as the arc was round the circle.
                    // An arc is measured from three o'clock; the outlines here begin at the top.
                    android.graphics.PathMeasure measure = new android.graphics.PathMeasure(RING, true);
                    float length = measure.getLength();
                    float from = start + (sweep < 0 ? sweep : 0) + 90f - turn;
                    from = ((from % 360f) + 360f) % 360f / 360f * length;
                    float span = Math.min(Math.abs(sweep), 360f) / 360f * length;
                    PART.rewind();
                    if (from + span <= length) {
                        measure.getSegment(from, from + span, PART, true);
                    } else {
                        measure.getSegment(from, length, PART, true);
                        measure.getSegment(0, from + span - length, PART, true);
                    }
                    canvas.drawPath(PART, paint);
                    return;
                }
            } catch (Throwable error) {
                // a ring left round is better than no ring
            }
        }
        canvas.drawArc(oval, start, sweep, useCentre, paint);
    }

    // ------------------------------------------------------------ the spin

    static final String SPIN_KEY = "avatar_spin";
    /** One turn of the shape takes this long. */
    private static final long TURN = 9000;

    /** -1 until the setting has been read. */
    private static volatile int spin = -1;

    /**
     * Paths the loader asked for a circle in, and where: the centre and the
     * radius. Only these turn -- a thumbnail cut with rounded corners is in
     * the same drawables and must stay as it is. The loader empties a path
     * before it builds it again, and that call comes here too, so a path
     * that is not a shape any more is forgotten.
     */
    private static final WeakHashMap<Path, float[]> SHAPED = new WeakHashMap<>();

    static boolean spins() {
        int known = spin;
        if (known < 0) {
            Context context = Margy.context();
            if (context == null) return false;
            try {
                known = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .getBoolean(SPIN_KEY, false) ? 1 : 0;
            } catch (Throwable error) {
                known = 0;
            }
            spin = known;
        }
        return known == 1;
    }

    static void setSpin(Context context, boolean on) {
        spin = on ? 1 : 0;
        context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(SPIN_KEY, on).apply();
    }

    /** How far round the shape is now, in degrees. Every avatar on the screen is at the same place. */
    static float angle() {
        return (AnimationUtils.currentAnimationTimeMillis() % TURN) * 360f / TURN;
    }

    /** Whether there is anything to turn: a circle turned is the same circle. */
    static boolean turning() {
        return index() > 0 && spins();
    }

    /** What the image loader calls where it used to empty a path. */
    public static void reset(Path path) {
        synchronized (SHAPED) {
            SHAPED.remove(path);
        }
        path.reset();
    }

    private static Object owner;

    /**
     * Who is about to draw: called just before each of the calls below, by
     * the drawable or the view that makes it. A turned shape has to be drawn
     * again on the next frame, and the call itself has no way to say whose
     * it is.
     */
    public static void owner(Object who) {
        owner = who;
    }

    private static Object takeOwner() {
        Object who = owner;
        owner = null;
        return who;
    }

    private static final Path TURNED = new Path();
    private static final Path PLACED = new Path();
    private static final Matrix TURN_PLACE = new Matrix();
    private static final RectF WHOLE = new RectF();
    private static final RectF SHAPE = new RectF();

    /**
     * The path as it is to be drawn now: the same path, or a copy with the
     * shape in it turned round its centre. Only the outline turns. The
     * picture is the paint's, laid on the canvas, so it stays still and the
     * shape moves over it.
     */
    private static Path turned(Path path) {
        if (!turning()) return path;
        float[] at;
        synchronized (SHAPED) {
            at = SHAPED.get(path);
        }
        if (at == null) return path;
        TURN_PLACE.setScale(at[2], at[2]);
        TURN_PLACE.postTranslate(at[0], at[1]);
        PLACED.rewind();
        PLACED.addPath(unit(index()), TURN_PLACE);
        TURN_PLACE.setRotate(angle(), at[0], at[1]);
        path.computeBounds(WHOLE, true);
        PLACED.computeBounds(SHAPE, true);
        TURNED.set(path);
        if (Math.abs(WHOLE.left - SHAPE.left) < 1 && Math.abs(WHOLE.top - SHAPE.top) < 1
                && Math.abs(WHOLE.right - SHAPE.right) < 1 && Math.abs(WHOLE.bottom - SHAPE.bottom) < 1) {
            TURNED.transform(TURN_PLACE);
        } else {
            // the shape cut out of something larger, for a colour laid round
            // it: take the shape out where it was and put it back turned
            TURNED.op(PLACED, Path.Op.XOR);
            PLACED.transform(TURN_PLACE);
            TURNED.op(PLACED, Path.Op.XOR);
        }
        return TURNED;
    }

    /** What the image loader calls where it used to fill or stroke its path. */
    public static void drawPath(Canvas canvas, Path path, Paint paint) {
        Object who = takeOwner();
        synchronized (TURNED) {
            Path drawn = path;
            try {
                drawn = turned(path);
            } catch (Throwable error) {
                // a shape that cannot turn is drawn standing
            }
            canvas.drawPath(drawn, paint);
            if (drawn != path) again(who);
        }
    }

    /** The same, for the loader that cuts the picture by clipping to the path. */
    public static boolean clipPath(Canvas canvas, Path path) {
        Object who = takeOwner();
        synchronized (TURNED) {
            Path drawn = path;
            try {
                drawn = turned(path);
            } catch (Throwable error) {
                // a shape that cannot turn is drawn standing
            }
            boolean left = canvas.clipPath(drawn);
            if (drawn != path) again(who);
            return left;
        }
    }

    private static final WeakHashMap<Object, Boolean> WAITING = new WeakHashMap<>();
    private static boolean posted;

    /**
     * Something with a turned shape is to be drawn again on the next frame.
     * Whatever was not drawn this frame -- scrolled away, closed -- is not
     * asked again, so the turning stops by itself once nothing is on screen.
     */
    static void again(Object who) {
        if (who == null || Looper.myLooper() != Looper.getMainLooper()) return;
        WAITING.put(who, Boolean.TRUE);
        if (posted) return;
        posted = true;
        // a whole turn takes nine seconds: thirty steps a second look as smooth as a hundred and twenty,
        // and on a 120 Hz screen every frame was redrawing every avatar on it
        Choreographer.getInstance().postFrameCallbackDelayed(TICK, 30L);
    }

    private static final Choreographer.FrameCallback TICK = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            posted = false;
            if (!turning()) {
                WAITING.clear();
                return;
            }
            ArrayList<Object> due = new ArrayList<>(WAITING.keySet());
            WAITING.clear();
            for (Object who : due) {
                try {
                    if (who instanceof View) ((View) who).invalidate();
                    else if (who instanceof Drawable) ((Drawable) who).invalidateSelf();
                } catch (Throwable ignored) { }
            }
        }
    };

    /** The shape avatars are in at the moment, in a square two across. */
    static Path chosenUnit() {
        return unit(index());
    }

    /** A shape in a square two across, centred on nothing. Built once and never changed. */
    static Path unit(int shape) {
        Path known = UNIT[shape];
        if (known != null) return known;
        Path made = new Path();
        switch (ALL[shape]) {
            case "square":
                made.addRect(-1, -1, 1, 1, Path.Direction.CW);
                break;
            case "clover":
                scalloped(made, 4, 0.70f);
                break;
            case "sun":
                scalloped(made, 8, 0.78f);
                break;
            case "burst":
                scalloped(made, 12, 0.82f);
                break;
            case "cookie":
                // Prosto Launcher COOKIE_9: a clean nine-sided polygon.
                for (int i = 0; i < 9; i++) {
                    double angle = 2 * Math.PI * i / 9.0 - Math.PI / 2.0;
                    float x = (float) Math.cos(angle);
                    float y = (float) Math.sin(angle);
                    if (i == 0) made.moveTo(x, y); else made.lineTo(x, y);
                }
                made.close();
                break;
            default:
                made.addCircle(0, 0, 1, Path.Direction.CW);
                break;
        }
        UNIT[shape] = made;
        return made;
    }

    /** Same rounded scallop geometry as Prosto Launcher icon masks. */
    private static void scalloped(Path path, int lobes, float innerRadius) {
        float[][] points = new float[lobes * 2][2];
        for (int i = 0; i < points.length; i++) {
            double angle = Math.PI * 2.0 * i / points.length - Math.PI / 2.0;
            float radius = (i % 2 == 0) ? 1f : innerRadius;
            points[i][0] = radius * (float) Math.cos(angle);
            points[i][1] = radius * (float) Math.sin(angle);
        }
        float firstX = (points[points.length - 1][0] + points[0][0]) / 2f;
        float firstY = (points[points.length - 1][1] + points[0][1]) / 2f;
        path.moveTo(firstX, firstY);
        for (int i = 0; i < points.length; i++) {
            int next = (i + 1) % points.length;
            float midX = (points[i][0] + points[next][0]) / 2f;
            float midY = (points[i][1] + points[next][1]) / 2f;
            path.quadTo(points[i][0], points[i][1], midX, midY);
        }
        path.close();
    }

    /** A shape filled with one colour, for the list it is chosen from. */
    static Bitmap picture(String shape, int size, int colour) {
        int which = 0;
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(shape)) which = i;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        Path placed = new Path();
        Matrix place = new Matrix();
        place.setScale(size / 2f - 1, size / 2f - 1);
        place.postTranslate(size / 2f, size / 2f);
        placed.addPath(unit(which), place);
        canvas.drawPath(placed, paint);
        return bitmap;
    }

    static String nameOf(String shape) {
        switch (shape) {
            case "square": return Text.SHAPE_SQUARE;
            case "cookie": return Text.SHAPE_COOKIE;
            case "clover": return Text.SHAPE_CLOVER;
            case "burst": return Text.SHAPE_BURST;
            case "sun": return Text.SHAPE_SUN;
            default: return Text.SHAPE_CIRCLE;
        }
    }
}
