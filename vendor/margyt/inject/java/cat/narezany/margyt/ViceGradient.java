package cat.narezany.margyt;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * The Vice City theme's background: one sunset across the whole screen rather than a colour.
 *
 * Every surface the theme would paint in its ground colour gets one of these instead, and each draws the part
 * of a single screen-sized gradient that lies under it -- so the page, its header and a sheet over it read as one
 * sky and not as boxes, each with its own fade. With the theme changed or switched off it draws what the theme
 * would have painted there, so nothing is left stuck until a restart.
 */
final class ViceGradient extends Drawable {

    /** Night: indigo at the top left to plum at the bottom right, dark enough for white text. */
    private static final int[] DARK = {0xFF1A1450, 0xFF2C1350, 0xFF481444};
    /** Day: lavender to sunset pink, light enough for dark text. */
    private static final int[] LIGHT = {0xFFE7E4FF, 0xFFF5E2F8, 0xFFFFDCEA};
    private static final float[] STOPS = {0f, 0.55f, 1f};
    /** The same sunset a shade deeper, for the cards and buttons that sit on it. */
    private static final int[] DARK_CARD = {0xFF100C38, 0xFF1E0E3A, 0xFF320D31};
    private static final int[] LIGHT_CARD = {0xFFD8D2FF, 0xFFEACFF2, 0xFFFFC8DE};

    /**
     * The card sunset as a shader for something drawn at this place on the screen, or null when the theme is off.
     * Drawn in the drawer's own coordinates, so a card shows the part of the sky that lies under it.
     */
    static Shader cardShader(View view) {
        if (view == null || !Themes.gradientOn()) return null;
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        android.util.DisplayMetrics screen = Resources.getSystem().getDisplayMetrics();
        int w = screen.widthPixels, h = screen.heightPixels;
        return new LinearGradient(-at[0], -at[1], w - at[0], h - at[1],
                Themes.isDark() ? DARK_CARD : LIGHT_CARD, STOPS, Shader.TileMode.CLAMP);
    }

    private final WeakReference<View> owner;
    /** TikTok's own colour that was here, for when the gradient is not wanted any more. */
    private final int was;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The corners of the shape this replaced, as Path.addRoundRect takes them; null for a plain rectangle. */
    private final float[] radii;
    private final android.graphics.Path outline = new android.graphics.Path();
    private final android.graphics.RectF area = new android.graphics.RectF();
    private final int[] at = new int[2];
    private int shaderX = Integer.MIN_VALUE, shaderY, shaderW, shaderH;
    private boolean shaderDark;

    /** What was there before, drawn again whenever the sunset is not wanted; null means "the colour was". */
    private final Drawable original;

    ViceGradient(View view, int was, float[] radii) {
        this(view, was, radii, null);
    }

    ViceGradient(View view, int was, float[] radii, Drawable original) {
        this.owner = new WeakReference<View>(view);
        this.was = was;
        this.radii = radii;
        this.original = original;
    }

    /** Drawables already looked at and found not to be the ground, so they are not drawn out again on every walk. */
    private static final java.util.Map<Drawable, Boolean> CHECKED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Drawable, Boolean>());

    /**
     * A background of a kind the theme cannot read (a sheet's own shape class, say): drawn once into a small
     * picture, and if its middle is the theme's ground, it becomes the sunset, with the corners the picture shows.
     */
    static boolean tryReplace(Drawable drawable, View view) {
        if (drawable == null || view == null || drawable instanceof ViceGradient || !Themes.gradientOn()) return false;
        if (CHECKED.containsKey(drawable)) return false;
        int w = view.getWidth(), h = view.getHeight();
        if (w <= 0 || h <= 0) return false;
        android.util.DisplayMetrics screen = Resources.getSystem().getDisplayMetrics();
        if (w < screen.widthPixels * 0.5f) {
            CHECKED.put(drawable, Boolean.TRUE);
            return false;
        }
        int sw = 48, sh = Math.max(8, Math.min(200, Math.round(48f * h / w)));
        android.graphics.Bitmap picture = android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(picture);
            canvas.scale(sw / (float) w, sh / (float) h);
            Rect kept = drawable.copyBounds();
            drawable.setBounds(0, 0, w, h);
            drawable.draw(canvas);
            drawable.setBounds(kept);
            int middle = picture.getPixel(sw / 2, sh / 2);
            if ((middle >>> 24) < 250 || !close(middle, Themes.backgroundInUse())) {
                CHECKED.put(drawable, Boolean.TRUE);
                return false;
            }
            // each corner: how far in along the diagonal it stays clear says how round it is
            float[] radii = new float[8];
            int[][] corners = {{0, 0, 1, 1}, {sw - 1, 0, -1, 1}, {sw - 1, sh - 1, -1, -1}, {0, sh - 1, 1, -1}};
            boolean round = false;
            for (int c = 0; c < 4; c++) {
                int x = corners[c][0], y = corners[c][1], k = 0, most = Math.min(sw, sh) / 2;
                while (k < most && (picture.getPixel(x + corners[c][2] * k, y + corners[c][3] * k) >>> 24) < 128) k++;
                float r = k <= 0 ? 0f : k / 0.2929f * (w / (float) sw);
                radii[c * 2] = r;
                radii[c * 2 + 1] = r;
                if (r > 0) round = true;
            }
            view.setBackground(new ViceGradient(view, middle, round ? radii : null, drawable));
            guard(view, drawable.getClass(), middle, round ? radii : null);
            return true;
        } catch (Throwable error) {
            CHECKED.put(drawable, Boolean.TRUE);
            return false;
        } finally {
            picture.recycle();
        }
    }

    private static final int GUARDED = 0x56434732;  // "VCG2"

    /** What a guarded view's background was replaced from: its kind, its colour and its corners. */
    private static final class Held {
        final Class<?> kind;
        final int was;
        final float[] radii;
        Held(Class<?> kind, int was, float[] radii) { this.kind = kind; this.was = was; this.radii = radii; }
    }

    /**
     * A sheet TikTok gives a new background of the same kind whenever it opens, settles or moves (comments do):
     * waiting for the next walk showed the plain colour for a moment, and the sheet flickered. Looked at before
     * every frame instead, and wrapped again before the frame is drawn.
     */
    static void guard(final View view, Class<?> kind, int was, float[] radii) {
        boolean first = view.getTag(GUARDED) == null;
        view.setTag(GUARDED, new Held(kind, was, radii));
        if (!first) return;
        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                try {
                    Object held = view.getTag(GUARDED);
                    Drawable now = view.getBackground();
                    if (!(held instanceof Held) || now == null || now instanceof ViceGradient || !Themes.gradientOn()) return true;
                    Held h = (Held) held;
                    if (now.getClass() == h.kind) view.setBackground(new ViceGradient(view, h.was, h.radii, now));
                } catch (Throwable ignored) {
                }
                return true;
            }
        });
    }

    private static boolean close(int one, int two) {
        return Math.abs(((one >> 16) & 0xFF) - ((two >> 16) & 0xFF)) <= 14
                && Math.abs(((one >> 8) & 0xFF) - ((two >> 8) & 0xFF)) <= 14
                && Math.abs((one & 0xFF) - (two & 0xFF)) <= 14;
    }

    /** A shape's corners: its own list of eight, or one radius for all four, or none. */
    static float[] radiiOf(android.graphics.drawable.GradientDrawable shape) {
        if (android.os.Build.VERSION.SDK_INT < 24) return null;
        try {
            float[] each = shape.getCornerRadii();
            if (each != null && each.length == 8) return each.clone();
        } catch (Throwable ignored) {
        }
        float one = shape.getCornerRadius();
        return one > 0 ? new float[] {one, one, one, one, one, one, one, one} : null;
    }

    private void fill(Canvas canvas, Rect box) {
        if (radii == null) {
            canvas.drawRect(box, paint);
            return;
        }
        area.set(box);
        outline.rewind();
        outline.addRoundRect(area, radii, android.graphics.Path.Direction.CW);
        canvas.drawPath(outline, paint);
    }

    /** Whether this colour, about to be put on a view, is the ground the gradient replaces. */
    static boolean wanted(int colour) {
        return Themes.gradientOn() && (colour | 0xFF000000) == (Themes.backgroundInUse() | 0xFF000000)
                && (colour >>> 24) == 0xFF;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect box = getBounds();
        if (!Themes.gradientOn() && original != null) {
            // the theme moved on: what was here before comes back
            original.setBounds(box);
            original.draw(canvas);
            shaderX = Integer.MIN_VALUE;
            return;
        }
        if (!Themes.gradientOn()) {
            paint.setShader(null);
            paint.setColor(Themes.recolour(was, true));
            shaderX = Integer.MIN_VALUE;  // so the sunset is set up again when the theme comes back
            fill(canvas, box);
            return;
        }
        View view = owner.get();
        int x = 0, y = 0;
        if (view != null) {
            view.getLocationOnScreen(at);
            x = at[0];
            y = at[1];
        }
        android.util.DisplayMetrics screen = Resources.getSystem().getDisplayMetrics();
        int w = screen.widthPixels, h = screen.heightPixels;
        boolean dark = Themes.isDark();
        if (x != shaderX || y != shaderY || w != shaderW || h != shaderH || dark != shaderDark) {
            shaderX = x;
            shaderY = y;
            shaderW = w;
            shaderH = h;
            shaderDark = dark;
            // drawn in this view's own coordinates: the screen's corner is at minus where the view is
            paint.setColor(0xFF000000);
            paint.setShader(new LinearGradient(-x, -y, w - x, h - y, dark ? DARK : LIGHT, STOPS, Shader.TileMode.CLAMP));
        }
        fill(canvas, box);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return radii == null ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }
}
