package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Colours for the Material theme besides the wallpaper's, the same circles as in
 * Margy Gram: the first is the phone's wallpaper, the rest are seeds. A seed gives
 * the theme its neutrals (the ground and the words) and the accent its colour.
 *
 * The palettes are drawn in CIELAB: a hue and a chroma, every tone pulled into
 * sRGB by lowering the chroma until it fits.
 */
final class MaterialSeeds {

    private MaterialSeeds() {}

    /** 0 is the wallpaper; the rest are seeds, mint first. */
    static final int[] SEEDS = {0, 0xFF8DD1B0, 0xFF6750A4, 0xFF4285F4, 0xFF00897B, 0xFF43A047, 0xFFFBC02D,
            0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41, 0xFF546E7A};

    private static final String KEY = "md3_seed";

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    static int seed() {
        SharedPreferences prefs = prefs();
        return prefs == null ? 0 : prefs.getInt(KEY, 0);
    }

    /** Pick a colour: the theme's neutrals and the accent follow it. */
    static void choose(int seed) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY, seed).apply();
        Themes.forget();
        if (seed == 0) Accent.setMaterial(true);
        else Accent.set(tone(seed, 48, 0, 60));
        Themes.repaintVisible();
    }

    /** A neutral of the seed at a tone, 0 black to 100 white. */
    static int neutral(int seed, double tone) {
        return tone(seed, 4, 0, tone);
    }

    /** Three colours for a circle: primary, secondary, tertiary. */
    static int[] swatch(int seed, boolean dark) {
        if (seed == 0) {
            Context context = Margy.context();
            if (context != null && android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    return new int[]{
                            context.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600),
                            context.getColor(dark ? android.R.color.system_accent2_300 : android.R.color.system_accent2_200),
                            context.getColor(dark ? android.R.color.system_accent3_300 : android.R.color.system_accent3_400)};
                } catch (Throwable ignored) {
                }
            }
            seed = SEEDS[1];
        }
        return new int[]{tone(seed, 40, 0, dark ? 80 : 40), tone(seed, 16, 0, dark ? 70 : 80),
                tone(seed, 26, 60, dark ? 70 : 60)};
    }

    /** The seed's hue, turned by some degrees, at a chroma and a tone. */
    static int tone(int seed, double chroma, double turnDegrees, double tone) {
        double[] lab = lab(seed);
        double hue = Math.atan2(lab[2], lab[1]) + Math.toRadians(turnDegrees);
        double cos = Math.cos(hue), sin = Math.sin(hue);
        double c = chroma;
        int[] out = new int[1];
        while (c > 0 && !rgb(tone, c * cos, c * sin, out)) c -= 1;
        if (c <= 0) rgb(tone, 0, 0, out);
        return out[0];
    }

    // ------------------------------------------------------------- CIELAB

    private static double[] lab(int colour) {
        double r = linear(((colour >> 16) & 0xFF) / 255.0);
        double g = linear(((colour >> 8) & 0xFF) / 255.0);
        double b = linear((colour & 0xFF) / 255.0);
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    /** Lab to an opaque colour; false when it falls outside sRGB. */
    private static boolean rgb(double l, double a, double b, int[] out) {
        double fy = (l + 16) / 116, fx = fy + a / 500, fz = fy - b / 200;
        double x = 0.95047 * finv(fx), y = finv(fy), z = 1.08883 * finv(fz);
        double r = x * 3.2406 + y * -1.5372 + z * -0.4986;
        double g = x * -0.9689 + y * 1.8758 + z * 0.0415;
        double bl = x * 0.0557 + y * -0.2040 + z * 1.0570;
        final double e = 0.002;
        boolean fits = r >= -e && r <= 1 + e && g >= -e && g <= 1 + e && bl >= -e && bl <= 1 + e;
        out[0] = 0xFF000000 | (channel(r) << 16) | (channel(g) << 8) | channel(bl);
        return fits;
    }

    private static int channel(double linear) {
        linear = Math.max(0, Math.min(1, linear));
        double v = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(v * 255);
    }

    private static double linear(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    private static double finv(double t) {
        double t3 = t * t * t;
        return t3 > 216.0 / 24389 ? t3 : (116 * t - 16) / (24389.0 / 27);
    }
}
