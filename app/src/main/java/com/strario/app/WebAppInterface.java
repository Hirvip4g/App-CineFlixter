package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebAppInterface {
    private Context context;
    private AtomicBoolean videoSent = new AtomicBoolean(false);

    WebAppInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void playVideo(String url, String title, String description) {
        if (videoSent.getAndSet(true)) {
            return; // Video already sent
        }
        // Accept .txt files as valid video URLs
        launchPlayer(url, title, description);
    }

    @JavascriptInterface
    public void detectVideo(String videoUrl) {
        Log.d("WebAppInterface", "Video detected via JS: " + videoUrl);
        // Support .txt files that contain playlist data
        if (isValidVideoUrl(videoUrl)) {
             if (videoSent.getAndSet(true)) {
                return; // Video already sent, do nothing.
            }
            launchPlayer(videoUrl, "Detected Video", "Auto-detected content");
        }
    }

    @JavascriptInterface
    public void extractFromEmbed(String embedCode) {
        if (!videoSent.get()) {
            extractVideoFromEmbed(embedCode);
        }
    }

    public void resetVideoSentFlag() {
        videoSent.set(false);
    }
    
    public boolean isVideoUrl(String url) {
        return url != null && 
               (url.contains(".m3u8") || 
                url.contains(".mp4") || 
                url.contains(".txt") || // Accept .txt
                url.contains(".urlset/master.txt") || // Keep this specific pattern
                url.endsWith(".txt")); // Accept .txt at the end
    }

    private boolean isValidVideoUrl(String url) {
        return url != null &&
               (url.startsWith("http") || url.startsWith("https")) &&
               (url.contains(".m3u8") ||
                url.contains(".mp4") ||
                url.contains(".m3u") ||
                url.contains("/hls/") ||
                url.contains("manifest.mpd") ||
                url.contains(".ts") ||
                url.contains(".urlset/master.txt") || // Keep this
                url.endsWith(".txt") || // Accept any .txt
                url.contains(".txt")); // Accept .txt anywhere
    }

    private void extractVideoFromEmbed(String embedCode) {
        // Extract video URLs from common embed patterns
        String[] patterns = {
            "src=['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]",
            "src=['\"]([^'\"]+\\.mp4[^'\"]*)['\"]",
            "src=['\"]([^'\"]+\\.txt[^'\"]*)['\"]", // Add .txt support
            "data-video=['\"]([^'\"]+)['\"]",
            "data-src=['\"]([^'\"]+)['\"]"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(embedCode);
            if (m.find()) {
                String videoUrl = m.group(1);
                if (isValidVideoUrl(videoUrl)) {
                    launchPlayer(videoUrl, "Embedded Video", "Video from embed");
                    return;
                }
            }
        }
    }
    
    private void launchPlayer(String url, String title, String description) {
        if (url == null || url.isEmpty()) return;

        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_URL", url);
        intent.putExtra("VIDEO_TITLE", title);
        intent.putExtra("VIDEO_DESCRIPTION", description);
        context.startActivity(intent);
    }
}