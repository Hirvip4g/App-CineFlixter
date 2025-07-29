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
        if (videoSent.getAndSet(true)) return;
        launchPlayer(url.trim(), title, description);
    }

    @JavascriptInterface
    public void detectVideo(String videoUrl) {
        // Aceptar cualquier URL que parezca ser un video
        if (videoUrl == null || videoUrl.isEmpty()) return;
        
        String url = videoUrl.trim().toLowerCase();
        boolean isVideo = url.contains(".mp4") || 
                         url.contains(".m3u8") || 
                         url.contains(".avi") || 
                         url.contains(".mkv") || 
                         url.contains(".mov") || 
                         url.contains(".flv") || 
                         url.contains(".webm") ||
                         url.contains("video") ||
                         url.contains("stream");
        
        if (isVideo && videoSent.getAndSet(true) == false) {
            launchPlayer(videoUrl.trim(), "Video Detectado", "Contenido automático");
        }
    }

    @JavascriptInterface
    public void extractFromEmbed(String embedCode) {
        if (embedCode == null || embedCode.isEmpty()) return;
        
        // Extraer cualquier URL de video
        String[] patterns = {
            "https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*",
            "https?://[^\\s\"'<>]+\\.mp4[^\\s\"'<>]*",
            "https?://[^\\s\"'<>]+\\.txt[^\\s\"'<>]*",
            "https?://[^\\s\"'<>]+\\.avi[^\\s\"'<>]*",
            "https?://[^\\s\"'<>]+\\.mkv[^\\s\"'<>]*"
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(embedCode);
            if (m.find()) {
                String url = m.group();
                if (!url.isEmpty()) {
                    launchPlayer(url, "Video desde Embed", "Detectado automáticamente");
                    return;
                }
            }
        }
        
        // Si no encuentra patrón, intentar con el embed completo
        if (embedCode.contains("http")) {
            launchPlayer(embedCode, "Video Directo", "Enlace directo");
        }
    }

    public void resetVideoSentFlag() {
        videoSent.set(false);
    }
    
    private void launchPlayer(String url, String title, String description) {
        if (url == null || url.trim().isEmpty()) return;

        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_URL", url.trim());
        intent.putExtra("VIDEO_TITLE", title != null ? title : "Video");
        intent.putExtra("VIDEO_DESCRIPTION", description != null ? description : "");
        context.startActivity(intent);
    }
}