# Keep data classes used in Firestore serialization
-keep class com.syncbudget.app.data.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Compose
-dontwarn androidx.compose.**

# Keep CryptoHelper (JCE reflection)
-keep class com.syncbudget.app.data.CryptoHelper { *; }
