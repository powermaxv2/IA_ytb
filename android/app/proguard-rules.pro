# yt-dlp / FFmpeg bridge (youtubedl-android): reflection via Jackson and Commons Compress.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**

# commonmark (Markdown → HTML): loads its HTML entity table as a Java resource by absolute path
# (/org/commonmark/internal/util/entities.txt); keep the class that reads it under its own name.
-keep class org.commonmark.internal.util.Html5Entities { *; }
