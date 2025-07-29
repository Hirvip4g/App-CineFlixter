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
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;

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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
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
        if (videoUrl != null && videoUrl.contains(".txt")) {
            handleTxtPlaylist(videoUrl);
        } else {
            preparePlayer(videoUrl);
        }
    }
    
    private void handleTxtPlaylist(String txtUrl) {
        // .txt files contain M3U8 content - use them directly
        String videoId = getVideoId(txtUrl);
        long savedPosition = sharedPreferences.getLong(videoId, C.TIME_UNSET);
        
        // Remove format verification, trust that .txt is valid
        this.videoUrl = txtUrl;
        
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
    
    private boolean isValidM3U8(String url) {
        // Always return true for .txt files, trust the server responds
        if (url != null && url.endsWith(".txt")) {
            return true;
        }
        
        try {
            URL testUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) testUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.connect();
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void preparePlayer(String url) {
        // Handle .txt files directly without validation
        if (url != null && url.endsWith(".txt")) {
            String videoId = getVideoId(url);
            long savedPosition = sharedPreferences.getLong(videoId, C.TIME_UNSET);
            
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
            return;
        }
        
        // Use a stable ID for the video to check for saved position
        String videoId = getVideoId(videoUrl);
        
        // Check for a saved position using the stable ID
        long savedPosition = sharedPreferences.getLong(videoId, C.TIME_UNSET);
        
        // If there's a significant saved position, ask the user to resume
        if (savedPosition > 1000) { // More than 1 second
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
            // No saved position, or it's negligible, so start from the beginning
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
            
            // Configurar audio en español y calidad automática después de preparar
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        // Esperar a que el video esté listo para configurar audio y calidad
                        configureInitialSettings();
                        // Seek to resume position if it was set
                        if (resumePosition != C.TIME_UNSET) {
                            exoPlayer.seekTo(resumePosition);
                            resumePosition = C.TIME_UNSET; // Reset after seeking once
                        }
                    }
                }
                
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePlayPauseButton();
                }
            });

            exoPlayer.prepare();
            exoPlayer.play();
        }
    }

    private void configureInitialSettings() {
        // Configurar audio en español por defecto
        selectSpanishAudio();
    }

    private void selectSpanishAudio() {
        MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;
        
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                for (int j = 0; j < mappedTrackInfo.getTrackGroups(i).length; j++) {
                    for (int k = 0; k < mappedTrackInfo.getTrackGroups(i).get(j).length; k++) {
                        Format format = mappedTrackInfo.getTrackGroups(i).get(j).getFormat(k);
                        if ("es".equalsIgnoreCase(format.language)) {
                            DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters();
                            parametersBuilder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), 
                                new SelectionOverride(j, k));
                            trackSelector.setParameters(parametersBuilder.build());
                            return;
                        }
                    }
                }
            }
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

    private void updatePlayPauseButton() {
        ImageButton playPauseButton = playerView.findViewById(R.id.exo_play_pause);
        if (playPauseButton != null && exoPlayer != null) {
            playPauseButton.setImageResource(
                exoPlayer.getPlayWhenReady() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play
            );
        }
    }

    private void savePlaybackPosition() {
        if (exoPlayer != null && videoUrl != null) {
            long currentPosition = exoPlayer.getCurrentPosition();
            long duration = exoPlayer.getDuration();
            String videoId = getVideoId(videoUrl);
            
            // Don't save if position is invalid or video is almost finished (e.g., 95%)
            if (currentPosition > 0 && (duration == C.TIME_UNSET || currentPosition < duration * 0.95)) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong(videoId, currentPosition);
                editor.apply();
            } else {
                // If video is finished or position is invalid, clear the saved position
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
            updatePlayPauseButton();
        }
    }

    @Override
    protected void onDestroy() {
        savePlaybackPosition();
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
        executor.shutdownNow();
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}