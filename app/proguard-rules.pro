# Add project specific ProGuard rules here.
# Gson uses reflection for these API responses and persisted journal records.
# Preserve their JSON field names across updates and backup imports.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.david.carpcast.network.NominatimResult { *; }
-keep class com.david.carpcast.network.FarmSenseMoonResponse { *; }
-keep class com.david.carpcast.network.SunriseSunsetResponse { *; }
-keep class com.david.carpcast.network.SunriseSunsetResults { *; }
-keep class com.david.carpcast.journal.CatchRecord { *; }
-keep class com.david.carpcast.journal.FishingSession { *; }
-keep class com.david.carpcast.journal.JournalArchive { *; }
-keep class com.david.carpcast.journal.CachedForecast { *; }
-keep enum com.david.carpcast.journal.Waterbody { *; }

# Retrofit instantiates these services through dynamic proxies. Keep even
# services whose HTTP methods have no remaining callers (e.g. AstronomyApi).
# Conditional library rules alone can lose them after method shrinking.
-keep,allowobfuscation interface com.david.carpcast.network.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
