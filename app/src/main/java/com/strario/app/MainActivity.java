package com.cineflixter.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Add JavaScript interface
        WebAppInterface webAppInterface = new WebAppInterface(this);
        webView.addJavascriptInterface(webAppInterface, "AndroidInterface");

        // Configure WebView
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                Log.d("WebAppConsole", message);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // Check if it's a streamwish/swiftplayers link
                if (url.contains("swiftplayers.com") || url.contains("streamwish.")) {
                    extractVideo(url);
                    return true;
                }
                
                // Check for .txt playlist
                if (url.contains(".urlset/master.txt")) {
                    handleTxtPlaylist(url);
                    return true;
                }
                
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                // Check current page for video URLs
                checkUrlForVideo(url);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void extractVideo(String url) {
        new Thread(() -> {
            try {
                String videoUrl = StreamWishExtractor.extract(url);
                launchPlayer(videoUrl, "Stream Video");
            } catch (Exception e) {
                Log.e("MainActivity", "Error extracting video", e);
                runOnUiThread(() -> 
                    Toast.makeText(this, "Error loading video: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private void handleTxtPlaylist(String txtUrl) {
        new Thread(() -> {
            try {
                URL url = new URL(txtUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.connect();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                String m3u8Url = null;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.endsWith(".m3u8") && !line.startsWith("#")) {
                        m3u8Url = Uri.parse(txtUrl).resolve(line).toString();
                        break;
                    }
                }
                reader.close();
                
                final String finalM3u8Url = m3u8Url;
                runOnUiThread(() -> {
                    if (finalM3u8Url != null) {
                        launchPlayer(finalM3u8Url, "Video M3U8");
                    } else {
                        Toast.makeText(this, "No encontró el enlace de video", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e("MainActivity", "Error processing txt playlist", e);
                runOnUiThread(() -> 
                    Toast.makeText(this, "Error processing playlist", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private void checkUrlForVideo(String url) {
        if (isValidVideoUrl(url) && url.endsWith(".m3u8")) {
            launchPlayer(url, "Streaming Video");
        }
    }

    private boolean isValidVideoUrl(String url) {
        if (url == null) return false;
        return url.endsWith(".m3u8") || 
               url.endsWith(".mp4") || 
               url.contains(".urlset/master.txt") ||
               (url.contains(".m3u8") && !url.contains(".js") && !url.contains(".css"));
    }

    private void launchPlayer(String url, String title) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("VIDEO_URL", url);
        intent.putExtra("VIDEO_TITLE", title);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public static class WebAppInterface {
        private Context context;

        WebAppInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void playVideo(String url, String title, String description) {
            if (url != null && !url.isEmpty()) {
                ((MainActivity) context).launchPlayer(url, title);
            }
        }

        @JavascriptInterface
        public void detectVideo(String videoUrl) {
            if (videoUrl != null && ((MainActivity) context).isValidVideoUrl(videoUrl)) {
                ((MainActivity) context).launchPlayer(videoUrl, "Auto-detected");
            }
        }
    }
}