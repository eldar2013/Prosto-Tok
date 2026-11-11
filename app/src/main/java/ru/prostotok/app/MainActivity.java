package ru.prostotok.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView hint;
    private final ActivityResultLauncher<Intent> picker =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) play(uri);
            }
        });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF080808);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(28, 28, 28, 28);
        hint = new TextView(this);
        hint.setText("PROSTO TOK\n\nВыбери видео из галереи, чтобы начать просмотр");
        hint.setTextColor(0xFFFFD600);
        hint.setTextSize(20);
        hint.setGravity(Gravity.CENTER);
        overlay.addView(hint);
        Button choose = new Button(this);
        choose.setText("ОТКРЫТЬ ВИДЕО");
        choose.setOnClickListener(v -> openPicker());
        overlay.addView(choose);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        picker.launch(intent);
    }

    private void play(Uri uri) {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
        }
        hint.setVisibility(View.GONE);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (player != null) { player.release(); player = null; }
    }
}
