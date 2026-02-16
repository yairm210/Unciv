# To enable ProGuard in your project, edit project.properties
# to define the proguard.config property as described in that file.
#
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the ProGuard
# include property in project.properties.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

-verbose

-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.jnigen.BuildTarget*
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn java.awt.Rectangle

-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* {
   <init>(com.badlogic.gdx.Application, android.content.Context, java.lang.Object, com.badlogic.gdx.backends.android.AndroidApplicationConfiguration);
}

# You will need the next three lines if you use scene2d for UI or gameplay.
# If you don't use scene2d at all, you can remove or comment out the next line:
-keep public class com.badlogic.gdx.scenes.scene2d.** { *; }
# You will need the next two lines if you use BitmapFont or any scene2d.ui text:
-keep public class com.badlogic.gdx.graphics.g2d.BitmapFont { *; }
# You will probably need this line in most cases:
-keep public class com.badlogic.gdx.graphics.Color { *; }
-keep class * implements com.badlogic.gdx.utils.Json$Serializable { *; }

# These are for Kotlin serialization
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# keep annotations
-keepattributes RuntimeVisibleAnnotations

# Keep classes, fields, or methods, annotated with @UsedByReflection
-keep @com.unciv.utils.UsedByReflection class * { *; }
-keep class * {
  @com.unciv.utils.UsedByReflection *;
}

# Keep  @JsonSerialized classes and all members
-keep @com.unciv.utils.JsonSerialized class * { *; }

# Keep unciv Ruleset and game serialization
-keep class * implements com.unciv.models.ruleset.IRulesetObject { *; }
-keep class * implements com.unciv.models.stats.INamed { *; }
-keep class * implements com.unciv.logic.IsPartOfGameInfoSerialization { *; }

# These two lines are used with mapping files; see https://developer.android.com/build/shrink-code#retracing
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

-dontobfuscate #This hides potential bugs, but also makes deobfuscation unnecessary


