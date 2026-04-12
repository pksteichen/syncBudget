# Keep data classes used in Firestore serialization
-keep class com.techadvantage.budgetrak.data.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Compose
-dontwarn androidx.compose.**

# Keep CryptoHelper (JCE reflection)
-keep class com.techadvantage.budgetrak.data.CryptoHelper { *; }

# AndroidX Security (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**
