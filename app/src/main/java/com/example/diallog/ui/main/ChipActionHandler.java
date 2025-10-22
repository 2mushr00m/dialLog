package com.example.diallog.ui.main;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.diallog.data.model.Transcript;
import com.example.diallog.ui.adapter.TranscriptAdapter;

import java.util.ArrayList;
import java.util.List;

public class ChipActionHandler {

    static final int REQ_CALENDAR = 2101;
    static final int REQ_CONTACTS = 2102;

    private final Activity act;
    private Pending pending = null;

    ChipActionHandler(@NonNull Activity act){ this.act = act; }

    public void handle(@NonNull Transcript t, @NonNull TranscriptAdapter.ChipSpec spec){
        switch (spec.id) {
            case "schedule": {
                String[] need = missingPerms(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);
                if (need.length > 0) { askOrOpenSettings(need, REQ_CALENDAR, t, spec); return; }
                launchCalendar(t, spec); return;
            }
            case "contact": {
                String[] need = missingPerms(Manifest.permission.WRITE_CONTACTS);
                if (need.length > 0) { askOrOpenSettings(need, REQ_CONTACTS, t, spec); return; }
                launchContact(t, spec); return;
            }
            case "alarm": {
                launchAlarm(t, spec); return;
            }
            default: return;
        }
    }


    private void launchCalendar(Transcript t, TranscriptAdapter.ChipSpec spec){
        Intent ins = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
        if (spec.extras != null) {
            ins.putExtra(CalendarContract.Events.TITLE, spec.extras.getString("title","일정"));
            long begin = spec.extras.getLong("beginUtc", -1);
            long end   = spec.extras.getLong("endUtc",   -1);
            if (begin > 0) ins.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
            if (end   > 0) ins.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
            String loc = spec.extras.getString("location");
            if (loc != null) ins.putExtra(CalendarContract.Events.EVENT_LOCATION, loc);
        } else {
            ins.putExtra(CalendarContract.Events.TITLE, safeTitle(t));
        }
        if (tryStart(ins)) return;

        Intent edit = new Intent(Intent.ACTION_EDIT).setData(CalendarContract.Events.CONTENT_URI);
        edit.putExtras(ins);                         // 동일 extras 재사용
        if (tryStart(edit)) return;

        // 선택적 패키지 폴백(있으면 시도)
        if (tryStartPkg(ins,  "com.google.android.calendar")) return;
        if (tryStartPkg(edit, "com.google.android.calendar")) return;

        Toast.makeText(act, "캘린더 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
    }

    // 3) 연락처: SHOW_OR_CREATE_CONTACT(전화번호 있으면) → INSERT → 패키지 폴백
    private void launchContact(Transcript t, TranscriptAdapter.ChipSpec spec){
        String name  = null, phone = null, email = null, note = safeTitle(t);
        if (spec.extras != null) {
            name  = spec.extras.getString("name");
            phone = spec.extras.getString("phone");
            email = spec.extras.getString("email");
            note  = spec.extras.getString("note", note);
        }

        if (phone != null && !phone.isEmpty()) {
            Intent showOrCreate = new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT,
                    Uri.parse("tel:" + phone));
            if (name  != null) showOrCreate.putExtra(ContactsContract.Intents.Insert.NAME,  name);
            if (email != null) showOrCreate.putExtra(ContactsContract.Intents.Insert.EMAIL, email);
            showOrCreate.putExtra(ContactsContract.Intents.Insert.NOTES, note);
            if (tryStart(showOrCreate)) return;
        }

        Intent ins = new Intent(Intent.ACTION_INSERT)
                .setType(ContactsContract.Contacts.CONTENT_TYPE);
        putIf(ins, ContactsContract.Intents.Insert.NAME,  name);
        putIf(ins, ContactsContract.Intents.Insert.PHONE, phone);
        putIf(ins, ContactsContract.Intents.Insert.EMAIL, email);
        putIf(ins, ContactsContract.Intents.Insert.NOTES, note);
        if (tryStart(ins)) return;

        // 선택적 패키지 폴백
        if (tryStartPkg(ins, "com.google.android.contacts")) return;
        if (tryStartPkg(ins, "com.android.contacts")) return;

        Toast.makeText(act, "연락처 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
    }

    // 4) 알람: 암시 → 패키지 폴백 → SHOW_ALARMS
    private void launchAlarm(Transcript t, TranscriptAdapter.ChipSpec spec){
        int hour = 7, minute = 0;
        String label = safeTitle(t);
        if (spec.extras != null) {
            if (spec.extras.containsKey("hour"))   hour   = Math.min(23, Math.max(0, spec.extras.getInt("hour")));
            if (spec.extras.containsKey("minute")) minute = Math.min(59, Math.max(0, spec.extras.getInt("minute")));
            label = spec.extras.getString("label", label);
        }
        Intent set = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label);

        if (tryStart(set)) return;
        if (tryStartPkg(set, "com.google.android.deskclock")) return;
        if (tryStartPkg(set, "com.android.deskclock")) return;

        Intent show = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        if (tryStart(show)) return;

        Toast.makeText(act, "알람을 처리할 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
    }


    private void askOrOpenSettings(@NonNull String[] need, int reqCode,
                                   @NonNull Transcript t, @NonNull TranscriptAdapter.ChipSpec spec){
        // 요청할 게 없으면 즉시 실행
        if (need.length == 0) { handle(t, spec); return; }

        // rationale 여부 검사
        boolean anyRationale = false;
        for (String p : need) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(act, p)) { anyRationale = true; break; }
        }

        // 올바른 분기
        if (!anyRationale) {
            // 최초 요청 또는 '다시 묻지 않음'이 아님 → 바로 요청
            pending = new Pending(t, new TranscriptAdapter.ChipSpec(spec.id, spec.label,
                    spec.extras==null? null : new Bundle(spec.extras)));
            ActivityCompat.requestPermissions(act, need, reqCode);
        } else {
            // 사용자가 한 번 거절했고 설명이 필요한 상태 → 설명 UI 띄운 뒤 요청
            new AlertDialog.Builder(act)
                    .setTitle("권한이 필요합니다")
                    .setMessage("기능을 사용하려면 권한을 허용해 주세요.")
                    .setPositiveButton("허용 요청", (d,w) -> {
                        pending = new Pending(t, new TranscriptAdapter.ChipSpec(spec.id, spec.label,
                                spec.extras==null? null : new Bundle(spec.extras)));
                        ActivityCompat.requestPermissions(act, need, reqCode);
                    })
                    .setNegativeButton("취소", null)
                    .show();
        }
    }

    public void onRequestPermissionsResult(int reqCode, @NonNull String[] perms, @NonNull int[] results){
        if (pending == null) return;
        boolean allGranted = true;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
        Pending p = pending; pending = null;
        if (allGranted) handle(p.t, p.spec);
    }

    // ===== helpers =====
    private String[] missingPerms(String... perms){
        List<String> need = new ArrayList<>();
        for (String p : perms)
            if (ContextCompat.checkSelfPermission(act, p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        return need.toArray(new String[0]);
    }
    private void request(@NonNull String[] perms, int reqCode,
                         @NonNull Transcript t, @NonNull TranscriptAdapter.ChipSpec spec){
        if (perms.length == 0) { handle(t, spec); return; }
        android.os.Bundle copy = spec.extras == null ? null : new android.os.Bundle(spec.extras);
        pending = new Pending(t, new TranscriptAdapter.ChipSpec(spec.id, spec.label, copy));
        ActivityCompat.requestPermissions(act, perms, reqCode);
    }

    private boolean tryStart(@Nullable Intent i){
        if (i == null) return false;
        if (!(act instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int handlers = act.getPackageManager()
                .queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).size();
        Log.d("Chip", "act=" + i.getAction() + ", data=" + i.getData() + ", handlers=" + handlers);
        if (handlers == 0) return false;
        try { act.startActivity(i); return true; }
        catch (Exception e){ Log.w("Chip","start fail: "+e.getClass().getSimpleName()+" - "+e.getMessage()); return false; }
    }
    private boolean tryStartPkg(Intent base, String pkg){
        Intent i = new Intent(base).setPackage(pkg);
        return tryStart(i);
    }

    private void safeStart(Intent i){
        try {
            if (i.resolveActivity(act.getPackageManager()) != null) {
                act.startActivity(i);
            } else {
                Toast.makeText(act, "실행할 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(act, "권한이 없어 실행할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void putIf(Intent i, String k, String v){ if (v != null && !v.isEmpty()) i.putExtra(k, v); }
    private static String safeTitle(Transcript t){
        String s = t.text == null ? "" : t.text.trim();
        return s.isEmpty() ? "제안됨" : (s.length() > 40 ? s.substring(0, 40) : s);
    }

    private static final class Pending {
        final Transcript t;
        final TranscriptAdapter.ChipSpec spec;
        Pending(Transcript t, TranscriptAdapter.ChipSpec spec){ this.t = t; this.spec = spec; }
    }
}