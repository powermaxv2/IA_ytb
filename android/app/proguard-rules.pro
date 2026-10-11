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

# PdfBox-Android (Assembleur PDF): its own rules keep the security handlers it loads by reflection. The JPEG 2000
# decoder is an optional extra library Gulp does not ship.
-dontwarn com.gemalto.jp2.**
