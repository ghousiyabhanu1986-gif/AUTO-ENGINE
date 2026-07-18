-keep class com.auto.engine.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep public class * extends android.webkit.WebViewClient
-keep public class * extends android.webkit.WebChromeClient
