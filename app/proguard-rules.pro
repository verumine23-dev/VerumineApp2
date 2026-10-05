# Garder les classes principales pour éviter qu'elles ne soient supprimées lors de la compilation Release
-keep class com.farm.miner.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Ignorer les avertissements liés aux bibliothèques système
-dontwarn android.**