# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }
-keep class com.cineflixter.app.** { *; }

# WebView JavaScript interface
-keepclassmembers class com.cineflixter.app.WebAppInterface {
    public *;
}

