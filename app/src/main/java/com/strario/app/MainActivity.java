package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.util.Log;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    
    private WebView webView;
    private AtomicBoolean videoSent = new AtomicBoolean(false);
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize WebView
        webView = findViewById(R.id.webView);
        
        // Configure WebView
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
        
        // Setup JavaScript interface
        WebAppInterface webAppInterface = new WebAppInterface(this);
        webView.addJavascriptInterface(webAppInterface, "AndroidInterface");
        
        // Configure WebView client for video extraction
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d("MainActivity", "Loading URL: " + url);
                
                // Reset flag on new page load
                webAppInterface.resetVideoSentFlag();
                
                return super.shouldOverrideUrlLoading(view, request);
            }
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // Let the WebAppInterface handle video detection from network requests
                if (webAppInterface.isVideoUrl(url)) {
                    // Pass it to the JS interface to avoid race conditions with user clicks
                    // The JS interface can decide whether to play it or not.
                    runOnUiThread(() -> view.loadUrl("javascript:AndroidInterface.detectVideo('" + url + "');"));
                    // Let the request continue, in case the web UI needs it.
                    // The player will handle opening the video separately.
                }
                
                return super.shouldInterceptRequest(view, request);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Reset flag for new page
                webAppInterface.resetVideoSentFlag();
            }
        });
        
        // Load the web app
        webView.loadUrl("file:///android_asset/index.html");
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    // JavaScript interface for video detection
    public class WebAppInterface {
        private AtomicBoolean jsVideoSent = new AtomicBoolean(false);
        private Context context;
        
        WebAppInterface(Context context) {
            this.context = context;
        }
        
        @JavascriptInterface
        public void detectVideo(String videoUrl) {
            Log.d("WebAppInterface", "Video detected: " + videoUrl);
            if (!jsVideoSent.get() && isValidVideoUrl(videoUrl)) {
                launchPlayer(videoUrl, "Detected Video", "Auto-detected streaming content");
                jsVideoSent.set(true);
            }
        }
        
        @JavascriptInterface
        public void playVideo(String url, String title, String description) {
            if (!jsVideoSent.get()) {
                launchPlayer(url, title, description);
                jsVideoSent.set(true);
            }
        }
        
        @JavascriptInterface
        public void extractFromEmbed(String embedCode) {
            if (!jsVideoSent.get()) {
                extractVideoFromEmbed(embedCode);
            }
        }
        
        public void resetVideoSentFlag() {
            jsVideoSent.set(false);
        }
        
        public boolean isVideoUrl(String url) {
            return url != null && 
                   (url.startsWith("http") || url.startsWith("https")) && 
                   (url.contains(".m3u8") || 
                    url.contains(".mp4") || 
                    url.contains(".m3u") ||
                    url.contains("/hls/") ||
                    url.contains("manifest.mpd") ||
                    url.contains(".ts") ||
                    url.contains(".urlset/master.txt"));
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
                    url.contains(".urlset/master.txt"));
        }
        
        private void extractVideoFromEmbed(String embedCode) {
            // Extract video URLs from common embed patterns
            String[] patterns = {
                "src=['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]",
                "src=['\"]([^'\"]+\\.mp4[^'\"]*)['\"]",
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
            
            ((MainActivity) context).runOnUiThread(() -> {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("VIDEO_URL", url);
                intent.putExtra("VIDEO_TITLE", title);
                intent.putExtra("VIDEO_DESCRIPTION", description);
                context.startActivity(intent);
            });
        }
    }
}