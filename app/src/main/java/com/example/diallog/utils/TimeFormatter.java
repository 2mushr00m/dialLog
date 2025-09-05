package com.example.diallog.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class TimeFormatter {
    private static final ThreadLocal<SimpleDateFormat> DF = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return f;
    });
    public static String toYmdHm(long utcMs){
        return DF.get().format(new Date(utcMs));
    }

    public static String toMmSs(long ms) {
        long totalSec = ms / 1000;
        long mm = totalSec / 60;
        long ss = totalSec % 60;
        return String.format(Locale.KOREA, "%02d:%02d", mm, ss);
    }
}
