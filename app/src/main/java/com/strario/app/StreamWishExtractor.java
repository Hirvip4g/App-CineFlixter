package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamWishExtractor {

    private static final String TAG = "StreamWishExtractor";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    
    // Solo dominio swiftplayers.com
    private static final String[] DOMAINS = {
        "https://swiftplayers.com"
    };

    public interface ExtractionListener {
        void onExtractionResult(String videoUrl);
    }

    public static void extract(Context context, String pageUrl, ExtractionListener listener) {
        new Thread(() -> {
            try {
                URL url = new URL(pageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Headers necesarios
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                connection.setRequestProperty("Referer", "https://swiftplayers.com");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine).append("\n");
                }
                in.close();
                connection.disconnect();

                String htmlContent = content.toString();
                
                // Extraer URL del video usando el patrón específico
                String videoUrl = extractVideoUrl(htmlContent);
                
                if (videoUrl != null) {
                    Log.d(TAG, "URL extraída: " + videoUrl);
                    listener.onExtractionResult(videoUrl);
                } else {
                    Log.e(TAG, "No se pudo extraer el video de: " + pageUrl);
                    listener.onExtractionResult(null);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error en extracción de " + pageUrl, e);
                listener.onExtractionResult(null);
            }
        }).start();
    }

    private static String extractVideoUrl(String content) {
        // Patrones para extraer .m3u8
        String[] patterns = {
            "\"hls(\\d+)\"\\s*:\\s*\"(https:[^\"]+\\.m3u8[^\"]*)\"",
            "file:\\s*\"(https:[^\"]*\\.m3u8[^\"]*)\"",
            "sources:\\s*\\[\\s*\\{[^}]*file:\\s*\"([^\"]+\\.m3u8[^\"]*)"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(content);
            if (m.find()) {
                String url = m.group(m.groupCount());
                if (url.startsWith("http")) {
                    return url;
                }
            }
        }
        
        return null;
    }

    public static boolean isSupported(String url) {
        if (url == null) return false;
        return url.contains("swiftplayers.com");
    }

    public static void launchPlayer(Context context, String url, String title) {
        if (url == null || url.isEmpty()) return;
        
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_URL", url);
        intent.putExtra("VIDEO_TITLE", title);
        intent.putExtra("VIDEO_DESCRIPTION", "Contenido de SwiftPlayers");
        context.startActivity(intent);
    }

    // Utility method to handle txt playlists
    public static void handleTxtPlaylist(Context context, String txtUrl, ExtractionListener listener) {
        executor.execute(() -> {
            String m3u8Url = null;
            try {
                URL url = new URL(txtUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                connection.connect();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().endsWith(".m3u8")) {
                        m3u8Url = line.trim();
                        break;
                    }
                }
                reader.close();
                
                String finalUrl = m3u8Url != null ? m3u8Url : txtUrl.replace(".txt", ".m3u8");
                handler.post(() -> listener.onExtractionResult(finalUrl));
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to handle txt playlist", e);
                handler.post(() -> listener.onExtractionResult(null));
            }
        });
    }
}