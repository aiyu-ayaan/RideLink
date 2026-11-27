# RideLink release rules. Hilt, Compose and AndroidX ship their own consumer rules; add app rules here.

# Credential Manager finds its Play Services provider by reflection (androidx.credentials docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# WorkManager (cloud backup) builds its Room database by reflection on the generated _Impl's no-arg constructor;
# R8 full mode strips it and the app crashes at startup (NoSuchMethodException: WorkDatabase_Impl.<init>).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
