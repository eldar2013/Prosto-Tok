package cat.narezany.margyt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Where MargyCode asks to be let in. Shows who's asking and what it means; a no sends nothing back. */
public final class DevLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        if (!DevLink.genuine(this, getCallingPackage())) {
            Diary.note("devlink: refused " + getCallingPackage());
            finish();
            return;
        }
        Popup.ask(this, Local.of("Подключить MargyCode?"),
                Local.of("Агент MargyCode сможет ставить плагины в TikTok, смотреть экраны, нажимать и читать журнал, чтобы писать плагины за тебя.\n\nПлагин работает внутри TikTok со всеми его правами, в том числе с твоим аккаунтом, поэтому подключай, только если доверяешь модели, с которой работаешь. Отключить можно в настройках мода, в разделе «Плагины»."),
                Local.of("Подключить"), () -> {
                    try {
                        Object[] link = DevLink.pair(this);
                        setResult(RESULT_OK, new Intent().putExtra("port", (Integer) link[0]).putExtra("token", (String) link[1]));
                    } catch (Throwable error) {
                        Diary.note("devlink: " + error);
                    }
                    finish();
                },
                Text.CANCEL, this::finish,
                null, null);
        // swiped away or tapped past: without this an empty see-through screen would stay in the way
        Popup.afterLast(this::finish);
    }
}
