# Preserves specific fields used by reflection in PlatformContext.android.kt
# These are internal/private implementations of the Font interface in Compose UI
# We need to access 'resId' and 'path' to extract font data for the native engine

# For ResourceFont (usually androidx.compose.ui.text.font.ResourceFont)
-keepclassmembers class * implements androidx.compose.ui.text.font.Font {
    int resId;
}

# For AndroidFont (usually androidx.compose.ui.text.font.AndroidFont)
-keepclassmembers class * implements androidx.compose.ui.text.font.Font {
    java.lang.String path;
}
