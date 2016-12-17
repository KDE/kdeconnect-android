package org.kde.kdeconnect.Helpers;

import java.nio.charset.Charset;

public class StringsHelper {

    public static final Charset UTF8 = Charset.forName("UTF-8");

    public static int compare(String a, String b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareToIgnoreCase(b);
    }

}
