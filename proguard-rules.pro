# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in {SDKHOME}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
-keep class org.kde.kdeconnect.** {*;}

# SSHd requires mina, and mina uses reflection so some classes would get deleted
-keep class org.apache.sshd.** {*;}
-dontwarn org.apache.sshd.**

# The android-smsmms library uses class casting and reflection internally.
# R8 optimization breaks PduPersister, causing SMSPlugin.onCreate() to crash.
-keep class com.google.android.mms.** {*;}

