package de.astra.pulse;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;
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
    private VideoView localVideo;
    private Button playButton;
    private FrameLayout.LayoutParams timerLayout;
    private OverlayTimerView timerView;
    private WebView webView;
    private boolean timerOnRight = true;
    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
    private boolean localVideoWasPlaying = false;
    private final Runnable localPlaybackWatcher = new Runnable() { // from class: de.astra.pulse.OnlineVideoActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (OnlineVideoActivity.this.localVideo != null && OnlineVideoActivity.this.timerView != null) {
                boolean playing = OnlineVideoActivity.this.localVideo.isPlaying();
                if (playing && !OnlineVideoActivity.this.localVideoWasPlaying) {
                    OnlineVideoActivity.this.timerView.startTimer();
                } else if (!playing && OnlineVideoActivity.this.localVideoWasPlaying) {
                    OnlineVideoActivity.this.timerView.pauseTimer();
                }
                OnlineVideoActivity.this.localVideoWasPlaying = playing;
                OnlineVideoActivity.this.playbackHandler.postDelayed(this, 250L);
            }
        }
    };

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
            FrameLayout frameLayout = new FrameLayout(this);
            frameLayout.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
            if (localSource) {
                this.localVideo = createLocalVideo(Uri.parse(targetUrl));
                mediaSurface = this.localVideo;
            } else {
                this.webView = createWebView();
                mediaSurface = this.webView;
            }
            frameLayout.addView(mediaSurface, new FrameLayout.LayoutParams(-1, -1));
            this.timerView = new OverlayTimerView(this, config);
            int timerSize = dp(138);
            this.timerLayout = new FrameLayout.LayoutParams(timerSize, timerSize);
            this.timerLayout.bottomMargin = dp(24);
            updateTimerPosition();
            frameLayout.addView(this.timerView, this.timerLayout);
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(0);
            controls.setGravity(17);
            controls.setPadding(dp(4), dp(4), dp(4), dp(4));
            Button close = controlButton("X", "Videomodus schliessen");
            this.playButton = controlButton("▶", "Timer starten oder pausieren");
            Button reset = controlButton("↺", "Timer zuruecksetzen");
            Button side = controlButton("↔", "Timer-Seite wechseln");
            controls.addView(close);
            controls.addView(this.playButton);
            controls.addView(reset);
            controls.addView(side);
            FrameLayout.LayoutParams controlsLayout = new FrameLayout.LayoutParams(-2, -2, 8388661);
            controlsLayout.topMargin = dp(10);
            controlsLayout.rightMargin = dp(10);
            frameLayout.addView(controls, controlsLayout);
            close.setOnClickListener(view -> finish());
            this.playButton.setOnClickListener(view -> this.timerView.toggle());
            reset.setOnClickListener(view -> this.timerView.resetTimer());
            side.setOnClickListener(view -> {
                this.timerOnRight = !this.timerOnRight;
                updateTimerPosition();
                this.timerView.setLayoutParams(this.timerLayout);
            });
            this.timerView.setOnClickListener(view -> this.timerView.toggle());
            this.timerView.setStateListener(running -> this.playButton.setText(running ? "Ⅱ" : "▶"));
            setContentView(frameLayout);
            if (this.webView != null) {
                this.webView.loadUrl(targetUrl);
            }
            if (this.localVideo != null) {
                this.playbackHandler.post(this.localPlaybackWatcher);
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
            }
        });
        view.setWebChromeClient(new WebChromeClient());
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

    private VideoView createLocalVideo(Uri uri) {
        final VideoView view = new VideoView(this);
        view.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
        MediaController controls = new MediaController(this);
        controls.setAnchorView(view);
        view.setMediaController(controls);
        view.setVideoURI(uri);
        view.setOnPreparedListener(player -> {
            player.setLooping(false);
            view.seekTo(1);
            controls.show(0);
        });
        view.setOnErrorListener((player, what, extra) -> {
            Toast.makeText(this, "Dieses Videoformat konnte nicht abgespielt werden.", Toast.LENGTH_LONG).show();
            return true;
        });
        return view;
    }

    static /* synthetic */ void lambda$createLocalVideo$0(VideoView view, MediaPlayer player) {
        player.setLooping(false);
        view.start();
    }

    /* JADX INFO: renamed from: lambda$createLocalVideo$1$de-astra-pulse-OnlineVideoActivity, reason: not valid java name */
    /* synthetic */ boolean m44lambda$createLocalVideo$1$deastrapulseOnlineVideoActivity(MediaPlayer player, int what, int extra) {
        Toast.makeText(this, "Dieses Videoformat konnte nicht abgespielt werden.", 1).show();
        return true;
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
        if (this.localVideo != null && this.localVideo.isPlaying()) {
            this.localVideo.pause();
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
        this.playbackHandler.removeCallbacks(this.localPlaybackWatcher);
        if (this.timerView != null) {
            this.timerView.release();
        }
        if (this.webView != null) {
            this.webView.stopLoading();
            this.webView.destroy();
        }
        if (this.localVideo != null) {
            this.localVideo.stopPlayback();
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
        private final ToneGenerator tone;
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
            int volume = Math.max(0, Math.min(100, config.optInt("metronomeVolume", 60)));
            this.tone = new ToneGenerator(3, volume);
            resetTimer();
        }

        void setStateListener(StateListener listener) {
            this.stateListener = listener;
            notifyState();
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
