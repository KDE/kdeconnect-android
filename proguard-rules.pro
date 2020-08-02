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

-dontwarn android.net.ConnectivityManager
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn android.net.LinkProperties
