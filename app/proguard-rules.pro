# Keep MediaPipe LLM inference classes (reflection-heavy)
-keep class com.google.mediapipe.** { *; }

# Keep Room database entity classes
-keep class com.sophia.ops.data.entities.** { *; }

# Keep Room DAO interfaces
-keep class com.sophia.ops.data.dao.** { *; }

# Keep AI model classes used via reflection
-keep class com.sophia.ops.ai.** { *; }

# Suppress warnings for missing optional classes
-dontwarn com.google.mediapipe.tasks.genai.llminference.**
