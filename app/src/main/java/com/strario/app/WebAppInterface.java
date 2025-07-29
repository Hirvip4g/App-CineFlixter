package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private Context context;
    private boolean videoSent = false;

    WebAppInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void playVideo(String url, String title, String description) {
        if (videoSent) return;
        videoSent = true;
        
        // Accept .txt files directly without any validation
        launchPlayer(url, title, description);
    }

    @JavascriptInterface
    public void detectVideo(String videoUrl) {
        Log.d("WebAppInterface", "Video detected via JS: " + videoUrl);
        // Accept any .txt file directly
        if (videoUrl != null && videoUrl.contains(".txt")) {
            if (!videoSent) {
                videoSent = true;
                launchPlayer(videoUrl, "Detected Video", "Auto-detected content");
            }
        }
    }

    @JavascriptInterface
    public void extractFromEmbed(String embedCode) {
        // Extract video URLs from common embed patterns including .txt
        String[] patterns = {
            "src=['\"]([^'\"]*\\.txt[^'\"]*)['\"]",
            "src=['\"]([^'\"]*\\.m3u8[^'\"]*)['\"]",
            "src=['\"]([^'\"]*\\.mp4[^'\"]*)['\"]",
            "data-video=['\"]([^'\"]+)['\"]",
            "data-src=['\"]([^'\"]+)['\"]"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(embedCode);
            if (m.find()) {
                String videoUrl = m.group(1);
                // Accept all URLs without validation
                launchPlayer(videoUrl, "Embedded Video", "Video from embed");
                return;
            }
        }
    }
    
    public void resetVideoSentFlag() {
        videoSent = false;
    }
    
    public boolean isVideoUrl(String url) {
        // Accept all common video formats including .txt
        return url != null && (
            url.contains(".m3u8") || 
            url.contains(".mp4") || 
            url.contains(".txt") ||
            url.endsWith(".txt")
        );
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