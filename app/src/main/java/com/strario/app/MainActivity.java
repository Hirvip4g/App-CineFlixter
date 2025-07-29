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
                
                // Inyectar script para detectar m3u8 dinámicos
                String injectionScript = 
                    "setTimeout(() => {" +
                    "  const findM3U8 = () => {" +
                    "    const sources = [];" +
                    "    // Buscar en video elements" +
                    "    document.querySelectorAll('video').forEach(v => {" +
                    "      if (v.src && v.src.includes('.m3u8')) sources.push(v.src);" +
                    "      v.querySelectorAll('source').forEach(s => {" +
                    "        if (s.src && s.src.includes('.m3u8')) sources.push(s.src);" +
                    "      });" +
                    "    });" +
                    "    // Buscar en objetos JavaScript" +
                    "    for (let prop in window) {" +
                    "      try {" +
                    "        if (window[prop] && typeof window[prop] === 'object') {" +
                    "          const str = JSON.stringify(window[prop]);" +
                    "          const matches = str.match(/https?:[^\"']*\\.m3u8[^\"']*/g);" +
                    "          if (matches) sources.push(...matches);" +
                    "        }" +
                    "      } catch(e) {}" +
                    "    }" +
                    "    // Buscar en fetch/xhr interceptados" +
                    "    const originalFetch = window.fetch;" +
                    "    window.fetch = function(...args) {" +
                    "      const url = args[0];" +
                    "      if (typeof url === 'string' && url.includes('.m3u8')) {" +
                    "        AndroidInterface.detectVideo(url);" +
                    "      }" +
                    "      return originalFetch.apply(this, args);" +
                    "    };" +
                    "    // Buscar en network requests" +
                    "    const originalOpen = XMLHttpRequest.prototype.open;" +
                    "    XMLHttpRequest.prototype.open = function(method, url) {" +
                    "      if (typeof url === 'string' && url.includes('.m3u8')) {" +
                    "        AndroidInterface.detectVideo(url);" +
                    "      }" +
                    "      return originalOpen.apply(this, arguments);" +
                    "    };" +
                    "    return sources;" +
                    "  };" +
                    "  const m3u8s = findM3U8();" +
                    "  m3u8s.forEach(url => AndroidInterface.detectVideo(url));" +
                    "}, 3000);";
                
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