package com.cineflixter.app;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Url;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class StreamWishExtractor {

    public static final String NAME = "Streamwish";
    public static final String MAIN_URL = "https://swiftplayers.com";

    public static String extract(String link) throws Exception {
        try {
            Document document = Jsoup.connect(link).get();
            String html = document.html();

            String script = extractScript(html);
            if (script == null) {
                throw new Exception("Can't retrieve script");
            }

            String source = extractM3U8(script);
            if (source == null) {
                throw new Exception("Can't retrieve m3u8");
            }

            return source;
        } catch (Exception e) {
            Log.e("StreamWishExtractor", "Error extracting", e);
            throw e;
        }
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

    public static String getName() {
        return NAME;
    }

    public static String getMainUrl() {
        return MAIN_URL;
    }

    public interface Service {
        @GET
        String get(@Url String url, @Header("referer") String referer);
    }
}

