# Keep WebView JavaScript interface
-keepclassmembers class com.kulms.android.data.remote.WebViewFetcher$JsBridge {
    public *;
}

# Keep Room entities
-keep class com.kulms.android.data.model.** { *; }
