package it.successoplayer.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

public final class SplashActivity extends Activity {
    private static final long SPLASH_MS = 1800L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean opened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        View root = findViewById(android.R.id.content);
        if (root != null) root.setOnClickListener(v -> openMain());
        handler.postDelayed(this::openMain, SPLASH_MS);
    }

    private void openMain() {
        if (opened) return;
        opened = true;
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
