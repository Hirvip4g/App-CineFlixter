# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }
-keep class com.strario.app.** { *; }

# WebView JavaScript interface
-keepclassmembers class com.strario.app.WebAppInterface {
    public *;
}

