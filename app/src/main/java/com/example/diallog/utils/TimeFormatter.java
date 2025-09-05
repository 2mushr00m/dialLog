package com.example.diallog.utils;

import java.util.Locale;

public final class TimeFormatter {

    public static String toMmSs(long ms) {
        long totalSec = ms / 1000;
        long mm = totalSec / 60;
        long ss = totalSec % 60;
        return String.format(Locale.KOREA, "%02d:%02d", mm, ss);
    }
}
