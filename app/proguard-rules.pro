# Keep WebView JavaScript interface
-keepclassmembers class com.radian0523.kulms_plus_for_android.data.remote.WebViewFetcher$JsBridge {
    public *;
}

# Keep Room entities
-keep class com.radian0523.kulms_plus_for_android.data.model.** { *; }
