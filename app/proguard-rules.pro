# Keep BouncyCastle (used for on-device certificate generation)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# apksig
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**
