-keepattributes Signature,*Annotation*
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn org.apache.commons.compress.archivers.sevenz.**

# PdfBox-Android keeps JPEG 2000 support behind Gemalto's optional JP2 codec.
# Shovel only appends its own lossless signature image and leaves
# existing PDF streams untouched, so this optional decoder/encoder is not part
# of the signing path. Keep every other PdfBox warning visible to R8.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# Shizuku and libsu start these application classes by their binary names in a
# separate privileged process. Renaming or removing either class would make the
# optimized APK report an available backend that can never connect.
-keep class com.shovel.filemanager.advanced.ShizukuFileService { *; }
-keep class com.shovel.filemanager.advanced.RootFileService { *; }
-keep class com.shovel.filemanager.advanced.IPrivilegedFileService$Stub { *; }

# termlib's native bridge resolves these private CellRun and ScreenCell fields
# by their exact JVM names. The library's consumer rules keep only its public
# API, so a minified build otherwise renames the fields and aborts when JNI
# initializes or restores scrollback during a terminal resize.
-keepclassmembers class org.connectbot.terminal.CellRun {
    int fgRed;
    int fgGreen;
    int fgBlue;
    int bgRed;
    int bgGreen;
    int bgBlue;
    boolean bold;
    int underline;
    boolean italic;
    boolean blink;
    boolean reverse;
    boolean strike;
    int font;
    boolean dwl;
    int dhl;
    char[] chars;
    int runLength;
}

-keepclassmembers class org.connectbot.terminal.ScreenCell {
    char char;
    java.util.List combiningChars;
    int fgRed;
    int fgGreen;
    int fgBlue;
    int bgRed;
    int bgGreen;
    int bgBlue;
    boolean bold;
    boolean italic;
    int underline;
    boolean reverse;
    boolean strike;
    int width;
}
