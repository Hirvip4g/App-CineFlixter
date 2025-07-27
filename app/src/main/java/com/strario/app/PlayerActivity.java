package com.strario.app;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {
    
    private ExoPlayer exoPlayer;
    private com.google.android.exoplayer2.ui.PlayerView playerView;
    private ImageButton fullscreenButton;
    private ImageButton settingsButton;
    private boolean isFullscreen = false;
    private DefaultTrackSelector trackSelector;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        // Hide system bars for immersive experience
        hideSystemBars();
        
        // Initialize PlayerView
        playerView = findViewById(R.id.playerView);
        
        // Initialize ExoPlayer with DefaultTrackSelector for quality/audio selection
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector
            .buildUponParameters()
            .setMaxVideoSize(1920, 1080)
            .setMaxVideoBitrate(Integer.MAX_VALUE)
            .setForceHighestSupportedBitrate(true));
        exoPlayer = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(exoPlayer);
        
        // Get video URL from intent
        String videoUrl = getIntent().getStringExtra("VIDEO_URL");
        String title = getIntent().getStringExtra("VIDEO_TITLE");
        
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
                    updatePlayPauseButton(); // Update icon immediately
                }
            });
        }

        // Play video
        if (videoUrl != null) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            
            // Configurar audio en español y calidad automática después de preparar
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        // Esperar a que el video esté listo para configurar audio y calidad
                        configureInitialSettings();
                    }
                }
            });
            
            exoPlayer.play();
        }
        
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton();
            }
        });
    }

    private void configureInitialSettings() {
        // Configurar audio en español por defecto
        selectSpanishAudio();
    }

    private void selectSpanishAudio() {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;
        
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                for (int j = 0; j < mappedTrackInfo.getTrackGroups(i).length; j++) {
                    for (int k = 0; k < mappedTrackInfo.getTrackGroups(i).get(j).length; k++) {
                        Format format = mappedTrackInfo.getTrackGroups(i).get(j).getFormat(k);
                        if ("es".equalsIgnoreCase(format.language)) {
                            DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters();
                            parametersBuilder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), 
                                new DefaultTrackSelector.SelectionOverride(j, k));
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

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            updatePlayPauseButton();
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