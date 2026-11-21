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
// Свой тумблер: плавная заливка дорожки, бегунок тянется при нажатии
// =========================================================================
public final class SwitchView extends View {
    public interface Listener {
        void onChanged(boolean checked);
    }

    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final Path tick = new Path();
    final int onColor;
    final int offColor;
    final float density;
    boolean checked;
    float progress;
    float press;
    ValueAnimator slide;
    ValueAnimator squeeze;
    Listener listener;

    public SwitchView(Context context, int onColor, int offColor) {
        super(context);
        this.onColor = onColor;
        this.offColor = offColor;
        this.density = context.getResources().getDisplayMetrics().density;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value, boolean animate) {
        checked = value;
        float target = value ? 1.0f : 0.0f;
        if (slide != null) {
            slide.cancel();
        }
        if (!animate) {
            progress = target;
            invalidate();
            return;
        }
        slide = ValueAnimator.ofFloat(progress, target);
        slide.setDuration(200L);
        slide.setInterpolator(new DecelerateInterpolator(1.6f));
        slide.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                progress = (Float) animator.getAnimatedValue();
                invalidate();
            }
        });
        slide.start();
    }

    public void toggle() {
        setChecked(!checked, true);
        if (listener != null) {
            listener.onChanged(checked);
        }
    }

    void pressTo(float target) {
        if (squeeze != null) {
            squeeze.cancel();
        }
        squeeze = ValueAnimator.ofFloat(press, target);
        squeeze.setDuration(120L);
        squeeze.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                press = (Float) animator.getAnimatedValue();
                invalidate();
            }
        });
        squeeze.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(52.0f * density), Math.round(32.0f * density));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressTo(1.0f);
                return true;
            case MotionEvent.ACTION_UP:
                pressTo(0.0f);
                if (event.getX() >= 0 && event.getX() <= getWidth()
                    && event.getY() >= 0 && event.getY() <= getHeight()) {
                    toggle();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressTo(0.0f);
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float t = Motion.clamp(progress);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Motion.mixColors(offColor, onColor, t));
        rect.set(0.0f, 0.0f, w, h);
        canvas.drawRoundRect(rect, h / 2.0f, h / 2.0f, paint);

        float pad = h * 0.14f;
        float r = h / 2.0f - pad;
        float stretch = r * 0.45f * press;
        float cx = (pad + r) + ((w - pad - r) - (pad + r)) * t;
        float left = cx - r - stretch * t;
        float right = cx + r + stretch * (1.0f - t);
        paint.setColor(0xFFFFFFFF);
        rect.set(left, h / 2.0f - r, right, h / 2.0f + r);
        canvas.drawRoundRect(rect, r, r, paint);

        if (t > 0.05f) {
            float mx = (left + right) / 2.0f;
            float my = h / 2.0f;
            float u = r * 0.5f;
            tick.reset();
            tick.moveTo(mx - u * 0.9f, my + u * 0.05f);
            tick.lineTo(mx - u * 0.25f, my + u * 0.65f);
            tick.lineTo(mx + u * 0.95f, my - u * 0.6f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.24f);
            paint.setColor(onColor);
            paint.setAlpha(Math.round(255.0f * t));
            canvas.drawPath(tick, paint);
        }
    }
}
