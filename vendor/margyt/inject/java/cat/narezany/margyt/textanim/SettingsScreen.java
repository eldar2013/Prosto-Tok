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
// Своё меню настроек: превью эффекта сверху, настройки под ним
// =========================================================================
public final class SettingsScreen {
    static final String SAMPLE = "привет, это я";
    static final long TYPE_MS = 125L;
    static final long ERASE_MS = 65L;
    static final long HOLD_MS = 1300L;
    static final long PAUSE_MS = 650L;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static final int BACKGROUND = 0xFF121116;
    static final int CARD = 0xFF1C1B21;
    static final int PILL = 0xFF2A2931;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFF9B9AA1;
    static final int TRACK_OFF = 0xFF4A494F;
    static final int STROKE = 0x1AFFFFFF;
    static final int DIVIDER = 0x12FFFFFF;
    static final int DANGER = 0xFFFF6B6B;
    static final int DEFAULT_ACCENT = 0xFFB9A8FF;
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static final String[] EASING_NAMES = {
        "Мягкая", "Линейная", "Кубическая", "С перелётом", "Пружина", "Отскок",
        "Резкий старт", "Плавный разгон"
    };
    static final String[] TILT_DIR_NAMES = {"Влево", "Вправо", "По очереди", "Случайно"};
    // Тип полёта при отправке: 0 — как у эффекта, 1..8 — кривые из EASING_NAMES (по порядку), 9 — вихрь
    static final String[] MOTION_NAMES = {
        "Как у эффекта", "Мягкая", "Линейная", "Кубическая", "С перелётом", "Пружина", "Отскок",
        "Резкий старт", "Плавный разгон", "Вихрь"
    };
    static final String[] PIVOT_NAMES = {"Центр буквы", "Низ буквы", "Верх буквы"};
    static final String[] BLUR_MODE_NAMES = {"Мягкое", "Лёгкое"};
    static final String[] EFFECT_NAMES = {
        "Мягкий выезд", "Из размытия", "Падение", "Пружина", "Из увеличенного", "Волна", "Резко"
    };
    static final String[] GLOW_NAMES = {
        "Радужный перелив", "Розовый TikTok", "Бирюзовый TikTok", "Цвет акцента",
        "Золотой неон", "Белый неон", "Зелёный неон", "Фиолетовый неон", "Свой цвет"
    };
    static final String[] SPARK_NAMES = {
        "TikTok", "Неон", "Огонь", "Радуга", "Цвет текста", "Свои цвета"
    };
    static final String[] STYLE_NAMES = {
        "Пыль", "Снежинки", "Сакура", "Буквы", "Падение", "Песок", "Дым", "Пузыри", "Искры"
    };
    static final String[] CARET_COLOR_NAMES = {
        "Акцент", "Как текст", "Белый", "Фиолетовый", "Розовый", "Бирюзовый",
        "Радуга", "Золотой", "Зелёный", "Свой цвет"
    };
    static final String[] PRESET_NAMES = {"Как на видео", "Мягкий", "Яркий", "Лёгкий", "Песок", "Дым"};
    static final int[] PRESET_ICONS = {
        IconView.PLAY, IconView.CLOUD, IconView.RAINBOW, IconView.LEAF,
        IconView.BACKSPACE, IconView.GLOW
    };
    static final String[] PRESET_DESCRIPTIONS = {
        "Пружина и неон", "Спокойно и плавно", "Радуга и искры", "Минимум эффектов",
        "Крошится по форме буквы", "Растворяется в воздухе"
    };
    static final String[] SLIDE_DIR_NAMES = {"Сверху", "Снизу", "Слева", "Справа", "Случайно"};
    static final String[] CARET_SHAPE_NAMES = {"Палочка", "Прямоугольник", "Подчёркивание", "Блок"};
    static final String[] SMOKE_TONE_NAMES = {"Цвет букв", "Серый", "Тлеющий", "Цвета искр"};
    static final String[] LANG_NAMES = {"Авто", "Русский", "English"};
    static final String HINT_DEMO = "Нажми на поле и печатай сам";
    static final String HINT_TYPING = "Печатаешь сам · нажми сюда, чтобы вернуть демо";

    public final Activity host;
    final Main main;
    FrameLayout root;
    View placed;
    LinearLayout page;
    LinearLayout list;
    EditText preview;
    int accent;
    int runId;
    boolean closed;
    boolean typing;
    TextView hint;
    // Кнопка «Отправить» превью и номер цикла демо: демо чередует стирание и отправку
    View sendBtn;
    int cycle;
    // Чистый вид: редкие параметры под переключателем «Расширенные», пояснения — под «Подсказки»
    boolean advanced;
    boolean hints;
    final ArrayList<View> advRows = new ArrayList<View>();
    final ArrayList<View> advHeads = new ArrayList<View>();
    final ArrayList<View> hintViews = new ArrayList<View>();
    static final String ADV_KEYS = ",jitter,stagger,fade_start,slide_dp,slide_dir,scale_start,pivot,tilt_dir,squash,shake,"
        + "blur_end,blur_mode,glow_ms,aura,gradient_text,caret_width,caret_height,caret_glow,caret_morph,caret_stretch,"
        + "caret_jump,caret_dive,caret_pulse,blink_ms,sel_stretch,sel_side,send_guess,send_shape,send_motion,"
        + "send_duration,send_arc,send_swirl,send_dir,send_wave,send_morph,spark_size,part_speed,part_life,wind,blur_erase,";
    // Полоски-образцы своих цветов и число слотов «Мои пресеты»
    View swatchA;
    View swatchB;
    static final int USER_SLOTS = 3;
    boolean resetArmed;
    // Какие группы раскрыты: Появление, Движение, Размытие и свечение, Курсор, Отправка, Стирание
    final boolean[] open = {true, false, false, false, false, false, false};
    final java.util.HashMap<String, ArrayList<View>> links = new java.util.HashMap<String, ArrayList<View>>();
    final ArrayList<View> smokeRows = new ArrayList<View>();
    // Строки, которые нужны только для выбранного типа полёта или вихря
    final ArrayList<View> flightRows = new ArrayList<View>();
    final ArrayList<View> vortexRows = new ArrayList<View>();
    final java.util.HashMap<String, Boolean> defaults = new java.util.HashMap<String, Boolean>();

    public SettingsScreen(Main main, Activity host) {
        this.main = main;
        this.host = host;
    }

    public void show() {
        int accentColor = main.ttcuz().accent();
        accent = accentColor != 0 ? accentColor : DEFAULT_ACCENT;
        Strings.setMode(prefs().getInt("lang", 0));

        root = new FrameLayout(host);
        root.setBackgroundColor(BACKGROUND);
        root.setClickable(true);
        root.setLayoutParams(new FrameLayout.LayoutParams(MATCH, MATCH));

        page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, systemBar("status_bar_height"), 0, 0);
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));

        page.addView(buildHeader(), new LinearLayout.LayoutParams(MATCH, WRAP));
        page.addView(buildPreview(), params(MATCH, WRAP, 12, 10, 12, 8));

        ScrollView scroll = new ScrollView(host);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(4), dp(12), systemBar("navigation_bar_height") + dp(24));
        buildSettings();
        scroll.addView(list, new ViewGroup.LayoutParams(MATCH, WRAP));
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1.0f));

        View result = main.ttcuz().overlay(host, root, Gravity.TOP | Gravity.START);
        placed = result != null ? result : root;
        main.ttcuz().animateIn(placed);

        root.post(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                fitSystemBars();
                preview.requestFocus();
                restartDemo();
            }
        });
    }

    public void close(boolean animate) {
        if (closed) {
            return;
        }
        closed = true;
        runId++;
        hideKeyboard();
        main.detachInput(preview);
        if (main.settingsScreen == this) {
            main.settingsScreen = null;
        }
        final View target = placed != null ? placed : root;
        try {
            if (animate) {
                main.ttcuz().animateOut(target, new Runnable() {
                    @Override
                    public void run() {
                        removeLayer(target);
                    }
                });
            } else {
                removeLayer(target);
            }
        } catch (Throwable ignored) {
            // плагин уже остановлен: слой уберёт мод
        }
    }

    void removeLayer(View target) {
        try {
            main.ttcuz().removeOverlay(target);
        } catch (Throwable ignored) {
            // слой уже убран
        }
    }

    // ---------------------------------------------------------------
    // Превью: настоящее поле ввода с тем же движком анимации
    // ---------------------------------------------------------------
    View buildPreview() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(pill(CARD, tint(accent, 0x44), 24));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(host);
        dot.setBackground(rounded(accent, 10));
        top.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView label = text("ЖИВОЕ ПРЕВЬЮ", 11, MUTED);
        label.setTypeface(MEDIUM);
        label.setLetterSpacing(0.08f);
        label.setPadding(dp(8), 0, 0, 0);
        top.addView(label);
        card.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        preview = new EditText(host);
        preview.setSingleLine(true);
        preview.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        preview.setTextColor(TEXT);
        preview.setBackground(pill(PILL, STROKE, 100));
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(20), 0, dp(20), 0);
        preview.setShowSoftInputOnFocus(false);
        preview.setLongClickable(false);
        preview.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_BACK) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    close(true);
                }
                return true;
            }
        });
        preview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && !typing) {
                    startTyping();
                }
                return false;
            }
        });
        LinearLayout inputRow = new LinearLayout(host);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.addView(preview, new LinearLayout.LayoutParams(0, dp(56), 1.0f));

        // Кнопка отправки: по нажатию текст «отправляется» и показывается выбранный эффект
        FrameLayout sendButton = new FrameLayout(host);
        sendBtn = sendButton;
        sendButton.setBackground(rounded(accent, 100));
        sendButton.setContentDescription("Send");
        sendButton.addView(new IconView(host, IconView.SEND, onAccent()),
            new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDemo(view);
            }
        });
        inputRow.addView(sendButton, params(dp(56), dp(56), 8, 0, 0, 0));
        card.addView(inputRow, params(MATCH, WRAP, 0, 10, 0, 0));

        hint = text(HINT_DEMO, 12, MUTED);
        hint.setPadding(dp(4), dp(10), dp(4), 0);
        hint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (typing) {
                    stopTyping();
                }
            }
        });
        card.addView(hint, new LinearLayout.LayoutParams(WRAP, WRAP));
        return card;
    }

    public void restartDemo() {
        if (closed || preview == null || typing) {
            return;
        }
        main.attachOne(preview);
        runId++;
        final int id = runId;
        main.muteInput(preview, true);
        Editable text = preview.getText();
        if (text != null) {
            text.clear();
        }
        main.muteInput(preview, false);
        schedule(450L, new Runnable() {
            @Override
            public void run() {
                typeStep(id, 0);
            }
        });
    }

    // Тап по превью: демо замолкает, можно печатать самому с клавиатуры
    void startTyping() {
        typing = true;
        runId++;
        main.attachOne(preview);
        main.muteInput(preview, true);
        Editable text = preview.getText();
        if (text != null) {
            text.clear();
        }
        main.muteInput(preview, false);
        preview.setShowSoftInputOnFocus(true);
        preview.requestFocus();
        setTr(hint, HINT_TYPING);
        InputMethodManager manager = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(preview, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    void stopTyping() {
        typing = false;
        hideKeyboard();
        preview.setShowSoftInputOnFocus(false);
        setTr(hint, HINT_DEMO);
        restartDemo();
    }

    void hideKeyboard() {
        if (preview == null || !typing) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(preview.getWindowToken(), 0);
        }
    }

    void typeStep(final int id, final int index) {
        if (closed || id != runId) {
            return;
        }
        if (index == 0) {
            cycle++;
        }
        if (index >= sample().length()) {
            schedule(HOLD_MS, new Runnable() {
                @Override
                public void run() {
                    afterHold(id);
                }
            });
            return;
        }
        if (index == 0 && main.getConfig().stagger > 0) {
            // Задержка между буквами видна только на вставленном тексте: показываем «вставку» целиком
            preview.getText().append(sample());
            schedule(HOLD_MS + sample().length() * 60L, new Runnable() {
                @Override
                public void run() {
                    afterHold(id);
                }
            });
            return;
        }
        preview.getText().append(String.valueOf(sample().charAt(index)));
        schedule(TYPE_MS, new Runnable() {
            @Override
            public void run() {
                typeStep(id, index + 1);
            }
        });
    }

    // После паузы с текстом демо по очереди стирает его и отправляет: видны оба эффекта
    void afterHold(final int id) {
        if (closed || id != runId) {
            return;
        }
        if (sendBtn != null && cycle % 2 == 0) {
            sendDemo(sendBtn);
        } else {
            eraseStep(id);
        }
    }

    // Настройки отправки проверяем сразу: превью тут же отправляет пример с новыми значениями
    void applySendChange() {
        main.reloadConfig();
        updateSendRows();
        if (sendBtn != null) {
            sendDemo(sendBtn);
        } else {
            restartDemo();
        }
    }

    void eraseStep(final int id) {
        if (closed || id != runId) {
            return;
        }
        Editable text = preview.getText();
        int length = text.length();
        if (length == 0) {
            schedule(PAUSE_MS, new Runnable() {
                @Override
                public void run() {
                    typeStep(id, 0);
                }
            });
            return;
        }
        text.delete(length - 1, length);
        schedule(ERASE_MS, new Runnable() {
            @Override
            public void run() {
                eraseStep(id);
            }
        });
    }

    // Кнопка отправки в превью: «отправляет» текст из поля и запускает выбранный эффект
    void sendDemo(final View button) {
        if (closed || preview == null) {
            return;
        }
        button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (!typing) {
            runId++;
        }
        final int id = runId;
        Editable text = preview.getText();
        if (text != null && text.length() == 0) {
            // Поле пустое: сначала набираем пример, чтобы было что отправлять
            main.attachOne(preview);
            text.append(sample());
            schedule(650L, new Runnable() {
                @Override
                public void run() {
                    finishSend(button, id);
                }
            });
            return;
        }
        finishSend(button, id);
    }

    void finishSend(View button, final int id) {
        if (closed || preview == null || (!typing && id != runId)) {
            return;
        }
        Editable text = preview.getText();
        if (text == null || text.length() == 0) {
            return;
        }
        // Так же, как настоящая кнопка: запоминаем точку отправки и очищаем поле
        main.armSend(button);
        text.clear();
        if (!typing) {
            schedule(1900L, new Runnable() {
                @Override
                public void run() {
                    if (id == runId) {
                        restartDemo();
                    }
                }
            });
        }
    }

    void schedule(long delay, Runnable action) {
        main.ttcuz().later(action, delay);
    }

    // ---------------------------------------------------------------
    // Шапка и строки настроек
    // ---------------------------------------------------------------
    View buildHeader() {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(6), dp(18), dp(4));

        FrameLayout back = new FrameLayout(host);
        back.setBackground(pill(CARD, STROKE, 20));
        back.addView(new IconView(host, IconView.BACK, TEXT),
            new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                close(true);
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout names = new LinearLayout(host);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14), 0, 0, 0);
        TextView title = text("Анимация текста", 20, TEXT);
        title.setTypeface(MEDIUM);
        names.addView(title);
        TextView subtitle = text("Настрой, как оживает набор", 12, MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        names.addView(subtitle);
        header.addView(names, new LinearLayout.LayoutParams(0, WRAP, 1.0f));
        return header;
    }

    void buildSettings() {
        list.removeAllViews();
        links.clear();
        smokeRows.clear();
        flightRows.clear();
        vortexRows.clear();
        defaults.clear();
        advRows.clear();
        advHeads.clear();
        hintViews.clear();
        advanced = prefs().getBoolean("advanced", false);
        hints = prefs().getBoolean("hints", false);
        resetArmed = false;

        presets();

        LinearLayout modes = new LinearLayout(host);
        modes.setOrientation(LinearLayout.VERTICAL);
        modes.setBackground(pill(CARD, STROKE, 20));
        list.addView(modes, params(MATCH, WRAP, 0, 14, 0, 0));
        toggle(modes, "advanced", "Расширенные настройки", false);
        toggle(modes, "hints", "Подсказки под пунктами", false);

        // ===== ПОЯВЛЕНИЕ БУКВ =====
        LinearLayout body = section(0, IconView.SPARKLE, "Появление букв", "Эффект, скорость и очерёдность");
        effects(body);
        slider(body, "duration", "Время появления", "Сколько длится появление одной буквы",
            80, 800, 260, " мс", 1);
        choice(body, "easing", "Кривая движения",
            "Как меняется скорость: плавно, с пружиной, с отскоком…", EASING_NAMES, 4);
        slider(body, "fade_start", "Яркость в начале",
            "0% — буква проявляется из пустоты, 100% — сразу видна полностью", 0, 100, 25, "%", 1);
        slider(body, "stagger", "Задержка между буквами",
            "Если вставить много текста сразу, буквы выходят по очереди, как волна. 0 — все вместе",
            0, 100, 0, " мс", 1);
        slider(body, "jitter", "Разброс скорости",
            "Каждая буква появляется чуть быстрее или медленнее остальных — выглядит живее",
            0, 60, 0, "%", 1);

        // ===== ДВИЖЕНИЕ И РАЗМЕР =====
        body = section(1, IconView.PLAY, "Движение и размер", "Въезд, рост, наклон, сжатие и дрожь");
        toggle(body, "slide", "Въезд буквы", "Буква выезжает на своё место", true);
        View slideHeight = slider(body, "slide_dp", "Расстояние въезда",
            "С какого расстояния буква начинает движение", 0, 40, 8, " dp", 1);
        View slideDir = choice(body, "slide_dir", "Откуда выезжает",
            "«Случайно» — у каждой буквы своя сторона", SLIDE_DIR_NAMES, 0);
        link("slide", slideHeight, slideDir);

        toggle(body, "scale", "Изменение размера", "Буква появляется другого размера и приходит к обычному", true);
        View scaleStart = slider(body, "scale_start", "Размер буквы в начале",
            "Меньше 100% — буква вырастает, больше 100% — сжимается до обычного размера",
            20, 200, 35, "%", 1);
        View pivot = choice(body, "pivot", "Точка роста",
            "Вокруг какой точки буква растёт и наклоняется", PIVOT_NAMES, 0);
        link("scale", scaleStart, pivot);

        slider(body, "tilt", "Наклон", "На сколько градусов буква наклонена в начале и «встаёт» ровно",
            0, 60, 0, "°", 1);
        choice(body, "tilt_dir", "Куда наклоняется", null, TILT_DIR_NAMES, 0);
        slider(body, "squash", "Сжатие", "Буква падает приплюснутой и выпрямляется, как желе",
            0, 60, 0, "%", 1);
        slider(body, "shake", "Дрожь", "Лёгкое покачивание из стороны в сторону, быстро затухает",
            0, 12, 0, " dp", 1);

        // ===== РАЗМЫТИЕ И СВЕЧЕНИЕ =====
        body = section(2, IconView.GLOW, "Размытие и свечение", "Расфокус при появлении и подсветка свежих букв");
        toggle(body, "blur", "Размытие при появлении", "Буква выезжает размытой и становится чёткой", true);
        View blurDp = slider(body, "blur_dp", "Сила размытия",
            "Насколько сильно размыта буква в самом начале", 1, 30, 6, " dp", 1);
        View blurEnd = slider(body, "blur_end", "Когда становится чёткой",
            "К какому моменту появления буква уже резкая. Меньше — чёткость приходит раньше",
            20, 100, 80, "%", 1);
        View blurMode = choice(body, "blur_mode", "Качество размытия",
            "«Мягкое» — настоящее размытие, красивее. «Лёгкое» — упрощённое, быстрее и работает на любом телефоне",
            BLUR_MODE_NAMES, 0);
        link("blur", blurDp, blurEnd, blurMode);

        toggle(body, "glow", "Свечение недавних букв", "Буквы горят цветом и плавно гаснут", true);
        View glowTime = slider(body, "glow_ms", "Длительность свечения", 200, 3000, 1500, " мс");
        View glowColor = choice(body, "glow_mode", "Цвет свечения", GLOW_NAMES, 0);
        View aura = toggle(body, "aura", "Аура вокруг букв", "Мягкий ореол во время свечения", true);
        View gradient = toggle(body, "gradient_text", "Градиентный текст", "TikTok-градиент на горящих буквах", false);
        link("glow", glowTime, glowColor, aura, gradient);

        // ===== КУРСОР И ВЫДЕЛЕНИЕ =====
        body = section(3, IconView.CARET, "Курсор и выделение", "Вид, движение, мигание и ручки выделения");
        subhead(body, "ВИД");
        toggle(body, "caret", "Плавающий курсор", "Заменяет стандартный курсор", true);
        View color = choice(body, "caret_color", "Цвет курсора", CARET_COLOR_NAMES, 0);
        View shape = choice(body, "caret_shape", "Форма курсора", CARET_SHAPE_NAMES, 0);
        View width = slider(body, "caret_width", "Толщина курсора", 10, 60, 26, " dp", 10);
        View height = slider(body, "caret_height", "Высота курсора", 50, 120, 100, "%");
        View caretGlow = slider(body, "caret_glow", "Свечение курсора", 0, 100, 0, "%");

        subhead(body, "ДВИЖЕНИЕ");
        View speed = slider(body, "caret_speed", "Скорость курсора", 5, 100, 25, "", 1);
        View morph = slider(body, "caret_morph", "Плавность смены формы",
            "Как быстро курсор перетекает из одной формы в другую. Меньше — быстрее",
            20, 500, 110, " мс", 1);
        View liquid = toggle(body, "liquid", "Жидкий режим", "Растяжение и прыжки при вводе", true);
        View stretchS = slider(body, "caret_stretch", "Растяжение курсора", 0, 200, 100, "%");
        View jumpT = toggle(body, "caret_jump", "Прыжок на пробеле", "Курсор подпрыгивает и ныряет обратно", true);
        View diveT = toggle(body, "caret_dive", "Нырок на Enter", null, true);
        View pulseT = toggle(body, "caret_pulse", "Сжатие при стирании", null, true);
        View blink = toggle(body, "blink", "Мягкое мигание", null, true);
        View blinkS = slider(body, "blink_ms", "Период мигания", 500, 2500, 1000, " мс");

        subhead(body, "ВЫДЕЛЕНИЕ ТЕКСТА");
        View selT = toggle(body, "sel_effect", "Жидкие ручки выделения",
            "На границах выделенного текста растут «капли», тянутся за пальцем и слипаются", true);
        View selStretch = slider(body, "sel_stretch", "Растяжение ручек",
            "Насколько сильно ручки тянутся за движением границы", 0, 240, 60, "%", 1);
        View selSide = slider(body, "sel_side", "Боковой вынос ручек",
            "Насколько ручки разъезжаются в стороны и слипаются, когда выделение узкое", 0, 240, 50, "%", 1);

        link("caret", liquid, speed, width, color, blink, shape, height, morph, caretGlow, selT, selStretch, selSide);
        link("sel_effect", selStretch, selSide);
        link("liquid", jumpT, diveT, pulseT, stretchS);
        link("blink", blinkS);

        // ===== ОТПРАВКА СООБЩЕНИЯ =====
        body = section(4, IconView.SEND, "Отправка сообщения", "Что вылетает из букв и кнопки «Отправить»");
        subhead(body, "ЧТО ВЫЛЕТАЕТ");
        toggle(body, "send_particles", "Эффект при отправке",
            "Частицы вылетают из букв или из кнопки, когда нажимаешь «Отправить»", true);
        View sendGuessRow = toggle(body, "send_guess", "Угадывать отправку",
            "Если кнопка «Отправить» не найдена, считать отправкой полную очистку поля", true);
        View sendPresetRow = choice(body, "send_preset", "Какой эффект",
            "Взлёт, взрыв, салют, конфетти, сердечки, вихрь в кнопку…", Particles.sendPresetNames(false), 1);
        View sendPaletteRow = choice(body, "send_palette", "Цвета частиц",
            "«Как при стирании» берёт цвета из раздела «Стирание букв»", Particles.sendPaletteNames(false), 0);
        View sendAmountRow = slider(body, "send_amount", "Сколько частиц",
            "Частиц с каждой буквы (для салюта и конфетти — общее количество)", 1, 8, 4, "", 1);
        View sendSpeedRow = slider(body, "send_speed", "Скорость частиц", 40, 300, 100, "%");
        View sendShapeRow = choice(body, "send_shape", "Во что превращаются буквы",
            "Форма частиц: точки, штрихи, снежинки, лепестки, пузыри, сердечки, конфетти или сами буквы",
            Particles.sendShapeNames(false), 0);
        link("send_particles", sendGuessRow, sendPresetRow, sendPaletteRow, sendAmountRow, sendSpeedRow,
            sendShapeRow);

        subhead(body, "ПОЛЁТ");
        View motionRow = choice(body, "send_motion", "Тип полёта",
            "Как частицы летят к точке назначения. «Как у эффекта» — прежняя физика, остальные — плавная кривая, «Вихрь» — спираль",
            MOTION_NAMES, 0);
        View durRow = slider(body, "send_duration", "Время полёта",
            "Сколько длится полёт от буквы до точки назначения", 150, 1500, 600, " мс", 1);
        View arcRow = slider(body, "send_arc", "Изгиб пути",
            "Выгибает траекторию в одну сторону. 0 — прямой путь, минус — в другую", -100, 100, 0, "%", 1);
        View swirlRow = slider(body, "send_swirl", "Закрутка вихря",
            "0 — летят прямо, больше — сильнее закручиваются по спирали. Для «Вихрь в кнопку» и типа полёта «Вихрь»",
            0, 250, 100, "%", 1);
        View dirRow = choice(body, "send_dir", "Направление вихря",
            "Сторона закрутки. Для «Вихрь в кнопку» и типа полёта «Вихрь»",
            new String[]{"По часовой", "Против часовой", "Вперемешку"}, 0);
        flightRows.add(durRow);
        flightRows.add(arcRow);
        vortexRows.add(swirlRow);
        vortexRows.add(dirRow);

        subhead(body, "ПРЕВРАЩЕНИЕ БУКВ");
        View waveRow = slider(body, "send_wave", "Волна по буквам",
            "Когда каждая буква отпускает частицы: 0 — все сразу, 100 — как раньше, больше — медленнее",
            0, 200, 100, "%", 1);
        View morphRow = slider(body, "send_morph", "Плавность превращения",
            "Сколько букве нужно, чтобы раствориться, пока из неё вылетают частицы. 0 — как «Время появления». Нужно «Плавное исчезновение буквы»",
            0, 1500, 0, " мс", 1);

        // ===== СТИРАНИЕ БУКВ =====
        body = section(5, IconView.BACKSPACE, "Стирание букв", "Что происходит со стёртой буквой");
        toggle(body, "particles", "Частицы при стирании", "Стёртая буква рассыпается", true);
        View style = choice(body, "style", "Вид частиц", STYLE_NAMES, 0);
        View palette = choice(body, "spark_palette", "Цвета частиц", SPARK_NAMES, 0);
        View amount = slider(body, "amount", "Сколько частиц", 1, 12, 6, "", 1);
        View sparkSize = slider(body, "spark_size", "Размер частиц", 50, 200, 100, "%");
        View partSpeed = slider(body, "part_speed", "Скорость разлёта", 50, 250, 100, "%");
        View partLife = slider(body, "part_life", "Время жизни частиц", 40, 250, 100, "%");
        View windS = slider(body, "wind", "Ветер (влево ‹ › вправо)", -100, 100, 0, "");
        link("particles", style, palette, amount, sparkSize, partSpeed, partLife, windS);
        toggle(body, "ghosts", "Плавное исчезновение буквы", "Буква гаснет, а не пропадает мгновенно", true);
        toggle(body, "blur_erase", "Размытие при стирании",
            "Стёртая буква расплывается, пока исчезает (нужно «Плавное исчезновение»)", true);
        toggle(body, "haptic", "Виброотклик при удалении", null, false);
        // Настройки дыма видны только для вида «Дым», поэтому стоят в конце группы
        smokeRows.add(slider(body, "smoke_density", "Плотность дыма", 20, 160, 80, ""));
        smokeRows.add(slider(body, "smoke_rise", "Подъём дыма", 30, 250, 100, "%"));
        smokeRows.add(slider(body, "smoke_spread", "Разрастание", 0, 200, 100, "%"));
        smokeRows.add(slider(body, "smoke_swirl", "Завихрения", 0, 200, 100, "%"));
        smokeRows.add(slider(body, "smoke_hold", "Задержка распада", 0, 100, 30, "%"));
        smokeRows.add(choice(body, "smoke_tone", "Цвет дыма", SMOKE_TONE_NAMES, 0));
        updateSmokeRows();
        link("particles", smokeRows.toArray(new View[0]));

        // Плавность превращения нужна только тогда, когда буквы растворяются
        link("ghosts", morphRow);
        updateSendRows();

        // ===== СВОИ ЦВЕТА =====
        body = section(6, IconView.RAINBOW, "Свои цвета", "Два цвета для свечения, курсора и частиц");
        TextView colorHint = text("Чтобы применить, выбери «Свой цвет» в настройках свечения и курсора, а «Свои цвета» — в цветах частиц", 12, MUTED);
        colorHint.setPadding(dp(16), dp(10), dp(16), dp(6));
        body.addView(colorHint, new LinearLayout.LayoutParams(MATCH, WRAP));
        subhead(body, "ЦВЕТ 1 · СВЕЧЕНИЕ, КУРСОР, ЧАСТИЦЫ");
        slider(body, "ca_h", "Оттенок", 0, 359, 340, "°", 1);
        slider(body, "ca_s", "Насыщенность", 0, 100, 85, "%", 1);
        slider(body, "ca_v", "Яркость", 20, 100, 100, "%", 1);
        swatchA = swatchRow(body, "ca");
        subhead(body, "ЦВЕТ 2 · ВТОРОЙ ЦВЕТ ЧАСТИЦ");
        slider(body, "cb_h", "Оттенок", 0, 359, 180, "°", 1);
        slider(body, "cb_s", "Насыщенность", 0, 100, 85, "%", 1);
        slider(body, "cb_v", "Яркость", 20, 100, 100, "%", 1);
        swatchB = swatchRow(body, "cb");

        // ===== ОБЩЕЕ: язык и сброс =====
        LinearLayout general = new LinearLayout(host);
        general.setOrientation(LinearLayout.VERTICAL);
        general.setBackground(pill(CARD, STROKE, 20));
        list.addView(general, params(MATCH, WRAP, 0, 14, 0, 0));
        choice(general, "lang", "Язык интерфейса", "Автоматически, русский или English", LANG_NAMES, 0);
        toggle(general, "eco", "Экономия заряда",
            "Без размытия и частиц при энергосбережении, а при выключенных анимациях системы — ещё и без движения", true);

        list.addView(actionButton("Скопировать настройки", true), params(MATCH, WRAP, 0, 18, 0, 0));
        list.addView(actionButton("Вставить настройки", false), params(MATCH, WRAP, 0, 10, 0, 0));
        list.addView(resetButton(), params(MATCH, WRAP, 0, 10, 0, 0));

        TextView footer = text("Text Animation · @mihailkotovski & @mishabotov", 11, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setAlpha(0.7f);
        list.addView(footer, params(MATCH, WRAP, 0, 14, 0, 0));
    }

    // Кнопка сброса с подтверждением вторым тапом
    View resetButton() {
        final TextView reset = text("Сбросить настройки", 15, accent);
        reset.setTypeface(MEDIUM);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(16), dp(14), dp(16), dp(14));
        reset.setBackground(pill(0, tint(accent, 0x66), 20));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!resetArmed) {
                    resetArmed = true;
                    setTr(reset, "Нажми ещё раз — вернём всё как было");
                    reset.setTextColor(DANGER);
                    reset.setBackground(pill(tint(DANGER, 0x1F), tint(DANGER, 0x88), 20));
                    schedule(2800L, new Runnable() {
                        @Override
                        public void run() {
                            if (closed || !resetArmed) {
                                return;
                            }
                            resetArmed = false;
                            setTr(reset, "Сбросить настройки");
                            reset.setTextColor(accent);
                            reset.setBackground(pill(0, tint(accent, 0x66), 20));
                        }
                    });
                    return;
                }
                resetArmed = false;
                int lang = prefs().getInt("lang", 0);
                prefs().edit().clear().putInt("lang", lang).putBoolean("advanced", advanced).putBoolean("hints", hints).apply();
                buildSettings();
                applyChange();
            }
        });
        return reset;
    }

    // ---------------------------------------------------------------
    // Пресеты: плитки с эмодзи и коротким описанием
    // ---------------------------------------------------------------
    void presets() {
        TextView label = text("БЫСТРЫЕ ПРЕСЕТЫ", 11, MUTED);
        label.setTypeface(MEDIUM);
        label.setLetterSpacing(0.08f);
        label.setPadding(dp(6), dp(6), 0, dp(8));
        list.addView(label);

        HorizontalScrollView scroll = new HorizontalScrollView(host);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        LinearLayout tiles = new LinearLayout(host);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setPadding(0, 0, dp(12), 0);
        for (int i = 0; i < PRESET_NAMES.length + USER_SLOTS; i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(host);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(14), dp(12), dp(14), dp(12));
            tile.setBackground(pill(CARD, STROKE, 18));

            tile.addView(badge(i < PRESET_ICONS.length ? PRESET_ICONS[i] : IconView.SPARKLE), new LinearLayout.LayoutParams(dp(38), dp(38)));
            // Пружина: название и описание прижаты к низу, поэтому все плитки выглядят одинаково
            tile.addView(new View(host), new LinearLayout.LayoutParams(0, 0, 1.0f));
            TextView name = text(i < PRESET_NAMES.length ? PRESET_NAMES[i] : "Мой пресет " + (i - PRESET_NAMES.length + 1), 14, TEXT);
            name.setTypeface(MEDIUM);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(name);
            final TextView desc = text(i < PRESET_DESCRIPTIONS.length ? PRESET_DESCRIPTIONS[i] : slotHint(i - PRESET_NAMES.length), 11, MUTED);
            desc.setLines(2);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            desc.setPadding(0, dp(2), 0, 0);
            tile.addView(desc);

            tile.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start();
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160L)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f)).start();
                    }
                    return false;
                }
            });
            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (index < PRESET_NAMES.length) {
                        applyPreset(index);
                    } else {
                        loadSlot(index - PRESET_NAMES.length, desc);
                    }
                }
            });
            if (index >= PRESET_NAMES.length) {
                tile.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        int slot = index - PRESET_NAMES.length;
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        saveSlot(slot);
                        setTr(desc, slotHint(slot));
                        return true;
                    }
                });
            }
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(150), dp(142));
            tileParams.setMargins(0, 0, dp(8), 0);
            tiles.addView(tile, tileParams);
        }
        scroll.addView(tiles, new ViewGroup.LayoutParams(WRAP, WRAP));
        list.addView(scroll, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // ---------------------------------------------------------------
    // Свои цвета, слоты «Мои пресеты», копирование и вставка настроек
    // ---------------------------------------------------------------
    int currentCustom(String prefix) {
        int hue = "ca".equals(prefix) ? 340 : 180;
        return Config.hsv(prefs().getInt(prefix + "_h", hue), prefs().getInt(prefix + "_s", 85),
            prefs().getInt(prefix + "_v", 100));
    }

    View swatchRow(LinearLayout body, String prefix) {
        View bar = new View(host);
        bar.setBackground(rounded(currentCustom(prefix), 10));
        LinearLayout row = new LinearLayout(host);
        row.setPadding(dp(16), dp(10), dp(16), dp(14));
        row.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(26)));
        addRow(body, row);
        return bar;
    }

    void refreshSwatches() {
        if (swatchA != null) {
            swatchA.setBackground(rounded(currentCustom("ca"), 10));
        }
        if (swatchB != null) {
            swatchB.setBackground(rounded(currentCustom("cb"), 10));
        }
    }

    SharedPreferences slots() {
        return host.getSharedPreferences("text_animation_slots", Context.MODE_PRIVATE);
    }

    String slotHint(int slot) {
        return slots().getString("slot_" + slot, null) == null
            ? "Пусто · удерживай, чтобы сохранить" : "Сохранён · удерживай, чтобы заменить";
    }

    void saveSlot(int slot) {
        slots().edit().putString("slot_" + slot, settingsCode()).apply();
    }

    void loadSlot(int slot, TextView desc) {
        String code = slots().getString("slot_" + slot, null);
        if (code == null || !applyCode(code)) {
            setTr(desc, slotHint(slot));
            return;
        }
        buildSettings();
        applyChange();
    }

    // Все свои настройки одной строкой: TA1:{json}. Язык интерфейса не передаётся
    String settingsCode() {
        org.json.JSONObject all = new org.json.JSONObject();
        java.util.Map<String, ?> values = prefs().getAll();
        for (java.util.Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if ("lang".equals(entry.getKey()) || "advanced".equals(entry.getKey()) || "hints".equals(entry.getKey())) {
                continue;
            }
            if (value instanceof Integer || value instanceof Boolean || value instanceof Long) {
                try {
                    all.put(entry.getKey(), value);
                } catch (Exception ignored) {
                    // пропускаем неподходящее значение
                }
            }
        }
        return "TA1:" + all.toString();
    }

    // Применяет код целиком или не трогает ничего: сначала разбор, потом запись
    boolean applyCode(String code) {
        if (code == null) {
            return false;
        }
        String text = code.trim();
        if (!text.startsWith("TA1:")) {
            return false;
        }
        try {
            org.json.JSONObject all = new org.json.JSONObject(text.substring(4));
            SharedPreferences.Editor edit = prefs().edit();
            edit.clear().putInt("lang", prefs().getInt("lang", 0));
            edit.putBoolean("advanced", advanced).putBoolean("hints", hints);
            java.util.Iterator<String> keys = all.keys();
            int count = 0;
            while (keys.hasNext()) {
                String key = keys.next();
                if ("lang".equals(key) || "advanced".equals(key) || "hints".equals(key) || !key.matches("[a-z0-9_]{1,32}")) {
                    continue;
                }
                Object value = all.get(key);
                if (value instanceof Boolean) {
                    edit.putBoolean(key, ((Boolean) value).booleanValue());
                    count++;
                } else if (value instanceof Integer || value instanceof Long) {
                    edit.putInt(key, ((Number) value).intValue());
                    count++;
                }
            }
            if (count == 0) {
                return false;
            }
            edit.apply();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    boolean copySettings() {
        try {
            android.content.ClipboardManager clip =
                (android.content.ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clip == null) {
                return false;
            }
            clip.setPrimaryClip(android.content.ClipData.newPlainText("Text Animation", settingsCode()));
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    boolean pasteSettings() {
        try {
            android.content.ClipboardManager clip =
                (android.content.ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clip == null || !clip.hasPrimaryClip() || clip.getPrimaryClip().getItemCount() == 0) {
                return false;
            }
            CharSequence value = clip.getPrimaryClip().getItemAt(0).coerceToText(host);
            if (value == null || !applyCode(value.toString())) {
                return false;
            }
            buildSettings();
            applyChange();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    // Кнопка-контур: копирует или вставляет настройки и на секунду показывает результат
    View actionButton(final String label, final boolean export) {
        final TextView button = text(label, 15, accent);
        button.setTypeface(MEDIUM);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setBackground(pill(0, tint(accent, 0x66), 20));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String result;
                if (export) {
                    result = copySettings() ? "Скопировано ✓" : "Не получилось";
                } else {
                    result = pasteSettings() ? "Применено ✓" : "В буфере нет настроек";
                }
                setTr(button, result);
                schedule(1800L, new Runnable() {
                    @Override
                    public void run() {
                        if (!closed) {
                            setTr(button, label);
                        }
                    }
                });
            }
        });
        return button;
    }

    void applyPreset(int index) {
        SharedPreferences.Editor edit = prefs().edit();
        edit.clear();
        edit.putInt("lang", prefs().getInt("lang", 0));
        edit.putBoolean("advanced", advanced).putBoolean("hints", hints);
        if (index == 0) {
            edit.putInt("duration", 260).putInt("easing", 4).putInt("scale_start", 35).putInt("slide_dp", 8);
            edit.putBoolean("glow", true).putInt("glow_mode", 3).putInt("glow_ms", 700);
            edit.putInt("spark_palette", 1).putInt("amount", 5);
        } else if (index == 1) {
            edit.putInt("duration", 220).putInt("easing", 0).putInt("scale_start", 88).putInt("slide_dp", 6);
            edit.putBoolean("glow", false).putBoolean("liquid", false);
            edit.putInt("spark_palette", 4).putInt("amount", 4).putInt("caret_width", 22);
        } else if (index == 2) {
            edit.putInt("duration", 300).putInt("easing", 3).putInt("scale_start", 25).putInt("slide_dp", 12);
            edit.putBoolean("glow", true).putInt("glow_mode", 0).putInt("glow_ms", 1800);
            edit.putBoolean("gradient_text", true);
            edit.putInt("spark_palette", 3).putInt("amount", 10);
        } else if (index == 3) {
            edit.putInt("duration", 160).putInt("easing", 1).putInt("scale_start", 90);
            edit.putBoolean("slide", false).putBoolean("scale", false).putBoolean("glow", false);
            edit.putBoolean("aura", false).putBoolean("liquid", false);
            edit.putBoolean("blur", false).putBoolean("blur_erase", false);
            edit.putInt("amount", 3);
        } else if (index == 4) {
            edit.putInt("duration", 240).putInt("easing", 4).putInt("scale_start", 70).putInt("slide_dp", 4);
            edit.putInt("tilt", 14);
            edit.putBoolean("glow", true).putInt("glow_mode", 3).putInt("glow_ms", 600);
            edit.putInt("style", Config.STYLE_SAND).putInt("spark_palette", 4).putInt("amount", 9);
        } else {
            edit.putInt("duration", 280).putInt("easing", 0).putInt("scale_start", 85).putInt("slide_dp", 6);
            edit.putBoolean("glow", false).putBoolean("aura", false);
            edit.putInt("style", Config.STYLE_SMOKE).putInt("smoke_density", 90).putInt("smoke_rise", 110);
            edit.putInt("smoke_spread", 120).putInt("smoke_swirl", 100).putInt("smoke_hold", 30);
        }
        edit.apply();
        buildSettings();
        applyChange();
    }

    // ---------------------------------------------------------------
    // Секции (сворачиваются) и строки
    // ---------------------------------------------------------------
    LinearLayout section(final int index, int icon, String title, String subtitle) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(pill(CARD, STROKE, 20));
        list.addView(card, params(MATCH, WRAP, 0, 14, 0, 0));

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(12), dp(14), dp(12));

        head.addView(badge(icon), new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout names = new LinearLayout(host);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(title, 16, TEXT);
        name.setTypeface(MEDIUM);
        names.addView(name);
        TextView sub = text(subtitle, 12, MUTED);
        sub.setPadding(0, dp(1), 0, 0);
        names.addView(sub);
        head.addView(names, params(0, WRAP, 12, 0, 8, 0));
        ((LinearLayout.LayoutParams) names.getLayoutParams()).weight = 1.0f;

        final View chevron = new IconView(host, IconView.CHEVRON, MUTED);
        chevron.setRotation(open[index] ? 180.0f : 0.0f);
        head.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(22)));
        card.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        final LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(open[index] ? View.VISIBLE : View.GONE);
        card.addView(body, new LinearLayout.LayoutParams(MATCH, WRAP));

        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean show = body.getVisibility() != View.VISIBLE;
                open[index] = show;
                if (show) {
                    body.setAlpha(0.0f);
                    body.setVisibility(View.VISIBLE);
                    body.animate().alpha(1.0f).setDuration(180L).start();
                } else {
                    body.setVisibility(View.GONE);
                }
                chevron.animate().rotation(show ? 180.0f : 0.0f).setDuration(180L).start();
            }
        });
        return body;
    }

    // Строки, которые не действуют при текущих настройках, приглушаются
    void updateSendRows() {
        int motion = prefs().getInt("send_motion", 0);
        int preset = prefs().getInt("send_preset", 1);
        setDimmed(flightRows, motion != Particles.MOTION_PRESET);
        setDimmed(vortexRows, motion == Particles.MOTION_VORTEX || preset == Particles.SEND_VORTEX);
    }

    void setDimmed(ArrayList<View> rows, boolean active) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setAlpha(active ? 1.0f : 0.38f);
        }
    }

    // Подзаголовок внутри группы: помогает ориентироваться в длинных списках
    void subhead(LinearLayout body, String title) {
        TextView label = text(title, 11, MUTED);
        label.setPadding(dp(16), dp(14), dp(16), dp(2));
        body.addView(label, new LinearLayout.LayoutParams(MATCH, WRAP));
        if ("ПОЛЁТ".equals(title) || "ПРЕВРАЩЕНИЕ БУКВ".equals(title)) {
            advHeads.add(label);
            label.setVisibility(advanced ? View.VISIBLE : View.GONE);
        }
    }

    // Строка из списка редких: прячется вместе со своей разделительной линией
    void markAdv(String key, View row) {
        if (!ADV_KEYS.contains("," + key + ",")) {
            return;
        }
        advRows.add(row);
        setRowShown(row, advanced);
    }

    void setRowShown(View row, boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        row.setVisibility(visibility);
        if (row.getParent() instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row.getParent();
            int at = group.indexOfChild(row);
            if (at > 0) {
                group.getChildAt(at - 1).setVisibility(visibility);
            }
        }
    }

    // Применяет «Расширенные» и «Подсказки» сразу, без перестроения экрана
    void applyDetail() {
        advanced = prefs().getBoolean("advanced", false);
        hints = prefs().getBoolean("hints", false);
        for (int i = 0; i < advRows.size(); i++) {
            setRowShown(advRows.get(i), advanced);
        }
        for (int i = 0; i < advHeads.size(); i++) {
            advHeads.get(i).setVisibility(advanced ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < hintViews.size(); i++) {
            hintViews.get(i).setVisibility(hints ? View.VISIBLE : View.GONE);
        }
    }

    void addRow(LinearLayout body, View row) {
        View divider = new View(host);
        divider.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(1) / 2));
        line.setMargins(dp(16), 0, dp(16), 0);
        body.addView(divider, line);
        body.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // Зависимые настройки приглушаются, когда главный переключатель выключен
    void link(String key, View... rows) {
        ArrayList<View> group = links.get(key);
        if (group == null) {
            group = new ArrayList<View>();
            links.put(key, group);
        }
        for (int i = 0; i < rows.length; i++) {
            group.add(rows[i]);
        }
        refreshLink(key);
    }

    void refreshLink(String key) {
        ArrayList<View> group = links.get(key);
        if (group == null) {
            return;
        }
        Boolean def = defaults.get(key);
        boolean on = prefs().getBoolean(key, def == null || def.booleanValue());
        for (int i = 0; i < group.size(); i++) {
            group.get(i).setAlpha(on ? 1.0f : 0.38f);
        }
    }

    View toggle(LinearLayout body, String key, String title, boolean def) {
        return toggle(body, key, title, null, def);
    }

    View toggle(LinearLayout body, final String key, String title, String desc, boolean def) {
        defaults.put(key, Boolean.valueOf(def));
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(12), dp(12));

        LinearLayout names = new LinearLayout(host);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(title, 15.5f, TEXT));
        if (desc != null) {
            TextView sub = text(desc, 12, MUTED);
            sub.setPadding(0, dp(2), 0, 0);
            sub.setVisibility(hints ? View.VISIBLE : View.GONE);
            hintViews.add(sub);
            names.addView(sub);
        }
        row.addView(names, params(0, WRAP, 0, 0, 12, 0));
        ((LinearLayout.LayoutParams) names.getLayoutParams()).weight = 1.0f;

        final SwitchView box = new SwitchView(host, accent, TRACK_OFF);
        box.setChecked(prefs().getBoolean(key, def), false);
        box.setListener(new SwitchView.Listener() {
            @Override
            public void onChanged(boolean checked) {
                prefs().edit().putBoolean(key, checked).apply();
                refreshLink(key);
                if ("advanced".equals(key) || "hints".equals(key)) {
                    applyDetail();
                    return;
                }
                if (key.startsWith("send_")) {
                    applySendChange();
                } else {
                    applyChange();
                }
            }
        });
        row.addView(box);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                box.toggle();
            }
        });
        addRow(body, row);
        markAdv(key, row);
        return row;
    }

    View slider(LinearLayout body, String key, String title,
                        int min, int max, int def, String unit) {
        return slider(body, key, title, null, min, max, def, unit, 1);
    }

    String shown(int value, int divisor) {
        return divisor == 1 ? String.valueOf(value) : String.valueOf(value / (float) divisor);
    }

    View slider(LinearLayout body, final String key, String title,
                        final int min, final int max, int def, final String unit, final int divisor) {
        return slider(body, key, title, null, min, max, def, unit, divisor);
    }

    View slider(LinearLayout body, final String key, String title, String desc,
                        final int min, final int max, int def, final String unit, final int divisor) {
        final String unitT = Strings.t(unit);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(6));

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(caption(title, desc), params(0, WRAP, 0, 0, 10, 0));
        ((LinearLayout.LayoutParams) top.getChildAt(0).getLayoutParams()).weight = 1.0f;
        final TextView value = text("", 13, TEXT);
        value.setTypeface(MEDIUM);
        value.setPadding(dp(10), dp(4), dp(10), dp(4));
        value.setBackground(rounded(tint(accent, 0x2E), 100));
        top.addView(value);
        row.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        int current = Math.max(min, Math.min(max, prefs().getInt(key, def)));
        value.setText(shown(current, divisor) + unitT);

        SliderView bar = new SliderView(host, accent, TRACK_OFF, min, max, def);
        bar.setInitial(current);
        bar.setListener(new SliderView.Listener() {
            @Override
            public void onChanged(int picked, boolean fromUser) {
                value.setText(shown(picked, divisor) + unitT);
                if (fromUser) {
                    prefs().edit().putInt(key, picked).apply();
                    main.reloadConfig();
                    if (key.startsWith("ca_") || key.startsWith("cb_")) {
                        refreshSwatches();
                    }
                }
            }

            @Override
            public void onTouch(boolean down) {
                float scale = down ? 1.12f : 1.0f;
                value.animate().scaleX(scale).scaleY(scale).setDuration(140L).start();
                if (!down) {
                    if (key.startsWith("send_")) {
                        applySendChange();
                    } else {
                        restartDemo();
                    }
                }
            }
        });
        row.addView(bar, params(MATCH, dp(40), 0, 2, 0, 0));
        addRow(body, row);
        markAdv(key, row);
        return row;
    }

    View choice(LinearLayout body, final String key, String title,
                        final String[] options, int def) {
        return choice(body, key, title, null, options, def);
    }

    // Название строки и (по желанию) пояснение мелким текстом под ним
    View caption(String title, String desc) {
        LinearLayout names = new LinearLayout(host);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(title, 15.5f, TEXT));
        if (desc != null) {
            TextView sub = text(desc, 12, MUTED);
            sub.setPadding(0, dp(2), 0, 0);
            sub.setVisibility(hints ? View.VISIBLE : View.GONE);
            hintViews.add(sub);
            names.addView(sub);
        }
        return names;
    }

    View choice(LinearLayout body, final String key, String title, String desc,
                        final String[] options, int def) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(14));
        row.addView(caption(title, desc), new LinearLayout.LayoutParams(MATCH, WRAP));

        Flow chips = new Flow(host, dp(8));
        final TextView[] views = new TextView[options.length];
        int selected = Math.max(0, Math.min(options.length - 1, prefs().getInt(key, def)));
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView chip = text(options[i], 13.5f, TEXT);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs().edit().putInt(key, index).apply();
                    paintChips(views, index);
                    if ("lang".equals(key)) {
                        relanguage();
                        return;
                    }
                    if ("style".equals(key)) {
                        updateSmokeRows();
                    }
                    if (key.startsWith("send_")) {
                        applySendChange();
                        return;
                    }
                    applyChange();
                }
            });
            views[i] = chip;
            chips.addView(chip);
        }
        paintChips(views, selected);
        row.addView(chips, params(MATCH, WRAP, 0, 10, 0, 0));
        addRow(body, row);
        markAdv(key, row);
        return row;
    }

    void paintChips(TextView[] views, int selected) {
        int ink = onAccent();
        for (int i = 0; i < views.length; i++) {
            boolean on = i == selected;
            views[i].setBackground(on ? pill(accent, 0, 100) : pill(PILL, STROKE, 100));
            views[i].setTextColor(on ? ink : TEXT);
            views[i].setTypeface(on ? MEDIUM : Typeface.DEFAULT);
        }
    }

    // Готовые эффекты появления: одним тапом выставляют сразу несколько ползунков,
    // дальше каждый можно подправить вручную. Остальные настройки (курсор, свечение, искры) не трогаются.
    void effects(LinearLayout body) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(14));
        row.addView(caption("Готовые эффекты",
            "Выбери основу — потом подстрой ползунки ниже под себя"), new LinearLayout.LayoutParams(MATCH, WRAP));
        Flow chips = new Flow(host, dp(8));
        for (int i = 0; i < EFFECT_NAMES.length; i++) {
            final int index = i;
            TextView chip = text(EFFECT_NAMES[i], 13.5f, TEXT);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(pill(PILL, STROKE, 100));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    applyEffect(index);
                }
            });
            chips.addView(chip);
        }
        row.addView(chips, params(MATCH, WRAP, 0, 10, 0, 0));
        body.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    void applyEffect(int index) {
        SharedPreferences.Editor e = prefs().edit();
        // Сначала нейтральная основа, чтобы настройки прошлого эффекта не примешивались
        e.putBoolean("slide", false).putInt("slide_dp", 8).putInt("slide_dir", 0);
        e.putBoolean("scale", false).putInt("scale_start", 80).putInt("pivot", 0);
        e.putInt("tilt", 0).putInt("tilt_dir", 0).putInt("squash", 0).putInt("shake", 0);
        e.putInt("stagger", 0).putInt("jitter", 0).putInt("fade_start", 25);
        e.putBoolean("blur", false).putInt("blur_dp", 6).putInt("blur_end", 80);
        switch (index) {
            case 0: // Мягкий выезд
                e.putBoolean("slide", true).putInt("slide_dp", 8);
                e.putBoolean("scale", true).putInt("scale_start", 80);
                e.putInt("duration", 260).putInt("easing", 0).putInt("fade_start", 20);
                e.putBoolean("blur", true).putInt("blur_dp", 4).putInt("blur_end", 70);
                break;
            case 1: // Из размытия
                e.putBoolean("scale", true).putInt("scale_start", 92);
                e.putInt("duration", 360).putInt("easing", 7).putInt("fade_start", 0);
                e.putBoolean("blur", true).putInt("blur_dp", 12).putInt("blur_end", 90);
                break;
            case 2: // Падение
                e.putBoolean("slide", true).putInt("slide_dp", 18).putInt("slide_dir", 0);
                e.putInt("duration", 320).putInt("easing", 5).putInt("squash", 25).putInt("fade_start", 40);
                e.putBoolean("blur", true).putInt("blur_dp", 3).putInt("blur_end", 40);
                break;
            case 3: // Пружина
                e.putBoolean("slide", true).putInt("slide_dp", 10).putInt("slide_dir", 1);
                e.putBoolean("scale", true).putInt("scale_start", 35).putInt("pivot", 1);
                e.putInt("duration", 320).putInt("easing", 4).putInt("squash", 20).putInt("fade_start", 20);
                e.putInt("tilt", 8).putInt("tilt_dir", 2);
                e.putBoolean("blur", true).putInt("blur_dp", 5).putInt("blur_end", 60);
                break;
            case 4: // Из увеличенного
                e.putBoolean("scale", true).putInt("scale_start", 180);
                e.putInt("duration", 300).putInt("easing", 6).putInt("fade_start", 0);
                e.putBoolean("blur", true).putInt("blur_dp", 8).putInt("blur_end", 85);
                break;
            case 5: // Волна
                e.putBoolean("slide", true).putInt("slide_dp", 12).putInt("slide_dir", 1);
                e.putBoolean("scale", true).putInt("scale_start", 70);
                e.putInt("duration", 340).putInt("easing", 3).putInt("fade_start", 10);
                e.putInt("stagger", 45).putInt("jitter", 20);
                e.putBoolean("blur", true).putInt("blur_dp", 4).putInt("blur_end", 70);
                break;
            default: // Резко
                e.putInt("duration", 120).putInt("easing", 1).putInt("fade_start", 100);
                break;
        }
        e.apply();
        buildSettings();
        applyChange();
    }

    // Переносит чипы на новые строки вместо горизонтальной прокрутки
    public static final class Flow extends ViewGroup {
        final int gap;

        public Flow(Context context, int gap) {
            super(context);
            this.gap = gap;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = child.getMeasuredWidth();
                int h = child.getMeasuredHeight();
                if (x > 0 && x + w > available) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                x += w + gap;
                rowHeight = Math.max(rowHeight, h);
            }
            int height = y + rowHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int available = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                int w = child.getMeasuredWidth();
                int h = child.getMeasuredHeight();
                if (x > 0 && x + w > available) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                int left = getPaddingLeft() + x;
                int top = getPaddingTop() + y;
                child.layout(left, top, left + w, top + h);
                x += w + gap;
                rowHeight = Math.max(rowHeight, h);
            }
        }
    }

    void applyChange() {
        main.reloadConfig();
        updateSendRows();
        restartDemo();
    }

    // ---------------------------------------------------------------
    // Мелочи
    // ---------------------------------------------------------------
    SharedPreferences prefs() {
        return main.ttcuz().prefs();
    }

    int dp(float value) {
        return main.ttcuz().dp(value);
    }

    // Текст с запоминанием русского ключа: при смене языка его можно перевести заново
    void setTr(TextView view, String ru) {
        view.setTag(ru);
        view.setText(Strings.t(ru));
    }

    void retranslate(View view) {
        if (view instanceof TextView && view.getTag() instanceof String) {
            ((TextView) view).setText(Strings.t((String) view.getTag()));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                retranslate(group.getChildAt(i));
            }
        }
    }

    // Настройки дыма видны только когда выбран стиль «Дым»
    void updateSmokeRows() {
        boolean on = prefs().getInt("style", Config.STYLE_DUST) == Config.STYLE_SMOKE;
        for (int i = 0; i < smokeRows.size(); i++) {
            View row = smokeRows.get(i);
            if (!(row.getParent() instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) row.getParent();
            int at = group.indexOfChild(row);
            row.setVisibility(on ? View.VISIBLE : View.GONE);
            if (at > 0) {
                group.getChildAt(at - 1).setVisibility(on ? View.VISIBLE : View.GONE);
            }
        }
    }

    void relanguage() {
        Strings.setMode(prefs().getInt("lang", 0));
        retranslate(root);
        buildSettings();
        restartDemo();
    }

    String sample() {
        return Strings.t(SAMPLE);
    }

    TextView text(String value, float sp, int color) {
        TextView view = new TextView(host);
        if (value != null && value.length() > 0) {
            view.setTag(value);
        }
        view.setText(Strings.t(value));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    // Цветной квадрат со своей иконкой внутри
    View badge(int icon) {
        FrameLayout box = new FrameLayout(host);
        box.setBackground(rounded(tint(accent, 0x2E), 12));
        box.addView(new IconView(host, icon, accent), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        return box;
    }

    int tint(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    // Тёмный или белый текст поверх цвета акцента
    int onAccent() {
        double luma = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255.0;
        return luma > 0.6 ? 0xFF1B1A1F : 0xFFFFFFFF;
    }

    GradientDrawable pill(int fill, int strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(Math.max(1, dp(1)), strokeColor);
        }
        return drawable;
    }

    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(width, height);
        result.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return result;
    }

    int systemBar(String name) {
        int id = host.getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? host.getResources().getDimensionPixelSize(id) : 0;
    }

    // Если слой уже лежит ниже строки состояния, лишний отступ не нужен
    void fitSystemBars() {
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        int top = Math.max(0, systemBar("status_bar_height") - location[1]);
        int screenHeight = host.getResources().getDisplayMetrics().heightPixels;
        int gap = screenHeight - (location[1] + root.getHeight());
        int bottom = Math.max(0, systemBar("navigation_bar_height") - gap);
        page.setPadding(0, top, 0, 0);
        list.setPadding(dp(12), dp(4), dp(12), bottom + dp(24));
    }
}
