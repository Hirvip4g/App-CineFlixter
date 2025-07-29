package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        
        // Procesar el enlace directamente sin validación
        launchPlayer(url, title, description);
    }

    @JavascriptInterface
    public void detectVideo(String videoUrl) {
        Log.d("WebAppInterface", "Video detectado: " + videoUrl);
        
        // Aceptar cualquier tipo de enlace directamente
        if (videoUrl != null && !videoUrl.isEmpty()) {
            if (!videoSent) {
                videoSent = true;
                launchPlayer(videoUrl, "Video Detectado", "Contenido automático");
            }
        }
    }

    @JavascriptInterface
    public void extractFromEmbed(String embedCode) {
        if (embedCode == null || embedCode.isEmpty()) return;
        
        // Extraer cualquier URL de video de manera más flexible
        String[] patterns = {
            "src=['\"]([^'\"]+)['\"]",
            "href=['\"]([^'\"]+)['\"]",
            "data-video=['\"]([^'\"]+)['\"]",
            "data-src=['\"]([^'\"]+)['\"]",
            "source:\\s*['\"]([^'\"]+)['\"]",
            "file:\\s*['\"]([^'\"]+)['\"]"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(embedCode.toLowerCase());
            if (m.find()) {
                String videoUrl = m.group(1);
                // Enviar directamente a ExoPlayer
                launchPlayer(videoUrl, "Video desde Embed", "Detectado automáticamente");
                return;
            }
        }
        
        // Si no se encuentra patrón, intentar usar el embed completo como URL
        if (embedCode.contains("http")) {
            launchPlayer(embedCode, "Video Directo", "Enlace directo");
        }
    }

    public void resetVideoSentFlag() {
        videoSent = false;
    }
    
    public boolean isVideoUrl(String url) {
        // Aceptar cualquier URL que contenga http/https
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
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