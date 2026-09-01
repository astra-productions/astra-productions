package de.astra.pulse;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes3.dex */
public class OnlineVideoActivity extends Activity {
    public static final String EXTRA_CONFIG = "astra_video_config";
    private static final String OVERLAY_PREFS = "astra_video_overlay";
    private ExoPlayer localPlayer;
    private PlayerView localPlayerView;
    private Button playButton;
    private FrameLayout rootFrame;
    private LinearLayout controlPanel;
    private Button toolbarToggle;
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout.LayoutParams timerLayout;
    private OverlayTimerView timerView;
    private WebView webView;
    private boolean timerOnRight = true;

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        View mediaSurface;
        super.onCreate(savedInstanceState);
        getWindow().addFlags(128);
        try {
            JSONObject config = new JSONObject(getIntent().getStringExtra(EXTRA_CONFIG));
            String targetUrl = config.optString("url", "");
            boolean localSource = targetUrl.startsWith("content://");
            if (!localSource && !targetUrl.startsWith("https://")) {
                throw new IllegalArgumentException("Ungueltige URL");
            }
            this.timerOnRight = !"left".equals(config.optString("position", "right"));
            this.rootFrame = new FrameLayout(this);
            this.rootFrame.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
            if (localSource) {
                this.localPlayerView = createLocalVideo(Uri.parse(targetUrl));
                mediaSurface = this.localPlayerView;
            } else {
                this.webView = createWebView();
                mediaSurface = this.webView;
            }
            this.rootFrame.addView(mediaSurface, new FrameLayout.LayoutParams(-1, -1));
            this.timerView = new OverlayTimerView(this, config);
            SharedPreferences overlayPrefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
            if (overlayPrefs.contains("metronome_volume")) {
                this.timerView.setMetronomeVolume(overlayPrefs.getInt("metronome_volume", 60));
            }
            setVideoVolume(overlayPrefs.getInt("video_volume", 100));
            int timerSize = dp(108);
            this.timerLayout = new FrameLayout.LayoutParams(timerSize, timerSize);
            this.timerLayout.bottomMargin = dp(24);
            updateTimerPosition();
            this.rootFrame.addView(this.timerView, this.timerLayout);
            this.controlPanel = new LinearLayout(this);
            this.controlPanel.setOrientation(0);
            this.controlPanel.setGravity(17);
            this.controlPanel.setPadding(dp(4), dp(4), dp(4), dp(4));
            Button close = controlButton("X", "Videomodus schliessen");
            this.playButton = controlButton("▶", "Timer starten oder pausieren");
            Button reset = controlButton("↺", "Timer zuruecksetzen");
            Button side = controlButton("↔", "Timer-Seite wechseln");
            this.controlPanel.addView(close);
            this.controlPanel.addView(this.playButton);
            this.controlPanel.addView(reset);
            this.controlPanel.addView(side);
            FrameLayout.LayoutParams controlsLayout = new FrameLayout.LayoutParams(-2, -2, 8388661);
            controlsLayout.topMargin = dp(10);
            controlsLayout.rightMargin = dp(62);
            this.rootFrame.addView(this.controlPanel, controlsLayout);
            this.toolbarToggle = controlButton("⌃", "Astra-Steuerleiste einklappen");
            FrameLayout.LayoutParams toggleLayout = new FrameLayout.LayoutParams(dp(46), dp(46), 8388661);
            toggleLayout.topMargin = dp(14);
            toggleLayout.rightMargin = dp(10);
            this.rootFrame.addView(this.toolbarToggle, toggleLayout);
            this.toolbarToggle.setOnClickListener(view -> toggleControlPanel());
            close.setOnClickListener(view -> finish());
            this.playButton.setOnClickListener(view -> this.timerView.toggle());
            reset.setOnClickListener(view -> this.timerView.resetTimer());
            side.setOnClickListener(view -> {
                this.timerOnRight = !this.timerOnRight;
                getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().clear().apply();
                updateTimerPosition();
                this.timerView.setLayoutParams(this.timerLayout);
                this.rootFrame.post(() -> placeTimerInCorner());
            });
            attachDraggableTimer();
            this.timerView.setStateListener(running -> this.playButton.setText(running ? "Ⅱ" : "▶"));
            setContentView(this.rootFrame);
            this.rootFrame.post(this::restoreTimerPosition);
            if (this.webView != null) {
                this.webView.loadUrl(targetUrl);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Der Online-Videomodus konnte nicht gestartet werden.", 1).show();
            finish();
        }
    }

    /* JADX INFO: renamed from: lambda$onCreate$0$de-astra-pulse-OnlineVideoActivity, reason: not valid java name */
    /* synthetic */ void m45lambda$onCreate$0$deastrapulseOnlineVideoActivity(View view) {
        finish();
    }

    /* JADX INFO: renamed from: lambda$onCreate$1$de-astra-pulse-OnlineVideoActivity, reason: not valid java name */
    /* synthetic */ void m46lambda$onCreate$1$deastrapulseOnlineVideoActivity(View view) {
        this.timerView.toggle();
    }

    /* JADX INFO: renamed from: lambda$onCreate$2$de-astra-pulse-OnlineVideoActivity, reason: not valid java name */
    /* synthetic */ void m47lambda$onCreate$2$deastrapulseOnlineVideoActivity(View view) {
        this.timerView.resetTimer();
    }

    /* JADX INFO: renamed from: lambda$onCreate$3$de-astra-pulse-OnlineVideoActivity, reason: not valid java name */
    /* synthetic */ void m48lambda$onCreate$3$deastrapulseOnlineVideoActivity(View view) {
        this.timerOnRight = !this.timerOnRight;
        updateTimerPosition();
        this.timerView.setLayoutParams(this.timerLayout);
    }

    /* JADX INFO: renamed from: lambda$onCreate$4$de-astra-pulse-OnlineVideoActivity, reason: not valid java name */
    /* synthetic */ void m49lambda$onCreate$4$deastrapulseOnlineVideoActivity(View view) {
        this.timerView.toggle();
    }

    /* JADX INFO: renamed from: lambda$onCreate$5$de-astra-pulse-OnlineVideoActivity, reason: not valid java name */
    /* synthetic */ void m50lambda$onCreate$5$deastrapulseOnlineVideoActivity(boolean running) {
        this.playButton.setText(running ? "Ⅱ" : "▶");
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        view.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
        view.addJavascriptInterface(new PlaybackBridge(), "AstraVideoPlayback");
        view.setWebViewClient(new WebViewClient() { // from class: de.astra.pulse.OnlineVideoActivity.2
            @Override // android.webkit.WebViewClient
            public void onPageFinished(WebView currentView, String url) {
                super.onPageFinished(currentView, url);
                OnlineVideoActivity.this.injectVideoPlaybackListener(currentView);
                OnlineVideoActivity.this.setVideoVolume(
                        getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).getInt("video_volume", 100)
                );
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View customView, CustomViewCallback callback) {
                showFullscreenVideo(customView, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenVideo();
            }
        });
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        return view;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void injectVideoPlaybackListener(WebView view) {
        view.evaluateJavascript("(function(){if(window.__astraPulseVideoWatch)return;window.__astraPulseVideoWatch=true;function bind(v){if(v.__astraBound)return;v.__astraBound=true;v.addEventListener('play',function(){AstraVideoPlayback.onPlay();});v.addEventListener('pause',function(){AstraVideoPlayback.onPause();});v.addEventListener('ended',function(){AstraVideoPlayback.onPause();});}function scan(){document.querySelectorAll('video').forEach(bind);}scan();new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});})();", null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    class PlaybackBridge {
        private PlaybackBridge() {
        }

        @JavascriptInterface
        public void onPlay() {
            OnlineVideoActivity.this.runOnUiThread(() -> {
                if (OnlineVideoActivity.this.timerView != null) {
                    OnlineVideoActivity.this.timerView.startTimer();
                }
            });
        }

        /* JADX INFO: renamed from: lambda$onPlay$0$de-astra-pulse-OnlineVideoActivity$PlaybackBridge, reason: not valid java name */
        /* synthetic */ void m52x202bfa5() {
            if (OnlineVideoActivity.this.timerView != null) {
                OnlineVideoActivity.this.timerView.startTimer();
            }
        }

        @JavascriptInterface
        public void onPause() {
            OnlineVideoActivity.this.runOnUiThread(() -> {
                if (OnlineVideoActivity.this.timerView != null) {
                    OnlineVideoActivity.this.timerView.pauseTimer();
                }
            });
        }

        /* JADX INFO: renamed from: lambda$onPause$0$de-astra-pulse-OnlineVideoActivity$PlaybackBridge, reason: not valid java name */
        /* synthetic */ void m51xbd546cef() {
            if (OnlineVideoActivity.this.timerView != null) {
                OnlineVideoActivity.this.timerView.pauseTimer();
            }
        }
    }

    private PlayerView createLocalVideo(Uri uri) {
        PlayerView view = new PlayerView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setUseController(true);
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        this.localPlayer = new ExoPlayer.Builder(this).build();
        view.setPlayer(this.localPlayer);
        this.localPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (OnlineVideoActivity.this.timerView == null) return;
                if (isPlaying) {
                    OnlineVideoActivity.this.timerView.startTimer();
                } else {
                    OnlineVideoActivity.this.timerView.pauseTimer();
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Toast.makeText(OnlineVideoActivity.this,
                        "Dieses Video konnte nicht abgespielt werden: " + error.getErrorCodeName(),
                        Toast.LENGTH_LONG).show();
            }
        });
        this.localPlayer.setMediaItem(MediaItem.fromUri(uri));
        this.localPlayer.setPlayWhenReady(false);
        this.localPlayer.prepare();
        return view;
    }

    private void showFullscreenVideo(View view, WebChromeClient.CustomViewCallback callback) {
        if (this.customVideoView != null || this.rootFrame == null) {
            callback.onCustomViewHidden();
            return;
        }
        this.customVideoView = view;
        this.customViewCallback = callback;
        this.rootFrame.addView(view, new FrameLayout.LayoutParams(-1, -1));
        this.timerView.bringToFront();
        this.controlPanel.bringToFront();
        this.toolbarToggle.bringToFront();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void hideFullscreenVideo() {
        if (this.customVideoView == null) return;
        this.rootFrame.removeView(this.customVideoView);
        this.customVideoView = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (this.customViewCallback != null) {
            this.customViewCallback.onCustomViewHidden();
            this.customViewCallback = null;
        }
    }

    private void toggleControlPanel() {
        boolean open = this.controlPanel.getVisibility() == View.VISIBLE;
        this.controlPanel.setVisibility(open ? View.GONE : View.VISIBLE);
        this.toolbarToggle.setText(open ? "⌄" : "⌃");
        this.toolbarToggle.setContentDescription(open
                ? "Astra-Steuerleiste ausklappen"
                : "Astra-Steuerleiste einklappen");
    }

    @Override
    public void onBackPressed() {
        if (this.customVideoView != null) {
            hideFullscreenVideo();
            return;
        }
        if (this.webView != null && this.webView.canGoBack()) {
            this.webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private Button controlButton(String text, String description) {
        Button button = new Button(this);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(dp(46), dp(46));
        layout.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(layout);
        button.setText(text);
        button.setTextColor(-1);
        button.setTextSize(17.0f);
        button.setContentDescription(description);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundResource(R.drawable.video_control_button);
        return button;
    }

    private void updateTimerPosition() {
        this.timerLayout.gravity = (this.timerOnRight ? GravityCompat.END : GravityCompat.START) | 80;
        this.timerLayout.leftMargin = dp(16);
        this.timerLayout.rightMargin = dp(16);
    }

    private void attachDraggableTimer() {
        this.timerView.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private float startX;
            private float startY;
            private boolean dragged;
            private boolean longPressed;
            private final Runnable openVolume = () -> {
                if (!dragged) {
                    longPressed = true;
                    showVolumeDialog();
                }
            };

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = view.getX();
                        startY = view.getY();
                        dragged = false;
                        longPressed = false;
                        view.postDelayed(openVolume, 550L);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (Math.hypot(dx, dy) > dp(5)) {
                            dragged = true;
                            view.removeCallbacks(openVolume);
                        }
                        float maxX = Math.max(0, rootFrame.getWidth() - view.getWidth());
                        float maxY = Math.max(0, rootFrame.getHeight() - view.getHeight());
                        view.setX(Math.max(0, Math.min(maxX, startX + dx)));
                        view.setY(Math.max(0, Math.min(maxY, startY + dy)));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.removeCallbacks(openVolume);
                        if (dragged) {
                            saveTimerPosition();
                        } else if (!longPressed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                            view.performClick();
                            timerView.toggle();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void showVolumeDialog() {
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(10), dp(24), dp(6));

        TextView videoLabel = volumeLabel("Video", prefs.getInt("video_volume", 100));
        SeekBar videoVolume = volumeSlider(prefs.getInt("video_volume", 100));
        TextView metronomeLabel = volumeLabel("Metronom", prefs.getInt("metronome_volume", this.timerView.getMetronomeVolume()));
        SeekBar metronomeVolume = volumeSlider(prefs.getInt("metronome_volume", this.timerView.getMetronomeVolume()));
        content.addView(videoLabel);
        content.addView(videoVolume);
        content.addView(metronomeLabel);
        content.addView(metronomeVolume);

        videoVolume.setOnSeekBarChangeListener(volumeListener(value -> {
            videoLabel.setText("Video · " + value + "%");
            prefs.edit().putInt("video_volume", value).apply();
            setVideoVolume(value);
        }));
        metronomeVolume.setOnSeekBarChangeListener(volumeListener(value -> {
            metronomeLabel.setText("Metronom · " + value + "%");
            prefs.edit().putInt("metronome_volume", value).apply();
            this.timerView.setMetronomeVolume(value);
        }));

        new AlertDialog.Builder(this)
                .setTitle("Lautstärke")
                .setView(content)
                .setPositiveButton("FERTIG", null)
                .show();
    }

    private TextView volumeLabel(String name, int value) {
        TextView label = new TextView(this);
        label.setText(name + " · " + value + "%");
        label.setTextColor(Color.WHITE);
        label.setTextSize(15f);
        label.setPadding(0, dp(12), 0, 0);
        return label;
    }

    private SeekBar volumeSlider(int value) {
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(Math.max(0, Math.min(100, value)));
        return slider;
    }

    private interface VolumeChange { void onChange(int value); }

    private SeekBar.OnSeekBarChangeListener volumeListener(VolumeChange change) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) change.onChange(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void setVideoVolume(int value) {
        float volume = Math.max(0, Math.min(100, value)) / 100f;
        if (this.localPlayer != null) this.localPlayer.setVolume(volume);
        if (this.webView != null) {
            this.webView.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.volume=" + volume + ";});", null);
        }
    }

    private void saveTimerPosition() {
        float maxX = Math.max(1, this.rootFrame.getWidth() - this.timerView.getWidth());
        float maxY = Math.max(1, this.rootFrame.getHeight() - this.timerView.getHeight());
        getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit()
                .putFloat("x", this.timerView.getX() / maxX)
                .putFloat("y", this.timerView.getY() / maxY)
                .apply();
    }

    private void restoreTimerPosition() {
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        if (!prefs.contains("x") || !prefs.contains("y")) {
            placeTimerInCorner();
            return;
        }
        float maxX = Math.max(0, this.rootFrame.getWidth() - this.timerView.getWidth());
        float maxY = Math.max(0, this.rootFrame.getHeight() - this.timerView.getHeight());
        this.timerView.setX(maxX * prefs.getFloat("x", 1f));
        this.timerView.setY(maxY * prefs.getFloat("y", 1f));
    }

    private void placeTimerInCorner() {
        float x = this.timerOnRight
                ? this.rootFrame.getWidth() - this.timerView.getWidth() - dp(16)
                : dp(16);
        float y = this.rootFrame.getHeight() - this.timerView.getHeight() - dp(24);
        this.timerView.setX(Math.max(0, x));
        this.timerView.setY(Math.max(0, y));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override // android.app.Activity
    protected void onPause() {
        if (this.timerView != null) {
            this.timerView.pauseTimer();
        }
        if (this.webView != null) {
            this.webView.onPause();
        }
        if (this.localPlayer != null && this.localPlayer.isPlaying()) {
            this.localPlayer.pause();
        }
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        if (this.webView != null) {
            this.webView.onResume();
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        hideFullscreenVideo();
        if (this.timerView != null) {
            this.timerView.release();
        }
        if (this.webView != null) {
            this.webView.stopLoading();
            this.webView.destroy();
        }
        if (this.localPlayerView != null) {
            this.localPlayerView.setPlayer(null);
        }
        if (this.localPlayer != null) {
            this.localPlayer.release();
            this.localPlayer = null;
        }
        super.onDestroy();
    }

    private static class Segment {
        final String name;
        final int seconds;

        Segment(String name, int seconds) {
            this.name = name;
            this.seconds = Math.max(1, seconds);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    static class OverlayTimerView extends View {
        private final Runnable beat;
        private boolean finished;
        private final Matrix gradientMatrix;
        private final Handler handler;
        private long lastTick;
        private final boolean metronomeEnabled;
        private final List<Segment> modes;
        private final Paint paint;
        private final Set<Integer> pauseAfterRounds;
        private final int pauseEvery;
        private long remainingMs;
        private boolean rest;
        private final int restSeconds;
        private final RectF ring;
        private int roundIndex;
        private boolean running;
        private StateListener stateListener;
        private final Runnable ticker;
        private ToneGenerator tone;
        private int metronomeVolume;
        private long totalMs;
        private final boolean usePresetPauses;

        interface StateListener {
            void onRunningChanged(boolean z);
        }

        static /* synthetic */ long access$722(OverlayTimerView x0, long x1) {
            long j = x0.remainingMs - x1;
            x0.remainingMs = j;
            return j;
        }

        OverlayTimerView(Context context, JSONObject config) {
            super(context);
            this.paint = new Paint(1);
            this.ring = new RectF();
            this.gradientMatrix = new Matrix();
            this.handler = new Handler(Looper.getMainLooper());
            this.modes = new ArrayList();
            this.pauseAfterRounds = new HashSet();
            this.roundIndex = 0;
            this.rest = false;
            this.running = false;
            this.finished = false;
            this.ticker = new Runnable() { // from class: de.astra.pulse.OnlineVideoActivity.OverlayTimerView.1
                @Override // java.lang.Runnable
                public void run() {
                    if (OverlayTimerView.this.running) {
                        long now = SystemClock.elapsedRealtime();
                        OverlayTimerView.access$722(OverlayTimerView.this, Math.max(0L, now - OverlayTimerView.this.lastTick));
                        OverlayTimerView.this.lastTick = now;
                        if (OverlayTimerView.this.remainingMs <= 0) {
                            OverlayTimerView.this.advancePhase();
                        }
                        OverlayTimerView.this.invalidate();
                        if (OverlayTimerView.this.running) {
                            OverlayTimerView.this.handler.postDelayed(this, 33L);
                        }
                    }
                }
            };
            this.beat = new Runnable() { // from class: de.astra.pulse.OnlineVideoActivity.OverlayTimerView.2
                @Override // java.lang.Runnable
                public void run() {
                    if (OverlayTimerView.this.running && !OverlayTimerView.this.rest && !OverlayTimerView.this.finished && OverlayTimerView.this.metronomeEnabled) {
                        OverlayTimerView.this.tone.startTone(24, 45);
                        OverlayTimerView.this.handler.postDelayed(this, Math.max(250, 60000 / OverlayTimerView.bpmFor(OverlayTimerView.this.currentName())));
                    }
                }
            };
            setClickable(true);
            setFocusable(true);
            setContentDescription("Schwebender Astra-Pulse-Timer");
            JSONArray sourceModes = config.optJSONArray("modes");
            if (sourceModes != null) {
                for (int index = 0; index < sourceModes.length(); index++) {
                    JSONObject source = sourceModes.optJSONObject(index);
                    if (source != null) {
                        this.modes.add(new Segment(source.optString("name", "Normal"), source.optInt("seconds", 1)));
                    }
                }
            }
            if (this.modes.isEmpty()) {
                this.modes.add(new Segment("Normal", 1200));
            }
            JSONArray presetPauses = config.optJSONArray("pauseAfterRounds");
            this.usePresetPauses = presetPauses != null;
            if (presetPauses != null) {
                for (int index2 = 0; index2 < presetPauses.length(); index2++) {
                    this.pauseAfterRounds.add(Integer.valueOf(presetPauses.optInt(index2)));
                }
            }
            this.pauseEvery = Math.max(1, config.optInt("pauseEvery", 1));
            this.restSeconds = Math.max(1, config.optInt("restSeconds", 20));
            this.metronomeEnabled = config.optBoolean("metronomeEnabled", false);
            this.metronomeVolume = Math.max(0, Math.min(100, config.optInt("metronomeVolume", 60)));
            this.tone = new ToneGenerator(3, this.metronomeVolume);
            resetTimer();
        }

        void setStateListener(StateListener listener) {
            this.stateListener = listener;
            notifyState();
        }

        int getMetronomeVolume() {
            return this.metronomeVolume;
        }

        void setMetronomeVolume(int volume) {
            this.metronomeVolume = Math.max(0, Math.min(100, volume));
            this.tone.release();
            this.tone = new ToneGenerator(3, this.metronomeVolume);
            if (this.running) restartBeat();
        }

        void toggle() {
            if (!this.running) {
                startTimer();
            } else {
                pauseTimer();
            }
        }

        void startTimer() {
            if (this.finished) {
                resetTimer();
            }
            if (this.running) {
                return;
            }
            this.running = true;
            this.lastTick = SystemClock.elapsedRealtime();
            this.handler.post(this.ticker);
            restartBeat();
            notifyState();
            invalidate();
        }

        void pauseTimer() {
            this.running = false;
            this.handler.removeCallbacks(this.ticker);
            this.handler.removeCallbacks(this.beat);
            notifyState();
            invalidate();
        }

        void resetTimer() {
            pauseTimer();
            this.roundIndex = 0;
            this.rest = false;
            this.finished = false;
            setDuration(this.modes.get(0).seconds);
            invalidate();
        }

        void release() {
            pauseTimer();
            this.tone.release();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void advancePhase() {
            if (!this.rest) {
                if (this.roundIndex >= this.modes.size() - 1) {
                    this.finished = true;
                    pauseTimer();
                    this.remainingMs = 0L;
                    return;
                } else if (shouldPauseAfter(this.roundIndex + 1)) {
                    this.rest = true;
                    setDuration(this.restSeconds);
                } else {
                    this.roundIndex++;
                    setDuration(this.modes.get(this.roundIndex).seconds);
                }
            } else {
                this.rest = false;
                this.roundIndex++;
                setDuration(this.modes.get(this.roundIndex).seconds);
            }
            restartBeat();
        }

        private boolean shouldPauseAfter(int completedRound) {
            if (this.usePresetPauses) {
                return this.pauseAfterRounds.contains(Integer.valueOf(completedRound));
            }
            return completedRound % this.pauseEvery == 0;
        }

        private void setDuration(int seconds) {
            this.totalMs = ((long) Math.max(1, seconds)) * 1000;
            this.remainingMs = this.totalMs;
        }

        private void restartBeat() {
            this.handler.removeCallbacks(this.beat);
            if (!this.running || this.rest || this.finished || !this.metronomeEnabled) {
                return;
            }
            this.handler.post(this.beat);
        }

        private void notifyState() {
            if (this.stateListener != null) {
                this.stateListener.onRunningChanged(this.running);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public String currentName() {
            return this.finished ? "Fertig" : this.rest ? "Pause" : this.modes.get(Math.min(this.roundIndex, this.modes.size() - 1)).name;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static int bpmFor(String name) {
            String key = name == null ? "" : name.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
            if (key.matches(".*ultra\\s*speed.*")) {
                return 240;
            }
            if (key.matches(".*super\\s*(speed|speen).*")) {
                return 210;
            }
            if (key.contains("super fast")) {
                return 160;
            }
            if (key.contains("edge")) {
                return 190;
            }
            if (key.contains("speed") || key.contains("speen")) {
                return 180;
            }
            if (key.contains("faster")) {
                return 130;
            }
            if (key.contains("fast") || key.contains("schnell")) {
                return 100;
            }
            if (key.contains("slow") || key.contains("langsam")) {
                return 50;
            }
            return key.contains("normal") ? 70 : 60;
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float center = width / 2.0f;
            float padding = width * 0.09f;
            float stroke = width * 0.065f;
            this.ring.set(padding, padding, width - padding, width - padding);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setShader(null);
            this.paint.setColor(Color.argb(220, 8, 5, 22));
            canvas.drawCircle(center, center, center - 2.0f, this.paint);
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(stroke);
            this.paint.setStrokeCap(Paint.Cap.ROUND);
            this.paint.setColor(Color.argb(120, 120, 82, 180));
            canvas.drawArc(this.ring, -90.0f, 360.0f, false, this.paint);
            float progress = this.totalMs > 0 ? Math.max(0.0f, Math.min(1.0f, this.remainingMs / this.totalMs)) : 0.0f;
            SweepGradient gradient = new SweepGradient(center, center, new int[]{Color.rgb(255, 83, 211), Color.rgb(139, 103, 255), Color.rgb(105, 231, 255), Color.rgb(255, 83, 211)}, (float[]) null);
            this.gradientMatrix.setRotate(-90.0f, center, center);
            gradient.setLocalMatrix(this.gradientMatrix);
            this.paint.setShader(gradient);
            canvas.drawArc(this.ring, -90.0f, progress * 360.0f, false, this.paint);
            this.paint.setShader(null);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setTextAlign(Paint.Align.CENTER);
            this.paint.setTypeface(Typeface.DEFAULT_BOLD);
            this.paint.setColor(-1);
            this.paint.setTextSize(0.145f * width);
            String name = currentName().toUpperCase(Locale.ROOT);
            if (name.length() > 12) {
                this.paint.setTextSize(0.115f * width);
            }
            canvas.drawText(name, center, center - (0.015f * width), this.paint);
            long seconds = Math.max(0L, (this.remainingMs + 999) / 1000);
            String time = String.format(Locale.ROOT, "%02d:%02d", Long.valueOf(seconds / 60), Long.valueOf(seconds % 60));
            this.paint.setColor(Color.rgb(255, 143, 218));
            this.paint.setTextSize(0.12f * width);
            canvas.drawText(time, center, (0.18f * width) + center, this.paint);
        }
    }
}
