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
    
    // Domain aliases for StreamWish
    private static final String[] DOMAINS = {
        "streamwish.to", "streamwish.com", "ajmidyad.sbs", "khadhnayad.sbs", "yadmalik.sbs",
        "hayaatieadhab.sbs", "kharabnahs.sbs", "atabkhha.sbs", "atabknha.sbs", "atabknhk.sbs",
        "atabknhs.sbs", "abkrzkr.sbs", "abkrzkz.sbs", "wishembed.pro", "mwish.pro", "strmwis.xyz",
        "awish.pro", "dwish.pro", "embedwish.com", "cilootv.store", "uqloads.xyz", "hlswish.com",
        "swiftplayers.com", "swishsrv.com", "playerwish.com", "streamwish.site", "wishfast.top"
    };

    public interface ExtractionListener {
        void onExtractionResult(String videoUrl);
    }

    public static void extract(Context context, String pageUrl, ExtractionListener listener) {
        executor.execute(() -> {
            try {
                URL url = new URL(pageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Common headers to bypass restrictions
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Safari/537.36");
                connection.setRequestProperty("Referer", getRefererFromUrl(pageUrl));
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                connection.setRequestProperty("Connection", "keep-alive");
                
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine).append("\n");
                }
                in.close();
                connection.disconnect();

                String htmlContent = content.toString();
                
                // First try to extract from eval/packed JS
                String unpacked = unpackJS(htmlContent);
                String videoUrl = extractVideoUrl(unpacked != null ? unpacked : htmlContent);
                
                if (videoUrl != null) {
                    Log.d(TAG, "Extracted URL: " + videoUrl);
                    handler.post(() -> listener.onExtractionResult(videoUrl));
                } else {
                    Log.e(TAG, "Could not extract video URL from: " + pageUrl);
                    handler.post(() -> listener.onExtractionResult(null));
                }

            } catch (Exception e) {
                Log.e(TAG, "Extraction failed for " + pageUrl, e);
                handler.post(() -> listener.onExtractionResult(null));
            }
        });
    }

    private static String unpackJS(String html) {
        // Pattern to find packed/eval JS code
        Pattern pattern = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\).*?\\)\\);");
        Matcher matcher = pattern.matcher(html);
        
        if (matcher.find()) {
            String packed = matcher.group();
            // Basic unpacking - this is a simplified version
            // For production, use a proper JavaScript unpacker
            Pattern urlPattern = Pattern.compile("file:\"(.*?\\.m3u8.*?)\"");
            Matcher urlMatcher = urlPattern.matcher(packed);
            if (urlMatcher.find()) {
                return packed;
            }
        }
        
        // Alternative patterns for different embeds
        Pattern alt = Pattern.compile("sources:\\s*\\[\\s*\\{[^}]*file:\\s*\"([^\"]+\\.m3u8[^\"]*)\"");
        Matcher altMatcher = alt.matcher(html);
        if (altMatcher.find()) {
            return altMatcher.group(1);
        }
        
        return html;
    }

    private static String extractVideoUrl(String content) {
        // Try multiple patterns to extract m3u8 URL
        String[] patterns = {
            "file:\\s*\"(https:[^\"]*\\.m3u8[^\"]*)\"",
            "sources:\\s*\\[\\s*\\{[^}]*file:\\s*\"([^\"]+\\.m3u8[^\"]*)\"",
            "\"hls(\\d+)\"\\s*:\\s*\"(https:[^\"]+\\.m3u8[^\"]*)\"",
            "data-url=\"([^\"]*\\.m3u8[^\"]*)\"",
            "src:\\s*\"([^\"]*\\.m3u8[^\"]*)\""
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(content);
            if (m.find()) {
                String url = m.group(m.groupCount());
                if (url != null && url.startsWith("http")) {
                    return url;
                }
            }
        }
        
        return null;
    }

    private static String getRefererFromUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost();
        } catch (Exception e) {
            return "https://streamwish.to";
        }
    }

    public static boolean isSupported(String url) {
        if (url == null) return false;
        
        for (String domain : DOMAINS) {
            if (url.contains(domain)) {
                return true;
            }
        }
        
        // Also check for common embed patterns
        return url.contains("streamwish") || 
               url.contains("swiftplayers") || 
               url.contains("hlswish") || 
               url.contains("playerwish") ||
               url.contains("uqloads");
    }

    public static void launchPlayer(Context context, String url, String title) {
        if (url == null || url.isEmpty()) return;
        
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_URL", url);
        intent.putExtra("VIDEO_TITLE", title);
        intent.putExtra("VIDEO_DESCRIPTION", "Streamed content");
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