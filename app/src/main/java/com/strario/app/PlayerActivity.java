package com.cineflixter.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.C;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PlayerActivity extends AppCompatActivity {
    
    private ExoPlayer exoPlayer;
    private com.google.android.exoplayer2.ui.PlayerView playerView;
    private ImageButton fullscreenButton;
    private ImageButton settingsButton;
    private boolean isFullscreen = false;
    private DefaultTrackSelector trackSelector;
    private SharedPreferences sharedPreferences;
    private String videoUrl;
    private long resumePosition = C.TIME_UNSET;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Method to extract a stable video ID from the URL
    private String getVideoId(String url) {
        if (url == null) {
            return null;
        }
        // Pattern to find the numeric ID before "/master.m3u8"
        Pattern pattern = Pattern.compile("(\\d+)/master\\.m3u8");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1); // Return the captured numeric ID
        }
        // Fallback to using the full URL if the pattern doesn't match
        return url;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        // Hide system bars for immersive experience
        hideSystemBars();
        
        // Initialize SharedPreferences for playback positions
        sharedPreferences = getSharedPreferences("PlaybackPositions", MODE_PRIVATE);

        // Initialize PlayerView
        playerView = findViewById(R.id.playerView);
        
        // Get video URL from intent
        videoUrl = getIntent().getStringExtra("VIDEO_URL");
        
        // Handle .txt files directly without validation
        preparePlayer(videoUrl);
    }
    
    private void preparePlayer(String url) {
        // Skip all validation for .txt files
        String videoId = getVideoId(videoUrl);
        long savedPosition = sharedPreferences.getLong(videoId, C.TIME_UNSET);
        
        this.videoUrl = url;
        
        if (savedPosition > 1000) {
            new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("Reanudar reproducción")
                .setMessage("¿Quieres continuar donde lo dejaste?")
                .setPositiveButton("Reanudar", (dialog, which) -> {
                    resumePosition = savedPosition;
                    startPlayer();
                })
                .setNegativeButton("Empezar de nuevo", (dialog, which) -> {
                    clearPlaybackPosition();
                    resumePosition = C.TIME_UNSET;
                    startPlayer();
                })
                .setCancelable(false)
                .show();
        } else {
            startPlayer();
        }
    }
    
    private void startPlayer() {
        String title = getIntent().getStringExtra("VIDEO_TITLE");

        // Initialize ExoPlayer with DefaultTrackSelector for quality/audio selection
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector
            .buildUponParameters()
            .setMaxVideoSize(1920, 1080)
            .setMaxVideoBitrate(Integer.MAX_VALUE)
            .setForceHighestSupportedBitrate(true));
        exoPlayer = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(exoPlayer);
        
        // Find custom controls
        ImageButton backButton = playerView.findViewById(R.id.exo_back_button);
        settingsButton = playerView.findViewById(R.id.exo_settings);
        fullscreenButton = playerView.findViewById(R.id.exo_fullscreen);
        ImageButton playPauseButton = playerView.findViewById(R.id.exo_play_pause);
        TextView titleTextView = playerView.findViewById(R.id.exo_video_title);

        if (title != null && titleTextView != null) {
            titleTextView.setText(title);
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackPressed());
        }

        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> showTrackSelectionDialog());
        }

        if (fullscreenButton != null) {
            fullscreenButton.setOnClickListener(v -> toggleFullscreen());
            updateFullscreenButton();
        }

        if (playPauseButton != null) {
            playPauseButton.setOnClickListener(v -> {
                if (exoPlayer != null) {
                    boolean playing = exoPlayer.getPlayWhenReady();
                    exoPlayer.setPlayWhenReady(!playing);
                }
            });
        }

        // Play video - handle .txt as valid stream
        if (videoUrl != null) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            exoPlayer.setMediaItem(mediaItem);
            
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        // Seek to resume position if it was set
                        if (resumePosition != C.TIME_UNSET) {
                            exoPlayer.seekTo(resumePosition);
                            resumePosition = C.TIME_UNSET;
                        }
                    }
                }
            });

            exoPlayer.prepare();
            exoPlayer.play();
        }
    }

    private void showTrackSelectionDialog() {
        TrackSelectionDialogFragment dialog = TrackSelectionDialogFragment.newInstance(exoPlayer, trackSelector);
        dialog.show(getSupportFragmentManager(), "track_selection");
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void updateFullscreenButton() {
        if (fullscreenButton == null) return;
        
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isFullscreen = true;
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit);
        } else {
            isFullscreen = false;
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateFullscreenButton();
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController =
                new WindowInsetsControllerCompat(getWindow(), playerView);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void savePlaybackPosition() {
        if (exoPlayer != null && videoUrl != null) {
            long currentPosition = exoPlayer.getCurrentPosition();
            long duration = exoPlayer.getDuration();
            String videoId = getVideoId(videoUrl);
            
            if (currentPosition > 0 && (duration == C.TIME_UNSET || currentPosition < duration * 0.95)) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong(videoId, currentPosition);
                editor.apply();
            } else {
                clearPlaybackPosition();
            }
        }
    }

    private void clearPlaybackPosition() {
        if (videoUrl != null) {
            String videoId = getVideoId(videoUrl);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(videoId);
            editor.apply();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePlaybackPosition();
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        savePlaybackPosition();
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}