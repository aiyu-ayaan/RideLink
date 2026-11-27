# RideLink release rules. Hilt, Compose and AndroidX ship their own consumer rules; add app rules here.

# Credential Manager finds its Play Services provider by reflection (androidx.credentials docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
