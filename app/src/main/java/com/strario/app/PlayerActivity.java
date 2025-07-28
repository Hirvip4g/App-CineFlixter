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
        
        if (videoUrl != null && videoUrl.contains(".urlset/master.txt")) {
            handleTxtPlaylist(videoUrl);
        } else {
            preparePlayer(videoUrl);
        }
    }
    
    private void handleTxtPlaylist(String txtUrl) {
        executor.execute(() -> {
            String m3u8Url = null;
            try {
                // Get base URL for resolving relative paths
                String baseUrl = txtUrl.substring(0, txtUrl.lastIndexOf('/') + 1);

                URL url = new URL(txtUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                // Set a user agent as some servers require it
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                
                // Read the entire content first
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                
                // Check if it's actually an M3U8 playlist
                String fullContent = content.toString();
                if (fullContent.contains("#EXTM3U") || fullContent.contains(".m3u8")) {
                    // This is actually an M3U8 playlist disguised as .txt
                    m3u8Url = txtUrl;
                } else {
                    // Look for actual M3U8 URLs within the content
                    String[] lines = fullContent.split("\n");
                    for (String contentLine : lines) {
                        contentLine = contentLine.trim();
                        if (contentLine.contains(".m3u8") || contentLine.contains("master.m3u8")) {
                            if (contentLine.startsWith("http")) {
                                m3u8Url = contentLine;
                            } else {
                                m3u8Url = baseUrl + contentLine;
                            }
                            break;
                        }
                    }
                    
                    // If no specific M3U8 URL found, try the base URL
                    if (m3u8Url == null) {
                        // Try replacing .txt with .m3u8
                        String modifiedUrl = txtUrl.replace(".txt", ".m3u8");
                        if (isValidM3U8(modifiedUrl)) {
                            m3u8Url = modifiedUrl;
                        } else {
                            // Use the original .txt URL as is - ExoPlayer might handle it
                            m3u8Url = txtUrl;
                        }
                    }
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                // Fallback: try using the original URL
                m3u8Url = txtUrl;
            }

            final String finalM3u8Url = m3u8Url;
            handler.post(() -> {
                if (finalM3u8Url != null) {
                    preparePlayer(finalM3u8Url);
                } else {
                    Toast.makeText(this, "Could not find a valid video stream.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        });
    }
    
    private boolean isValidM3U8(String url) {
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
        this.videoUrl = url;
        
        // Handle .txt playlists that contain .m3u8 content
        if (url != null && (url.endsWith(".txt") || url.contains(".urlset/master.txt"))) {
            handleTxtPlaylist(url);
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

        // Play video
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