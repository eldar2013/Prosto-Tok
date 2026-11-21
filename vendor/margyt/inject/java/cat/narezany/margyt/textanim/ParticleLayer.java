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

public final class ParticleLayer extends Drawable {
    final Particles particles;
    final int[] location = new int[2];
    final Runnable sweep = new Runnable() {
        @Override
        public void run() {
            sweep();
        }
    };
    WeakReference<ViewGroup> host;
    long last;

    public ParticleLayer(Particles particles) {
        this.particles = particles;
    }

    public void attach(View view) {
        View root = view.getRootView();
        if (root == host()) {
            invalidateSelf();
            return;
        }
        detach();
        if (!(root instanceof ViewGroup) || view.getWindowToken() == null) {
            particles.clear();
            return;
        }
        ViewGroup group = (ViewGroup) root;
        host = new WeakReference<ViewGroup>(group);
        last = 0L;
        setBounds(0, 0, group.getWidth(), group.getHeight());
        group.getOverlay().add(this);
    }

    public void detach() {
        ViewGroup current = host();
        host = null;
        if (current != null) {
            current.removeCallbacks(sweep);
            current.getOverlay().remove(this);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        ViewGroup current = host();
        if (current == null) return;
        int save = canvas.save();
        try {
            long now = AnimationUtils.currentAnimationTimeMillis();
            if (last != 0L) {
                particles.step(now - last);
            }
            last = now;
            Rect bounds = getBounds();
            if (bounds.right != current.getWidth() || bounds.bottom != current.getHeight()) {
                setBounds(0, 0, current.getWidth(), current.getHeight());
            }
            current.getLocationInWindow(location);
            canvas.translate(-location[0], -location[1]);
            particles.draw(canvas);
        } catch (Throwable error) {
            particles.clear();
        } finally {
            canvas.restoreToCount(save);
        }
        if (particles.isEmpty()) {
            current.post(sweep);
        } else {
            invalidateSelf();
        }
    }

    ViewGroup host() {
        return host == null ? null : host.get();
    }

    void sweep() {
        if (particles.isEmpty()) {
            detach();
        }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter filter) {}

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
