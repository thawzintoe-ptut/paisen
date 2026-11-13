# R8 for the release build. Libraries bring their own keep rules; only what they leave out is here.

# PaiSeN is GPL-3.0 and its source is public, so renaming buys nothing. Readable names keep
# Play's crash and ANR reports usable without uploading a mapping file.
-dontobfuscate

# Credential Manager finds its Play services provider by reflection (Google sign-in, #187).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
