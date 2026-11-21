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
// Свой ползунок: без системного SeekBar
// =========================================================================
public final class SliderView extends View {
    public interface Listener {
        void onChanged(int value, boolean fromUser);

        void onTouch(boolean down);
    }

    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final float density;
    final int onColor;
    final int offColor;
    final int min;
    final int max;
    int def;
    final int slop;
    int value;
    int detent = -1;
    float shown;
    float grow;
    float downX;
    float downY;
    boolean dragging;
    boolean longPressed;
    float grabOffset;
    ValueAnimator moveAnim;
    ValueAnimator growAnim;
    Listener listener;
    final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            // Долгое нажатие без движения возвращает значение по умолчанию
            longPressed = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            setValue(def, true, true);
        }
    };

    public SliderView(Context context, int onColor, int offColor, int min, int max, int def) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.onColor = onColor;
        this.offColor = offColor;
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.def = Math.max(min, Math.min(this.max, def));
        this.slop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void setInitial(int initial) {
        value = Math.max(min, Math.min(max, initial));
        shown = fraction(value);
        detent = detentOf(value);
        invalidate();
    }

    float fraction(int v) {
        return (v - min) / (float) (max - min);
    }

    int detentOf(int v) {
        int range = max - min;
        return range <= 24 ? v : (v - min) * 20 / range;
    }

    float left() {
        return 16.0f * density;
    }

    float right() {
        return getWidth() - 16.0f * density;
    }

    void setValue(int next, boolean fromUser, boolean animate) {
        next = Math.max(min, Math.min(max, next));
        if (next == value) return;
        value = next;
        int d = detentOf(next);
        if (d != detent) {
            detent = d;
            performHapticFeedback(next == def
                ? HapticFeedbackConstants.VIRTUAL_KEY : HapticFeedbackConstants.CLOCK_TICK);
        }
        moveShown(fraction(next), animate);
        if (listener != null) {
            listener.onChanged(next, fromUser);
        }
    }

    void moveShown(float target, boolean animate) {
        if (moveAnim != null) {
            moveAnim.cancel();
        }
        if (!animate) {
            shown = target;
            invalidate();
            return;
        }
        moveAnim = ValueAnimator.ofFloat(shown, target);
        moveAnim.setDuration(170L);
        moveAnim.setInterpolator(new DecelerateInterpolator(1.8f));
        moveAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                shown = (Float) animator.getAnimatedValue();
                invalidate();
            }
        });
        moveAnim.start();
    }

    void growTo(float target) {
        if (growAnim != null) {
            growAnim.cancel();
        }
        growAnim = ValueAnimator.ofFloat(grow, target);
        growAnim.setDuration(target > grow ? 220L : 160L);
        growAnim.setInterpolator(target > grow
            ? new android.view.animation.OvershootInterpolator(2.2f) : new DecelerateInterpolator());
        growAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                grow = (Float) animator.getAnimatedValue();
                invalidate();
            }
        });
        growAnim.start();
    }

    // Не даём системному жесту «назад» с края экрана перехватывать ползунок
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            setSystemGestureExclusionRects(java.util.Collections.singletonList(new Rect(0, 0, r - l, b - t)));
        }
    }

    void setFromX(float x, boolean animate) {
        float span = Math.max(1.0f, right() - left());
        float f = Math.max(0.0f, Math.min(1.0f, (x - left()) / span));
        setValue(min + Math.round(f * (max - min)), true, animate);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(40.0f * density));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                longPressed = false;
                grabOffset = 0.0f;
                // Попали по ползунку: тянем сразу, без «мёртвой зоны» и без перехвата скроллом
                float thumbX = left() + (right() - left()) * shown;
                if (Math.abs(downX - thumbX) <= 28.0f * density) {
                    dragging = true;
                    grabOffset = downX - thumbX;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                growTo(1.0f);
                if (listener != null) listener.onTouch(true);
                postDelayed(longPress, 600L);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && !longPressed) {
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dx > slop && dx > dy) {
                        dragging = true;
                        removeCallbacks(longPress);
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (dy > slop) {
                        removeCallbacks(longPress);
                    }
                }
                if (dragging) {
                    if (Math.abs(event.getX() - downX) > slop) {
                        removeCallbacks(longPress);
                    }
                    setFromX(event.getX() - grabOffset, false);
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (!dragging && !longPressed
                    && Math.abs(event.getX() - downX) <= slop && Math.abs(event.getY() - downY) <= slop) {
                    setFromX(event.getX(), true);
                }
                finish();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                finish();
                return true;
            default:
                return true;
        }
    }

    void finish() {
        dragging = false;
        growTo(0.0f);
        if (listener != null) listener.onTouch(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(longPress);
        if (moveAnim != null) moveAnim.cancel();
        if (growAnim != null) growAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float cy = h / 2.0f;
        float left = left();
        float right = right();
        float trackH = 6.0f * density;
        float x = left + (right - left) * shown;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(offColor);
        rect.set(left, cy - trackH / 2.0f, right, cy + trackH / 2.0f);
        canvas.drawRoundRect(rect, trackH / 2.0f, trackH / 2.0f, paint);

        if (x > left) {
            paint.setColor(onColor);
            rect.set(left, cy - trackH / 2.0f, x, cy + trackH / 2.0f);
            canvas.drawRoundRect(rect, trackH / 2.0f, trackH / 2.0f, paint);
        }

        // Засечка значения по умолчанию: гаснет, когда ползунок рядом
        float defX = left + (right - left) * fraction(def);
        float near = Math.min(1.0f, Math.abs(defX - x) / (16.0f * density));
        if (near > 0.05f) {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(Math.round(110.0f * near));
            rect.set(defX - density, cy - 7.0f * density, defX + density, cy + 7.0f * density);
            canvas.drawRoundRect(rect, density, density, paint);
        }

        float r = 9.0f * density * (1.0f + 0.20f * grow);
        if (grow > 0.01f) {
            paint.setColor(onColor);
            paint.setAlpha(Math.round(70.0f * Math.min(1.0f, grow)));
            canvas.drawCircle(x, cy, r + 8.0f * density * grow, paint);
        }
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(x, cy, r, paint);
        paint.setColor(onColor);
        canvas.drawCircle(x, cy, r * 0.38f, paint);
    }
}
