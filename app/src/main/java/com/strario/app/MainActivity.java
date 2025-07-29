package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

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

        WebAppInterface webAppInterface = new WebAppInterface(this);
        webView.addJavascriptInterface(webAppInterface, "AndroidInterface");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("swiftplayers.com")) {
                    new Thread(() -> {
                        try {
                            String videoUrl = StreamWishExtractor.extract(url);
                            if (videoUrl != null) {
                                launchPlayer(videoUrl, "SwiftPlayers Video");
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error extracting video", e);
                        }
                    }).start();
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }
        });

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
        private Context context;

        WebAppInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void detectVideo(String videoUrl) {
            Log.d("WebAppInterface", "Video detected: " + videoUrl);
            if (isValidVideoUrl(videoUrl)) {
                launchPlayer(videoUrl, "Detected Video", "Auto-detected streaming content");
            }
        }

        @JavascriptInterface
        public void playVideo(String url, String title, String description) {
            if (isValidVideoUrl(url)) {
                launchPlayer(url, title, description);
            }
        }

        @JavascriptInterface
        public void extractFromEmbed(String embedCode) {
            if (isValidVideoUrl(embedCode)) {
                extractVideoFromEmbed(embedCode);
            }
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

    private void launchPlayer(String url, String title) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("VIDEO_URL", url);
        intent.putExtra("VIDEO_TITLE", title);
        startActivity(intent);
    }
}