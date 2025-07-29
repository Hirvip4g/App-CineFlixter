package com.cineflixter.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

public class PlayerActivity extends AppCompatActivity {
    
    private ExoPlayer exoPlayer;
    private com.google.android.exoplayer2.ui.PlayerView playerView;
    private boolean isFullscreen = false;
    private DefaultTrackSelector trackSelector;
    private String videoUrl;
    private long resumePosition = C.TIME_UNSET;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        // Ocultar barras del sistema
        hideSystemBars();
        
        // Inicializar PlayerView
        playerView = findViewById(R.id.playerView);
        
        // Obtener URL del video desde el intent
        videoUrl = getIntent().getStringExtra("VIDEO_URL");
        
        if (videoUrl != null && !videoUrl.isEmpty()) {
            // Procesar directamente sin validación
            preparePlayer(videoUrl);
        }
    }
    
    private void preparePlayer(String url) {
        this.videoUrl = url;
        startPlayer();
    }
    
    private void startPlayer() {
        String title = getIntent().getStringExtra("VIDEO_TITLE");
        
        // Inicializar ExoPlayer
        trackSelector = new DefaultTrackSelector(this);
        exoPlayer = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(exoPlayer);
        
        // Configurar controles personalizados
        ImageButton backButton = playerView.findViewById(R.id.exo_back_button);
        ImageButton settingsButton = playerView.findViewById(R.id.exo_settings);
        ImageButton fullscreenButton = playerView.findViewById(R.id.exo_fullscreen);
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
        }
        
        // Configurar y reproducir el video
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
        exoPlayer.setMediaItem(mediaItem);
        
        exoPlayer.prepare();
        exoPlayer.play();
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
    
    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController =
                new WindowInsetsControllerCompat(getWindow(), playerView);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }
    
    @Override
    protected void onDestroy() {
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