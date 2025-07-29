package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamWishExtractor {

    public static final String NAME = "Streamwish";
    public static final String MAIN_URL = "https://swiftplayers.com";

    public interface ExtractionCallback {
        void onSuccess(String videoUrl);
    }

    public static void extract(Context context, String link, ExtractionCallback callback) {
        new Thread(() -> {
            try {
                Document doc = Jsoup.connect(link).get();
                String html = doc.html();

                String script = extractScript(html);
                if (script == null) {
                    callback.onSuccess(null);
                    return;
                }

                String source = extractM3U8(script);
                callback.onSuccess(source);

            } catch (Exception e) {
                Log.e("StreamWishExtractor", "Error extracting", e);
                callback.onSuccess(null);
            }
        }).start();
    }

    private static String extractScript(String html) {
        Pattern pattern = Pattern.compile("<script .*?>(eval.*?)</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractM3U8(String script) {
        Pattern pattern = Pattern.compile("\"hls(\\d+)\"\\s*:\\s*\"(https:[^\"]+\\.m3u8[^\"]*)\"");
        Matcher matcher = pattern.matcher(script);
        while (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    public static void launchPlayer(Context context, String url, String title) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_URL", url);
        intent.putExtra("VIDEO_TITLE", title);
        context.startActivity(intent);
    }
}

