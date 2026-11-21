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
// Свои иконки: рисуются путями на сетке 24x24, без эмодзи и картинок
// =========================================================================
public final class IconView extends View {
    public static final int BACK = 0;
    public static final int CHEVRON = 1;
    public static final int SPARKLE = 2;
    public static final int CARET = 3;
    public static final int GLOW = 4;
    public static final int BACKSPACE = 5;
    public static final int PLAY = 6;
    public static final int CLOUD = 7;
    public static final int RAINBOW = 8;
    public static final int LEAF = 9;
    public static final int SEND = 10;

    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final RectF oval = new RectF();
    int icon;
    int color;

    public IconView(Context context, int icon, int color) {
        super(context);
        this.icon = icon;
        this.color = color;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColor(int value) {
        color = value;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.round(24.0f * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(size, widthSpec), resolveSize(size, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth(), getHeight()) / 24.0f;
        canvas.translate((getWidth() - 24.0f * scale) / 2.0f, (getHeight() - 24.0f * scale) / 2.0f);
        canvas.scale(scale, scale);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.9f);
        path.reset();
        switch (icon) {
            case BACK:
                path.moveTo(11.0f, 5.5f);
                path.lineTo(4.5f, 12.0f);
                path.lineTo(11.0f, 18.5f);
                path.moveTo(5.0f, 12.0f);
                path.lineTo(19.5f, 12.0f);
                canvas.drawPath(path, paint);
                break;
            case CHEVRON:
                path.moveTo(6.0f, 9.0f);
                path.lineTo(12.0f, 15.0f);
                path.lineTo(18.0f, 9.0f);
                canvas.drawPath(path, paint);
                break;
            case SPARKLE:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(10.0f, 5.0f);
                path.quadTo(10.8f, 12.2f, 18.0f, 13.0f);
                path.quadTo(10.8f, 13.8f, 10.0f, 21.0f);
                path.quadTo(9.2f, 13.8f, 2.0f, 13.0f);
                path.quadTo(9.2f, 12.2f, 10.0f, 5.0f);
                path.close();
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(18.5f, 2.3f);
                path.quadTo(18.8f, 5.2f, 21.7f, 5.5f);
                path.quadTo(18.8f, 5.8f, 18.5f, 8.7f);
                path.quadTo(18.2f, 5.8f, 15.3f, 5.5f);
                path.quadTo(18.2f, 5.2f, 18.5f, 2.3f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case CARET:
                path.moveTo(12.0f, 5.0f);
                path.lineTo(12.0f, 19.0f);
                path.moveTo(8.5f, 5.0f);
                path.lineTo(15.5f, 5.0f);
                path.moveTo(8.5f, 19.0f);
                path.lineTo(15.5f, 19.0f);
                canvas.drawPath(path, paint);
                break;
            case GLOW:
                canvas.drawCircle(12.0f, 12.0f, 4.0f, paint);
                for (int k = 0; k < 8; k++) {
                    double angle = k * Math.PI / 4.0;
                    float cos = (float) Math.cos(angle);
                    float sin = (float) Math.sin(angle);
                    canvas.drawLine(12.0f + cos * 7.2f, 12.0f + sin * 7.2f,
                        12.0f + cos * 9.6f, 12.0f + sin * 9.6f, paint);
                }
                break;
            case BACKSPACE:
                path.moveTo(8.5f, 5.5f);
                path.lineTo(20.0f, 5.5f);
                path.lineTo(20.0f, 18.5f);
                path.lineTo(8.5f, 18.5f);
                path.lineTo(3.0f, 12.0f);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(11.8f, 9.6f, 16.4f, 14.4f, paint);
                canvas.drawLine(16.4f, 9.6f, 11.8f, 14.4f, paint);
                break;
            case PLAY:
                oval.set(3.5f, 5.0f, 20.5f, 19.0f);
                canvas.drawRoundRect(oval, 3.5f, 3.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(10.2f, 9.2f);
                path.lineTo(15.3f, 12.0f);
                path.lineTo(10.2f, 14.8f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case CLOUD:
                path.moveTo(7.5f, 18.5f);
                path.lineTo(17.0f, 18.5f);
                path.cubicTo(19.8f, 18.5f, 21.0f, 16.5f, 21.0f, 14.8f);
                path.cubicTo(21.0f, 12.8f, 19.4f, 11.4f, 17.6f, 11.5f);
                path.cubicTo(17.3f, 8.6f, 15.1f, 6.5f, 12.2f, 6.5f);
                path.cubicTo(9.6f, 6.5f, 7.6f, 8.3f, 7.3f, 10.8f);
                path.cubicTo(5.0f, 11.1f, 3.5f, 12.8f, 3.5f, 14.8f);
                path.cubicTo(3.5f, 16.9f, 5.2f, 18.5f, 7.5f, 18.5f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case RAINBOW:
                for (int r = 3; r <= 9; r += 3) {
                    oval.set(12.0f - r, 17.5f - r, 12.0f + r, 17.5f + r);
                    canvas.drawArc(oval, 180.0f, 180.0f, false, paint);
                }
                break;
            case LEAF:
                path.moveTo(5.0f, 19.0f);
                path.cubicTo(5.0f, 10.0f, 10.0f, 5.0f, 19.0f, 5.0f);
                path.cubicTo(19.0f, 14.0f, 14.0f, 19.0f, 5.0f, 19.0f);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(5.0f, 19.0f, 13.0f, 11.0f, paint);
                break;
            case SEND:
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                paint.setStrokeWidth(1.2f);
                path.moveTo(3.6f, 11.2f);
                path.lineTo(20.4f, 3.8f);
                path.lineTo(14.2f, 20.4f);
                path.lineTo(11.4f, 13.4f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            default:
                break;
        }
    }
}
