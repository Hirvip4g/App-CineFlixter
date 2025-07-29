package com.cineflixter.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.util.Log;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

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
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36");
        
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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Reset flag for new page
                webAppInterface.resetVideoSentFlag();
                
                // Enhanced detection script
                String injectionScript = 
                    "(function() {" +
                    "  function detectVideo() {" +
                    "    const sources = [];" +
                    "    " +
                    "    // 1. Video elements with src" +
                    "    document.querySelectorAll('video[src]').forEach(v => {" +
                    "      if (v.src) sources.push(v.src);" +
                    "    });" +
                    "    " +
                    "    // 2. Source elements" +
                    "    document.querySelectorAll('source[src]').forEach(s => {" +
                    "      if (s.src) sources.push(s.src);" +
                    "    });" +
                    "    " +
                    "    // 3. Common video file extensions" +
                    "    const videoExtensions = ['.m3u8', '.mp4', '.avi', '.mkv', '.mov', '.wmv', '.flv', '.webm'];" +
                    "    const allLinks = Array.from(document.querySelectorAll('a, link, script, iframe')).map(el => el.href || el.src || el.getAttribute('data-src')).filter(Boolean);" +
                    "    " +
                    "    allLinks.forEach(link => {" +
                    "      if (videoExtensions.some(ext => link.toLowerCase().includes(ext))) {" +
                    "        sources.push(link);" +
                    "      }" +
                    "    });" +
                    "    " +
                    "    // 4. Embedded players" +
                    "    const embeds = document.querySelectorAll('iframe, embed, object');" +
                    "    embeds.forEach(embed => {" +
                    "      const src = embed.src || embed.getAttribute('data-src') || embed.getAttribute('data-video');" +
                    "      if (src) sources.push(src);" +
                    "    });" +
                    "    " +
                    "    // 5. Check window objects for video URLs" +
                    "    try {" +
                    "      const allText = document.body.innerText;" +
                    "      const urlRegex = /https?:\\/\\/[^\\s\"'<>]+/g;" +
                    "      const foundUrls = allText.match(urlRegex) || [];" +
                    "      foundUrls.forEach(url => {" +
                    "        if (videoExtensions.some(ext => url.toLowerCase().includes(ext))) {" +
                    "          sources.push(url.split('?')[0]);" +
                    "        }" +
                    "      });" +
                    "    } catch(e) {}" +
                    "    " +
                    "    // Send unique sources" +
                    "    const uniqueSources = [...new Set(sources)];" +
                    "    uniqueSources.forEach(source => {" +
                    "      if (source && source.trim()) {" +
                    "        AndroidInterface.detectVideo(source.split('?')[0]);" +
                    "      }" +
                    "    });" +
                    "  }" +
                    "  " +
                    "  // Run detection multiple times" +
                    "  setTimeout(detectVideo, 1000);" +
                    "  setTimeout(detectVideo, 3000);" +
                    "  setTimeout(detectVideo, 5000);" +
                    "  setInterval(detectVideo, 10000);" +
                    "})();";
                
                view.loadUrl("javascript:" + injectionScript);
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
            
            // Aceptar cualquier URL sin validación
            launchPlayer(url, title, description);
        }

        @JavascriptInterface
        public void detectVideo(String videoUrl) {
            Log.d("WebAppInterface", "Video detected via JS: " + videoUrl);
            
            // Aceptar cualquier video sin validación
            if (videoUrl != null && !videoUrl.isEmpty()) {
                if (videoSent.getAndSet(true)) {
                    return; // Video already sent, do nothing.
                }
                launchPlayer(videoUrl, "Detected Video", "Auto-detected content");
            }
        }

        @JavascriptInterface
        public void extractFromEmbed(String embedCode) {
            if (!videoSent.get()) {
                // Extraer cualquier URL que contenga http/https
                String[] patterns = {
                    "https?://[^\\s\"']+\\.m3u8[^\\s\"']*",
                    "https?://[^\\s\"']+\\.mp4[^\\s\"']*",
                    "https?://[^\\s\"']+\\.txt[^\\s\"']*"
                };
                
                for (String pattern : patterns) {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                    java.util.regex.Matcher m = p.matcher(embedCode);
                    if (m.find()) {
                        String videoUrl = m.group();
                        launchPlayer(videoUrl, "Video from Embed", "Detected automatically");
                        return;
                    }
                }
                
                // Si no encuentra patrón, probar con el embed completo
                if (embedCode.contains("http")) {
                    launchPlayer(embedCode, "Direct Link", "Direct video link");
                }
            }
        }

        public void resetVideoSentFlag() {
            videoSent.set(false);
        }
        
        public boolean isVideoUrl(String url) {
            return url != null && url.startsWith("http");
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
}