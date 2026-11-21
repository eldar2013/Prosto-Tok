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
import android.os.PowerManager;
import android.provider.Settings;
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

/**
 * «Анимация текста» 12.8 (@mihailkotovski, @mishabotov), перенесена в MargyT из плагина ttcuz.
 * Вместо API ttcuz здесь TtcuzPlugin из этого же пакета; включает и выключает MargyT (TextAnim).
 */
public final class Main extends TtcuzPlugin {
    /** Выключено в настройках MargyT: новые поля не подхватываются. */
    public static volatile boolean enabled = true;
    static Main instance;
    final WeakHashMap<EditText, InputTracker> inputs = new WeakHashMap<EditText, InputTracker>();
    Config config = Config.DEFAULT;
    // Упрощённые варианты: энергосбережение и выключенные системные анимации
    Config saverConfig = Config.DEFAULT;
    Config calmConfig = Config.DEFAULT;
    boolean ecoOn = true;
    int ecoLevel;
    long ecoChecked;
    SettingsScreen settingsScreen;
    // Искры отправки: кнопки «Отправить», момент нажатия и центр кнопки (координаты окна)
    boolean sendSparks = true;
    static final long SEND_WINDOW_MS = 700L;
    final WeakHashMap<View, Boolean> sendViews = new WeakHashMap<View, Boolean>();
    float[] sendPoint;
    long sendUntil;
    // Пресет, палитра, количество и скорость эффекта отправки
    int sendPreset = 1;
    int sendPalette = 0;
    int sendAmount = 4;
    int sendSpeed = 100;
    int sendShape = 0;
    int sendSwirl = 100;
    int sendDir = 0;
    // Тип полёта, его длительность, изгиб, волна вылета и плавность превращения буквы
    int sendMotion = 0;
    int sendDuration = 600;
    int sendArc = 0;
    int sendWave = 100;
    int sendMorph = 0;
    boolean sendGuess = true;

    public static Main get() {
        return instance;
    }

    public Config getConfig() {
        return config;
    }

    @Override
    public void onStart() {
        instance = this;
        reloadConfig();
    }

    @Override
    public void onStop() {
        ArrayList<InputTracker> list = new ArrayList<InputTracker>(inputs.values());
        for (int i = 0; i < list.size(); i++) {
            InputTracker tracker = list.get(i);
            if (tracker != null) {
                tracker.dispose();
            }
        }
        inputs.clear();
        ArrayList<Activity> watched = new ArrayList<Activity>(focusWatchers.keySet());
        for (int i = 0; i < watched.size(); i++) {
            unwatchFocus(watched.get(i));
        }
        if (settingsScreen != null) {
            settingsScreen.close(false);
            settingsScreen = null;
        }
        instance = null;
    }

    // Конфиг для конкретного поля: при экономии заряда или выключенных анимациях системы — упрощённый
    public Config configFor(View view) {
        if (!ecoOn || view == null) {
            return config;
        }
        long now = SystemClock.uptimeMillis();
        if (ecoChecked == 0L || now - ecoChecked > 3000L) {
            ecoChecked = now;
            ecoLevel = 0;
            try {
                Context context = view.getContext();
                float scale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
                if (scale == 0.0f) {
                    ecoLevel = 2;
                } else {
                    PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    if (power != null && power.isPowerSaveMode()) {
                        ecoLevel = 1;
                    }
                }
            } catch (Throwable ignored) {
                ecoLevel = 0;
            }
        }
        return ecoLevel == 2 ? calmConfig : (ecoLevel == 1 ? saverConfig : config);
    }

    public void reloadConfig() {
        config = Config.fromPrefs(ttcuz().prefs());
        saverConfig = Config.fromPrefs(ttcuz().prefs(), 1);
        calmConfig = Config.fromPrefs(ttcuz().prefs(), 2);
        ecoOn = ttcuz().prefs().getBoolean("eco", true);
        ecoChecked = 0L;
        sendSparks = ttcuz().prefs().getBoolean("send_particles", true);
        sendPreset = Math.max(0, Math.min(Particles.SEND_PRESETS - 1, ttcuz().prefs().getInt("send_preset", 1)));
        sendPalette = Math.max(0, Math.min(Particles.SEND_PALETTE_COUNT - 1, ttcuz().prefs().getInt("send_palette", 0)));
        sendAmount = Math.max(1, Math.min(8, ttcuz().prefs().getInt("send_amount", 4)));
        sendSpeed = Math.max(40, Math.min(300, ttcuz().prefs().getInt("send_speed", 100)));
        sendShape = Math.max(0, Math.min(Particles.SEND_SHAPE_COUNT - 1, ttcuz().prefs().getInt("send_shape", 0)));
        sendSwirl = Math.max(0, Math.min(250, ttcuz().prefs().getInt("send_swirl", 100)));
        sendDir = Math.max(0, Math.min(2, ttcuz().prefs().getInt("send_dir", 0)));
        sendGuess = ttcuz().prefs().getBoolean("send_guess", true);
        sendMotion = Math.max(0, Math.min(Particles.MOTION_VORTEX, ttcuz().prefs().getInt("send_motion", 0)));
        sendDuration = Math.max(150, Math.min(1500, ttcuz().prefs().getInt("send_duration", 600)));
        sendArc = Math.max(-100, Math.min(100, ttcuz().prefs().getInt("send_arc", 0)));
        sendWave = Math.max(0, Math.min(200, ttcuz().prefs().getInt("send_wave", 100)));
        sendMorph = Math.max(0, Math.min(1500, ttcuz().prefs().getInt("send_morph", 0)));
        try {
            Strings.setMode(ttcuz().prefs().getInt("lang", 0));
        } catch (Throwable ignored) {
            // язык остаётся автоматическим
        }
        ArrayList<InputTracker> list = new ArrayList<InputTracker>(inputs.values());
        for (int i = 0; i < list.size(); i++) {
            InputTracker tracker = list.get(i);
            if (tracker != null) {
                tracker.refresh();
            }
        }
    }

    // Своё окно настроек с живым превью эффекта вместо стандартной страницы
    @Override
    public boolean onOpenSettings(Activity activity) {
        if (activity == null) {
            return false;
        }
        try {
            if (settingsScreen != null) {
                settingsScreen.close(false);
            }
            settingsScreen = new SettingsScreen(this, activity);
            settingsScreen.show();
            return true;
        } catch (Throwable error) {
            ttcuz().log("settings screen: " + error);
            settingsScreen = null;
            return false;
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (activity != null) {
            unwatchFocus(activity);
        }
        if (settingsScreen != null && settingsScreen.host == activity) {
            settingsScreen.close(false);
            settingsScreen = null;
        }
    }

    void detachInput(EditText edit) {
        InputTracker tracker = inputs.remove(edit);
        if (tracker != null) {
            tracker.dispose();
        }
    }

    void muteInput(EditText edit, boolean muted) {
        InputTracker tracker = inputs.get(edit);
        if (tracker != null) {
            tracker.setMuted(muted);
        }
    }

    @Override
    public void onPreferenceChanged(String scope, String key) {
        reloadConfig();
    }

    final WeakHashMap<Activity, ViewTreeObserver.OnGlobalFocusChangeListener> focusWatchers =
        new WeakHashMap<Activity, ViewTreeObserver.OnGlobalFocusChangeListener>();
    long accentTime;
    int accentValue;

    // Цвет акцента читаем не чаще раза в секунду: он нужен на каждом кадре
    public int accentColor() {
        long now = SystemClock.uptimeMillis();
        if (accentTime == 0L || now - accentTime > 1000L) {
            accentValue = ttcuz().accent();
            accentTime = now;
        }
        return accentValue;
    }

    // @uses interface: анимирует ввод текста и курсор во всех полях ввода на экране
    @Override
    public void onScreen(Activity activity, ViewGroup content) {
        if (activity != null) {
            watchFocus(activity);
        }
        if (content != null) {
            attachAll(content);
            watchSendAll(content);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity != null) {
            watchFocus(activity);
        }
    }

    // @uses interface: подхватывает поле ввода, которое мод перекрашивает
    @Override
    public void onView(View view) {
        if (view instanceof EditText) {
            attachOne((EditText) view);
        }
        watchSend(view);
    }

    // Вместо обхода всего экрана на каждой раскладке ждём, когда фокус перейдёт на поле ввода
    void watchFocus(Activity activity) {
        if (focusWatchers.containsKey(activity)) {
            return;
        }
        ViewTreeObserver observer = activity.getWindow().getDecorView().getViewTreeObserver();
        if (observer == null || !observer.isAlive()) {
            return;
        }
        ViewTreeObserver.OnGlobalFocusChangeListener watcher = new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (newFocus instanceof EditText) {
                    attachOne((EditText) newFocus);
                    watchSendNear(newFocus);
                }
            }
        };
        observer.addOnGlobalFocusChangeListener(watcher);
        focusWatchers.put(activity, watcher);
    }

    void unwatchFocus(Activity activity) {
        ViewTreeObserver.OnGlobalFocusChangeListener watcher = focusWatchers.remove(activity);
        if (watcher == null) {
            return;
        }
        ViewTreeObserver observer = activity.getWindow().getDecorView().getViewTreeObserver();
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalFocusChangeListener(watcher);
        }
    }

    /** Кнопка отправки рядом с полем: обходим не весь экран, а несколько родителей вверх. */
    void watchSendNear(View edit) {
        android.view.ViewParent up = edit.getParent();
        for (int i = 0; i < 3 && up instanceof ViewGroup && ((ViewGroup) up).getParent() instanceof ViewGroup; i++) {
            up = up.getParent();
        }
        if (up instanceof ViewGroup) {
            watchSendAll((ViewGroup) up);
        }
    }

    void attachOne(EditText edit) {
        if (!enabled) return;
        if (edit != null && !(edit.getTransformationMethod() instanceof PasswordTransformationMethod)) {
            if (!inputs.containsKey(edit)) {
                InputTracker tracker = new InputTracker(this, edit);
                inputs.put(edit, tracker);
            }
        }
    }

    void attachAll(ViewGroup root) {
        if (root == null) return;
        List<EditText> list = ttcuz().views(root, EditText.class);
        for (int i = 0; i < list.size(); i++) {
            attachOne(list.get(i));
        }
    }

    // Кнопки отправки находим по описанию «Send» / «Отправить». Нажатие запоминаем, чтобы
    // очистку поля после отправки не принять за стирание буквами.
    void watchSendAll(ViewGroup root) {
        if (root == null) return;
        List<View> list = ttcuz().views(root, View.class);
        for (int i = 0; i < list.size(); i++) {
            watchSend(list.get(i));
        }
    }

    void watchSend(View view) {
        if (view == null || sendViews.containsKey(view) || !isSendButton(view)) return;
        sendViews.put(view, Boolean.TRUE);
        // Если у кнопки уже есть обработчик касаний, вызываем его, чтобы не сломать её анимацию
        final View.OnTouchListener previous = existingTouchListener(view);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View button, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    armSend(button);
                }
                return previous != null && previous.onTouch(button, event);
            }
        });
    }

    static boolean isSendButton(View view) {
        CharSequence description = view.getContentDescription();
        if (description != null) {
            String text = description.toString().toLowerCase();
            if (text.contains("send") || text.contains("отправ")) return true;
        }
        // Часто у кнопки нет описания, но в имени ресурса есть «send»
        try {
            if (view.getId() != View.NO_ID && view.getResources() != null) {
                String name = view.getResources().getResourceEntryName(view.getId());
                if (name != null) {
                    name = name.toLowerCase();
                    return name.contains("send") && !name.contains("sending") && !name.contains("sender");
                }
            }
        } catch (Throwable ignored) {
            // у view нет читаемого имени
        }
        return false;
    }

    static View.OnTouchListener existingTouchListener(View view) {
        try {
            java.lang.reflect.Field infoField = View.class.getDeclaredField("mListenerInfo");
            infoField.setAccessible(true);
            Object info = infoField.get(view);
            if (info == null) return null;
            java.lang.reflect.Field touchField = info.getClass().getDeclaredField("mOnTouchListener");
            touchField.setAccessible(true);
            return (View.OnTouchListener) touchField.get(info);
        } catch (Throwable ignored) {
            return null;
        }
    }

    void armSend(View button) {
        int[] location = new int[2];
        button.getLocationInWindow(location);
        sendPoint = new float[]{
            location[0] + button.getWidth() / 2.0f,
            location[1] + button.getHeight() / 2.0f
        };
        sendUntil = SystemClock.uptimeMillis() + SEND_WINDOW_MS;
    }

    // Точка отправки отдаётся один раз и только в коротком окне после нажатия кнопки
    public float[] takeSendPoint() {
        float[] point = sendPoint;
        sendPoint = null;
        if (point == null || SystemClock.uptimeMillis() > sendUntil) return null;
        return point;
    }

    public boolean sendSparksEnabled() {
        return sendSparks;
    }

    public int getSendPreset() {
        return sendPreset;
    }

    public int getSendPalette() {
        return sendPalette;
    }

    public int getSendAmount() {
        return sendAmount;
    }

    public boolean sendGuessEnabled() {
        return sendGuess;
    }

    public int getSendShape() {
        return sendShape;
    }

    public int getSendSwirl() {
        return sendSwirl;
    }

    public int getSendDir() {
        return sendDir;
    }

    public int getSendMotion() {
        return sendMotion;
    }

    public int getSendDuration() {
        return sendDuration;
    }

    public int getSendArc() {
        return sendArc;
    }

    public int getSendWave() {
        return sendWave;
    }

    public int getSendMorph() {
        return sendMorph;
    }

    public int getSendSpeed() {
        return sendSpeed;
    }
}
