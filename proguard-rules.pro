# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in {SDKHOME}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
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

# Allow obfuscation of android.support.v7.internal.view.menu.**
# to avoid problem on Samsung 4.2.2 devices with appcompat v21
# see https://code.google.com/p/android/issues/detail?id=78377
-keepnames class !android.support.v7.internal.view.menu.**,android.support.v7.** {*;}

-dontwarn org.spongycastle.**
-dontwarn org.apache.sshd.**
-dontwarn org.apache.mina.**
-dontwarn org.slf4j.**
-dontwarn io.netty.**

-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*

-keep class org.spongycastle.** {*;}

# SSHd requires mina, and mina uses reflection so some classes would get deleted
-keep class org.apache.mina.** {*;}
-keep class org.apache.sshd.** {*;}

-keep class org.kde.kdeconnect.** {*;}

-dontwarn org.mockito.**
-dontwarn sun.reflect.**
-dontwarn android.test.**
-dontwarn java.lang.management.**
-dontwarn javax.**
