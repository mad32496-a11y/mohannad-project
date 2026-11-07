// Versions checked against public release notes (AGP 8.7.3 needs Gradle 8.9 and JDK 17; KSP 2.0.21-1.0.28 matches Kotlin 2.0.21).
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
