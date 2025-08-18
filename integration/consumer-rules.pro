# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep mesh integration plugin interface and implementation
-keep class org.torproject.android.meshrabiya.interfaces.** { *; }
-keep class org.torproject.android.meshrabiya.plugin.** { *; }

# Keep mesh UI components that might be used by reflection
-keep class org.torproject.android.ui.MeshFragment { *; }
-keep class org.torproject.android.ui.MeshViewModel { *; }

# Keep mesh service
-keep class org.torproject.android.service.MeshrabiyaService { *; }
