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
// Input tracker for each EditText
// =========================================================================
public final class InputTracker implements TextWatcher {
    final Main main;
    final WeakReference<EditText> ref;
    final Clusters clusters = new Clusters();
    final Ghosts ghosts = new Ghosts();
    final Caret caret = new Caret();
    final Particles particles = new Particles();
    final ParticleLayer particleLayer = new ParticleLayer(particles);
    final ArrayList<Ghost> pending = new ArrayList<Ghost>();
    final CaretOverlay caretOverlay;

    int insertedStart;
    int insertedCount;
    boolean isApplyingSpans;
    boolean muted;
    // Был ли у поля видимый системный курсор до нас: вернуть нужно именно его, а не всегда «виден»
    final boolean originalCursorVisible;

    // Снимок изменяемого диапазона: клавиатура заменяет слово целиком, а менялась одна буква
    String oldRegion;
    int oldStart;
    int diffPrefix;
    int diffSuffix;
    int diffBefore;
    int diffCount;
    boolean diffValid;
    static final int SNAPSHOT_LIMIT = 20000;
    String prevText;
    // SPAN_INTERMEDIATE: не уведомлять TextView, чтобы не пересобирать layout на каждую букву
    static final int QUIET = 0x80000;

    // Собственный цикл кадров: пока есть живые буквы, поле перерисовывается каждый кадр
    // само, не завися от того, дойдёт ли запрос перерисовки из draw() спана
    final ArrayList<AnimatedSpan> active = new ArrayList<AnimatedSpan>();
    boolean ticking;

    // Пустой «толкатель»: EditText кэширует отрисовку текста (display list) и перерисовывает
    // её только при изменении текста/спанов. invalidate() кэш не сбрасывает, а изменение
    // CharacterStyle-спана без UpdateLayout сбрасывает кэш нужного диапазона без пересборки layout.
    final android.text.style.CharacterStyle poke = new PokeSpan();

    void pokeText(EditText view) {
        Editable text = view.getText();
        if (text == null) return;
        int lo = Integer.MAX_VALUE;
        int hi = -1;
        for (int i = 0; i < active.size(); i++) {
            AnimatedSpan span = active.get(i);
            int a = text.getSpanStart(span);
            int b = text.getSpanEnd(span);
            if (a >= 0 && b > a) {
                if (a < lo) lo = a;
                if (b > hi) hi = b;
            }
        }
        if (hi > lo) {
            text.setSpan(poke, lo, hi, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (text.getSpanStart(poke) >= 0) {
            text.removeSpan(poke);
        }
    }

    void unpoke(EditText view) {
        Editable text = view.getText();
        if (text != null && text.getSpanStart(poke) >= 0) {
            text.removeSpan(poke);
        }
        view.invalidate();
    }

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            EditText v = ref.get();
            if (v == null || muted || !v.isAttachedToWindow()) {
                active.clear();
                if (v != null) unpoke(v);
                return;
            }
            long now = AnimationUtils.currentAnimationTimeMillis();
            for (int i = active.size() - 1; i >= 0; i--) {
                AnimatedSpan span = active.get(i);
                if (span.finished() || now - span.startTime > span.lifeMs()) {
                    active.remove(i);
                }
            }
            if (active.isEmpty()) {
                unpoke(v);
                return;
            }
            pokeText(v);
            v.invalidate();
            ticking = true;
            v.postOnAnimation(this);
        }
    };

    void startTicker(EditText view) {
        if (active.isEmpty()) {
            return;
        }
        view.invalidate();
        if (!ticking) {
            ticking = true;
            view.postOnAnimation(tick);
        }
    }
    final ArrayList<AnimatedSpan> savedSpans = new ArrayList<AnimatedSpan>();
    final ArrayList<int[]> savedBounds = new ArrayList<int[]>();

    public InputTracker(Main main, EditText view) {
        this.main = main;
        this.ref = new WeakReference<EditText>(view);
        this.caretOverlay = new CaretOverlay(this, view);
        this.originalCursorVisible = view.isCursorVisible();
        Editable initial = view.getText();
        prevText = initial != null && initial.length() <= SNAPSHOT_LIMIT ? initial.toString() : null;
        view.addTextChangedListener(this);
        view.getOverlay().add(caretOverlay);
        refresh();
    }

    public void setMuted(boolean value) {
        muted = value;
        pending.clear();
        insertedCount = 0;
    }

    public void refresh() {
        EditText view = ref.get();
        if (view == null) return;
        Config config = main.configFor(ref.get());
        if (config.caret) {
            view.setCursorVisible(false);
        } else {
            view.setCursorVisible(originalCursorVisible);
            caret.reset();
        }
        if (!config.ghosts) {
            ghosts.clear();
        }
        if (!config.particles) {
            particles.clear();
            particleLayer.detach();
        }
        caretOverlay.setBounds(0, 0, view.getWidth(), view.getHeight());
        view.invalidate();
    }

    public void dispose() {
        EditText view = ref.get();
        if (view != null) {
            view.removeTextChangedListener(this);
            view.getOverlay().remove(caretOverlay);
            view.setCursorVisible(originalCursorVisible);
            view.invalidate();
        }
        if (view != null) {
            view.removeCallbacks(tick);
        }
        ticking = false;
        active.clear();
        if (view != null) unpoke(view);
        particles.clear();
        particleLayer.detach();
        ghosts.clear();
        pending.clear();
        caret.reset();
    }

    public void drawOverlay(EditText view, Canvas canvas) {
        Config config = main.configFor(ref.get());
        long now = AnimationUtils.currentAnimationTimeMillis();
        boolean active = ghosts.draw(view, canvas, config, now);

        if (config.caret) {
            int color = config.getCaretColor(main.accentColor(), view.getCurrentTextColor());
            boolean focused = view.isFocused();
            long delay;
            int selStart = view.getSelectionStart();
            int selEnd = view.getSelectionEnd();
            if (config.selEffect && focused && selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
                delay = caret.drawSelection(view, canvas, config, color, true, now);
            } else {
                delay = caret.draw(view, canvas, config, color, focused, now);
            }
            if (delay == Caret.NEXT_FRAME || active) {
                view.postInvalidateOnAnimation();
            } else if (delay > 0L) {
                view.postInvalidateDelayed(delay);
            }
        } else if (active) {
            view.postInvalidateOnAnimation();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        pending.clear();
        savedSpans.clear();
        savedBounds.clear();
        oldRegion = null;
        diffValid = false;
        insertedStart = 0;
        insertedCount = 0;
        EditText view = ref.get();
        if (muted || view == null) {
            return;
        }
        if (count <= 4000) {
            oldRegion = text.subSequence(start, start + count).toString();
            oldStart = start;
            if (count > 0 && text instanceof Spanned) {
                AnimatedSpan[] spans = ((Spanned) text).getSpans(start, start + count, AnimatedSpan.class);
                for (int i = 0; i < spans.length; i++) {
                    if (spans[i].finished()) continue;
                    savedSpans.add(spans[i]);
                    savedBounds.add(new int[]{((Spanned) text).getSpanStart(spans[i]), ((Spanned) text).getSpanEnd(spans[i])});
                }
            }
        }
        if (count <= 0 || after >= count) {
            return;
        }
        Config config = main.configFor(ref.get());
        if ((config.ghosts || config.particles || main.sendSparksEnabled()) && view.isShown()) {
            capture(view, text, start, start + count);
        }
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
        EditText view = ref.get();
        if (view == null || muted) return;
        long now = AnimationUtils.currentAnimationTimeMillis();
        Config config = main.configFor(ref.get());

        // Реально изменённый кусок: срезаем общий префикс и суффикс старого и нового текста.
        // Иначе при удалении одной буквы клавиатура «заменяет всё слово» и частицы летят со всех букв.
        int delFrom = start;
        int delTo = start + before;
        int insFrom = start + before;
        int insTo = start + count;
        diffValid = false;
        if (oldRegion != null && oldStart == start && oldRegion.length() == before
            && start + count <= text.length()) {
            String fresh = text.subSequence(start, start + count).toString();
            int limit = Math.min(before, count);
            int p = 0;
            while (p < limit && oldRegion.charAt(p) == fresh.charAt(p)) p++;
            if (p < before && p > 0 && Character.isLowSurrogate(oldRegion.charAt(p))) p--;
            int sfx = 0;
            while (sfx < limit - p
                && oldRegion.charAt(before - 1 - sfx) == fresh.charAt(count - 1 - sfx)) sfx++;
            if (sfx > 0 && sfx < before && Character.isLowSurrogate(oldRegion.charAt(before - sfx))) sfx--;
            diffPrefix = p;
            diffSuffix = sfx;
            diffBefore = before;
            diffCount = count;
            diffValid = true;
            delFrom = start + p;
            delTo = start + before - sfx;
            insFrom = start + p;
            insTo = start + count - sfx;
        } else if (count > before) {
            delTo = delFrom;
        } else if (before > count) {
            insTo = insFrom;
        } else {
            delTo = delFrom;
        }

        boolean removed = delTo > delFrom;
        boolean added = insTo > insFrom;
        if (removed) {
            vanish(view, delFrom, delTo, now);
        }
        if (added) {
            insertedStart = insFrom;
            insertedCount = insTo - insFrom;
            caret.typed(text, insertedStart, insertedStart + insertedCount, now);
        } else if (removed) {
            caret.erased(now);
        }
        if (removed && !added && config.haptic) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        caret.wake(now);
        caretOverlay.setBounds(0, 0, view.getWidth(), view.getHeight());
        view.invalidate();
    }

    @Override
    public void afterTextChanged(Editable s) {
        savedSpans.clear();
        savedBounds.clear();
        if (isApplyingSpans) {
            return;
        }
        String cur = s.length() <= SNAPSHOT_LIMIT ? s.toString() : null;
        if (muted) {
            prevText = cur;
            active.clear();
            return;
        }
        EditText view = ref.get();
        if (view == null) return;
        Config config = main.configFor(ref.get());
        long now = AnimationUtils.currentAnimationTimeMillis();

        // Что изменилось на самом деле, считаем по всему тексту, а не по тому, что сообщила клавиатура:
        // клавиатуры пересоздают первую букву слова (она подчёркнута как composing), и спан терялся
        if (cur == null || prevText == null) {
            prevText = cur;
            active.clear();
            return;
        }
        isApplyingSpans = true;
        try {
            int pl = prevText.length();
            int cl = cur.length();
            int limit = Math.min(pl, cl);
            int p = 0;
            while (p < limit && prevText.charAt(p) == cur.charAt(p)) p++;
            if (p > 0 && p < cl && Character.isLowSurrogate(cur.charAt(p))) p--;
            int sf = 0;
            while (sf < limit - p && prevText.charAt(pl - 1 - sf) == cur.charAt(cl - 1 - sf)) sf++;
            if (sf > 0 && sf < cl && Character.isLowSurrogate(cur.charAt(cl - sf))) sf--;
            int delta = cl - pl;

            // Живые буквы: сдвигаем вместе с текстом и возвращаем спан, если клавиатура его сняла
            for (int i = active.size() - 1; i >= 0; i--) {
                AnimatedSpan span = active.get(i);
                if (span.finished()) {
                    active.remove(i);
                    continue;
                }
                int at = span.at;
                int len = span.text.length();
                int na;
                if (at + len <= p) {
                    na = at;
                } else if (at >= pl - sf) {
                    na = at + delta;
                } else {
                    span.cancel();
                    if (s.getSpanStart(span) >= 0) s.removeSpan(span);
                    active.remove(i);
                    continue;
                }
                span.at = na;
                if (na >= 0 && na + len <= cl
                    && (s.getSpanStart(span) != na || s.getSpanEnd(span) != na + len)) {
                    s.setSpan(span, na, na + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | QUIET);
                }
            }

            // Новые буквы: ровно тот кусок, который появился в тексте
            int from = p;
            int to = cl - sf;
            insertedStart = from;
            insertedCount = Math.max(0, to - from);
            if (to > from && to - from <= 30) {
                clusters.reset(cur);
                AnimatedSpan[] oldSpans = s.getSpans(from, to, AnimatedSpan.class);
                for (int i = 0; i < oldSpans.length; i++) {
                    oldSpans[i].cancel();
                    s.removeSpan(oldSpans[i]);
                    active.remove(oldSpans[i]);
                }
                int order = 0;
                for (int i = from; i < to; ) {
                    int next = clusters.next(i, to);
                    if (!Clusters.blank(cur, i, next) && !Clusters.emoji(cur, i, next)) {
                        // Вставленный кусок выходит по очереди: каждая следующая буква чуть позже
                        long delay = Math.min(1500L, (long) order * config.stagger);
                        order++;
                        AnimatedSpan span = new AnimatedSpan(main, view, cur.substring(i, next), config, now, i, delay);
                        span.at = i;
                        s.setSpan(span, i, next, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | QUIET);
                        active.add(span);
                    }
                    i = next;
                }
            }
            prevText = cur;
        } finally {
            isApplyingSpans = false;
        }
        startTicker(view);
    }

    void vanish(EditText view, int from, int to, long now) {
        // Поле очистила кнопка «Отправить», а не стирание: искры летят из её центра, буквы не крошатся
        float[] sendFrom = view.length() == 0 ? main.takeSendPoint() : null;
        if (sendFrom == null && view.length() == 0 && main.sendGuessEnabled()
                && to - from >= 2 && !pending.isEmpty()) {
            // Кнопку не нашли, но поле разом очистилось: считаем это отправкой, цель — правый край поля
            int[] at = new int[2];
            view.getLocationInWindow(at);
            float density = view.getResources().getDisplayMetrics().density;
            sendFrom = new float[]{at[0] + view.getWidth() - 24.0f * density, at[1] + view.getHeight() * 0.5f};
        }
        if (sendFrom != null) {
            if (main.sendSparksEnabled()) {
                Config sendConfig = main.configFor(ref.get());
                ArrayList<Ghost> letters = new ArrayList<Ghost>(pending);
                particles.setSendOptions(main.getSendShape(), main.getSendSwirl(), main.getSendDir());
                particles.setSendMotion(main.getSendMotion(), main.getSendDuration(), main.getSendArc(), main.getSendWave());
                if (particles.burstSend(view, sendConfig, letters, sendFrom[0], sendFrom[1],
                        main.getSendPreset(), main.getSendPalette(), main.getSendAmount(), main.getSendSpeed())) {
                    particleLayer.attach(view);
                    // сами буквы плавно гаснут, пока из них вылетают частицы
                    if (sendConfig.ghosts && !letters.isEmpty()
                            && main.getSendPreset() != Particles.SEND_CLASSIC
                            && main.getSendPreset() != Particles.SEND_FIREWORK
                            && main.getSendPreset() != Particles.SEND_CONFETTI) {
                        for (int i = 0; i < letters.size(); i++) {
                            Ghost ghost = letters.get(i);
                            if (!ghost.emoji) {
                                ghost.fadeMs = main.getSendMorph();
                                ghosts.add(ghost, now);
                            }
                        }
                    }
                }
            }
            pending.clear();
            return;
        }
        for (int i = pending.size() - 1; i >= 0; i--) {
            int offset = pending.get(i).offset;
            if (offset < from || offset >= to) {
                pending.remove(i);
            }
        }
        if (pending.isEmpty()) return;
        Config config = main.configFor(ref.get());
        boolean falling = config.particles && (config.style == Config.STYLE_FALL
            || config.style == Config.STYLE_SAND || config.style == Config.STYLE_SMOKE);
        if (config.ghosts && !falling) {
            for (int i = 0; i < pending.size(); i++) {
                Ghost ghost = pending.get(i);
                if (!ghost.emoji) {
                    ghosts.add(ghost, now);
                }
            }
        }
        if (config.particles) {
            if (particles.burst(view, config, pending)) {
                particleLayer.attach(view);
            }
        }
    }

    void capture(EditText view, CharSequence content, int start, int end) {
        Layout layout = view.getLayout();
        if (layout == null) return;
        String text = content.toString();
        clusters.reset(text);
        TextPaint paint = view.getPaint();
        float left = view.getCompoundPaddingLeft();
        int top = view.getTotalPaddingTop();
        int scroll = view.getScrollY();
        int first = layout.getLineForVertical(scroll - top);
        int last = layout.getLineForVertical(scroll + view.getHeight() - top);
        int from = Math.max(clusters.floor(start), layout.getLineStart(first));
        int to = Math.min(end, layout.getLineEnd(last));

        for (int i = from; i < to && pending.size() < Ghosts.LIMIT; ) {
            int next = clusters.next(i, to);
            if (!Clusters.blank(text, i, next)) {
                float width = paint.measureText(text, i, next);
                float x = left + layout.getPrimaryHorizontal(i);
                if (layout.isRtlCharAt(i)) {
                    x -= width;
                }
                float baseline = top + layout.getLineBaseline(layout.getLineForOffset(i));
                pending.add(new Ghost(i, text.substring(i, next), x, baseline, width, Clusters.emoji(content, i, next)));
            }
            i = next;
        }
    }
}
