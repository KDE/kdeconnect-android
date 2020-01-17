package org.kde.kdeconnect.Helpers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public class VideoUrlsHelper {
    private static final int SECONDS_IN_MINUTE = 60;
    private static final int MINUTES_IN_HOUR = 60;
    private static final int SECONDS_IN_HOUR = SECONDS_IN_MINUTE * MINUTES_IN_HOUR;

    public static URL formatUriWithSeek(String address, long position)
            throws MalformedURLException {
        URL url = new URL(address);
        position /= 1000; // Convert ms to seconds
        if (position <= 0) {
            return url; // nothing to do
        }
        String host = url.getHost().toLowerCase();

        // Most common settings as defaults:
        String parameter = "t="; // Characters before timestamp
        String timestamp = Long.toString(position); // Timestamp itself
        String trailer = ""; // Characters after timestamp
        // true  - search/add to query URL part (between ? and # signs),
        // false - search/add timestamp to ref (anchor) URL part (after # sign),
        boolean inQuery = true;
        // true - We know how to format URL with seek timestamp, false - not
        boolean seekUrl = false;

        // Override defaults if necessary
        if (host.contains("youtube.com")
                || host.contains("youtu.be")
                || host.contains("pornhub.com")) {
            seekUrl = true;
            url = stripTimestampS(url, parameter, trailer, inQuery);
        } else if (host.contains("vimeo.com")) {
            seekUrl = true;
            trailer = "s";
            url = stripTimestampS(url, parameter, trailer, inQuery);
        } else if (host.contains("dailymotion.com")) {
            seekUrl = true;
            parameter = "start=";
            url = stripTimestampS(url, parameter, trailer, inQuery);
        } else if (host.contains("twitch.tv")) {
            seekUrl = true;
            timestamp = formatTimestampHMS(position, true);
            url = stripTimestampHMS(url, parameter, trailer, inQuery);
        }

        if (seekUrl) {
            url = formatUrlWithSeek(url, timestamp, parameter, trailer, inQuery);
        }
        return url;
    }

    // Returns timestamp in 1h2m34s or 01h02m34s (according to padWithZeroes)
    private static String formatTimestampHMS(long seconds, boolean padWithZeroes) {
        if (seconds == 0) {
            return "0s";
        }

        int sec = (int) (seconds % SECONDS_IN_MINUTE);
        int min = (int) ((seconds / SECONDS_IN_MINUTE) % MINUTES_IN_HOUR);
        int hour = (int) (seconds / SECONDS_IN_HOUR);

        String hours = hour > 0 ? hour + "h" : "";
        String mins = min > 0 || hour > 0 ? min + "m" : "";
        String secs = sec + "s";

        String value;
        if (padWithZeroes) {
            String hoursPad = hour > 9 ? "" : "0";
            String minsPad = min > 9 ? "" : "0";
            String secsPad = sec > 9 ? "" : "0";
            value = hoursPad + hours + minsPad + mins + secsPad + secs;
        } else {
            value = hours + mins + secs;
        }
        return value;

    }

    // Remove timestamp in 01h02m34s or 1h2m34s or 02m34s or 2m34s or 01s or 1s format.
    // Can also nandle rimestamps in 1234s format if called with 's' trailer
    private static URL stripTimestampHMS(URL url, String parameter, String trailer, boolean inQuery)
            throws MalformedURLException {
        String regex = parameter + "([\\d]+[hH])?([\\d]+[mM])?[\\d]+[sS]" + trailer + "&?";
        return stripTimestampCommon(url, inQuery, regex);
    }


    // Remove timestamp in 1234 format
    private static URL stripTimestampS(URL url, String parameter, String trailer, boolean inQuery)
            throws MalformedURLException {
        String regex = parameter + "[\\d]+" + trailer + "&?";
        return stripTimestampCommon(url, inQuery, regex);
    }

    private static URL stripTimestampCommon(URL url, boolean inQuery, String regex)
            throws MalformedURLException {
        String value;
        if (inQuery) {
            value = url.getQuery();
        } else {
            value = url.getRef();
        }
        if (value == null) {
            return url;
        }
        String newValue = value.replaceAll(regex, "");
        String replaced = url.toString().replaceFirst(value, newValue);
        if (inQuery && replaced.endsWith("&")) {
            replaced = replaced.substring(0, replaced.length() - 1);
        }
        return new URL(replaced);
    }

    private static URL formatUrlWithSeek(URL url, String position, String parameter, String trailer,
                                         boolean inQuery) throws MalformedURLException {
        String value;
        String separator;
        String newValue;
        if (inQuery) {
            value = url.getQuery();
            separator = "?";
        } else {
            value = url.getRef();
            separator = "#";
        }
        if (value == null) {
            newValue = String.format(Locale.getDefault(), "%s%s%s%s%s",
                    url.toString(), separator, parameter, position, trailer);
            return new URL(newValue);
        }
        if (inQuery) {
            newValue = String.format(Locale.getDefault(), "%s&%s%s%s",
                    value, parameter, position, trailer);
        } else {
            newValue = String.format(Locale.getDefault(), "%s%s%s",
                    parameter, position, trailer);
        }
        return new URL(url.toString().replaceFirst(value, newValue));
    }
}
