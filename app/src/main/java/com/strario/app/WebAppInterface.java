package com.strario.app;

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
        launchPlayer(url, title, description);
    }

    @JavascriptInterface
    public void detectVideo(String videoUrl) {
        Log.d("WebAppInterface", "Video detected via JS: " + videoUrl);
        if (isValidVideoUrl(videoUrl)) {
             if (videoSent.getAndSet(true)) {
                return; 
            }
            launchPlayer(videoUrl, "Detected Video", "Auto-detected content");
        }
    }

    public void resetVideoSentFlag() {
        videoSent.set(false);
    }
    
    public boolean isVideoUrl(String url) {
        return url != null && (url.contains(".m3u8") || url.contains(".mp4"));
    }

    private boolean isValidVideoUrl(String url) {
        return url != null &&
               (url.startsWith("http") || url.startsWith("https")) &&
               (url.contains(".m3u8") ||
                url.contains(".mp4") ||
                url.contains(".m3u") ||
                url.contains("/hls/") ||
                url.contains("manifest.mpd") ||
                url.contains(".ts"));
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

