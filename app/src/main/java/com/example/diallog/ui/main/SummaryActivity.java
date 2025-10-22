package com.example.diallog.ui.main;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.config.AppConfig;
import com.example.diallog.data.model.Transcript;
import com.example.diallog.ui.adapter.TranscriptAdapter;
import com.example.diallog.ui.viewmodel.SummaryVMFactory;
import com.example.diallog.ui.viewmodel.SummaryViewModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SummaryActivity extends AppCompatActivity {
    public static final String EXTRA_URI = "audioUri";

    private SummaryViewModel vm;
    private RecyclerView rv;
    private TranscriptAdapter adapter;
    private ChipActionHandler chipHandler;

    private ImageButton btnPlay, btnRew, btnFwd;
    private ProgressBar slider;
    private TextView tvElapsed, tvDuration;

    private MediaPlayer player;
    private boolean prepared = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final int STEP_MS = 10_000;

    @Override protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        AppConfig.get().setIntentOverride(getIntent());

        setContentView(R.layout.activity_summary);
        setSupportActionBar(findViewById(R.id.toolbar));

        rv = findViewById(R.id.rv_sections);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);

        btnPlay   = findViewById(R.id.btnPlay);
        btnRew    = findViewById(R.id.btn_rew);
        btnFwd    = findViewById(R.id.btn_fwd);
        slider    = findViewById(R.id.slider);
        tvElapsed = findViewById(R.id.label_elapsed);
        tvDuration= findViewById(R.id.label_tv_duration);

        TranscriptAdapter.ChipProvider mock = t -> {
            List<TranscriptAdapter.ChipSpec> out = new ArrayList<>();

            // 1) 캘린더: 지금+1시간, 30분짜리
            long now = System.currentTimeMillis() + 60*60*1000L;
            Bundle cal = new Bundle();
            cal.putString("title", "통화 메모: 미팅");
            cal.putLong("beginUtc", now);
            cal.putLong("endUtc",   now + 30*60*1000L);
            cal.putString("location", "온라인");
            out.add(new TranscriptAdapter.ChipSpec("schedule", "캘린더(모의)", cal));

            // 2) 알람: 07:30
            Bundle alarm = new Bundle();
            alarm.putInt("hour", 7);
            alarm.putInt("minute", 30);
            alarm.putString("label", "통화 후속");
            out.add(new TranscriptAdapter.ChipSpec("alarm", "알람(모의)", alarm));

            // 3) 연락처: 홍길동 010-1234-5678
            Bundle contact = new Bundle();
            contact.putString("name", "홍길동");
            contact.putString("phone", "010-1234-5678");
            contact.putString("note",  t.text == null ? "" : t.text);
            out.add(new TranscriptAdapter.ChipSpec("contact", "연락처(모의)", contact));

            return out;
        };
        vm = new ViewModelProvider(this, new SummaryVMFactory(getApplication()))
                .get(SummaryViewModel.class);
        chipHandler = new ChipActionHandler(this);
        adapter = new TranscriptAdapter(
                (Transcript t) -> seekAndAutoPlay(t.startMs),
                (t, spec) -> chipHandler.handle(t, spec),
                mock
//                vm.chipProvider()
        );
        adapter.setAutoTextContrast(true);      // 화자별 색상 표시
        // adapter.setPalette(...); // 커스텀 팔레트가 있으면 주입
        rv.setAdapter(adapter);

        vm.segments().observe(this, segs -> {
            adapter.submitTranscripts(segs);
        });
        vm.loading().observe(this, loading -> {
            // 필요 시 Progress UI 갱신
        });
        vm.error().observe(this, e -> {
            if (e != null && !e.isEmpty()) {
                Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
            }
        });

        Uri audioUri = getIntent().getParcelableExtra(EXTRA_URI);
        if (audioUri == null) audioUri = getIntent().getData();

        if (audioUri != null) {
            vm.load(audioUri);
            initPlayer(audioUri);
            wireControls();
        } else {
            Toast.makeText(this, "오디오 없음", Toast.LENGTH_SHORT).show();
            // Toast.makeText(this, R.string.error_no_audio_uri, Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
            btnPlay.setImageResource(R.drawable.ic_play);
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        releasePlayer();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (chipHandler != null) chipHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null && prepared) {
                int pos = player.getCurrentPosition();
                int dur = Math.max(player.getDuration(), 1);
                slider.setMax(dur);
                slider.setProgress(pos);
                tvElapsed.setText(fmt(pos));
                tvDuration.setText(fmt(dur));
                if (player.isPlaying()) ui.postDelayed(this, 500);
            }
        }
    };


    private void wireControls() {
        btnPlay.setOnClickListener(v -> {
            if (!prepared || player == null) return;
            if (player.isPlaying()) {
                player.pause();
                btnPlay.setImageResource(R.drawable.ic_play);
            } else {
                player.start();
                btnPlay.setImageResource(R.drawable.ic_pause);
                ui.post(tick);
            }
        });
        btnRew.setOnClickListener(v -> seekBy(-STEP_MS));
        btnFwd.setOnClickListener(v -> seekBy(+STEP_MS));
    }

    private void initPlayer(Uri uri) {
        releasePlayer();
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            // SAF/MediaStore Uri 직접 사용
            player.setDataSource(this, uri);
            setControlsEnabled(false);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                setControlsEnabled(true);
                // 초기 UI
                int dur = Math.max(player.getDuration(), 1);
                slider.setMax(dur);
                slider.setProgress(0);
                tvElapsed.setText(fmt(0));
                tvDuration.setText(fmt(dur));
            });
            player.setOnCompletionListener(mp -> {
                btnPlay.setImageResource(R.drawable.ic_play);
                int dur = Math.max(player.getDuration(), 1);
                slider.setProgress(dur);
                tvElapsed.setText(fmt(dur));
            });
            player.setOnErrorListener((mp, what, extra) -> {
                prepared = false;
                setControlsEnabled(false);
                Toast.makeText(this, "재생 오류", Toast.LENGTH_SHORT).show();
                return true;
            });
            player.prepareAsync();
        } catch (IOException e) {
            prepared = false;
            setControlsEnabled(false);
            Toast.makeText(this, "오디오 열기 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void seekBy(int deltaMs) {
        if (!prepared || player == null) return;
        int dur = Math.max(player.getDuration(), 0);
        int to = clamp(player.getCurrentPosition() + deltaMs, 0, dur);
        player.seekTo(to);
        slider.setProgress(to);
        tvElapsed.setText(fmt(to));
    }
    private void seekAndAutoPlay(long ms) {
        if (!prepared || player == null) return;
        int dur = Math.max(player.getDuration(), 0);
        int to = (int) Math.max(0L, Math.min(ms, (long) dur));
        player.seekTo(to);
        slider.setProgress(to);
        tvElapsed.setText(fmt(to));
        if (!player.isPlaying()) {
            player.start();
            btnPlay.setImageResource(R.drawable.ic_pause);
            ui.post(tick);
        }
    }

    private void setControlsEnabled(boolean enabled) {
        btnPlay.setEnabled(enabled);
        btnRew.setEnabled(enabled);
        btnFwd.setEnabled(enabled);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String fmt(int ms) {
        int totalSec = Math.max(0, ms / 1000);
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format(Locale.KOREA, "%d:%02d", m, s);
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            try { player.reset(); player.release(); } catch (Throwable ignore) {}
            player = null;
        }
    }

}
