package com.example.diallog.ui.viewmodel;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.data.model.CallRecord;
import com.example.diallog.data.repository.CallRepository;
import com.example.diallog.data.repository.Transcriber;
import com.example.diallog.data.repository.cache.TranscriptCache;
import com.google.android.datatransport.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainVM";

    private final CallRepository repo;
    private final TranscriptCache tcache;
    private final Transcriber transcriber;

    private final MutableLiveData<List<CallRecord>> records = new MutableLiveData<>();
    private final MutableLiveData<Set<Uri>> transcribing = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Event<Uri>> sttCompleted = new MutableLiveData<>();

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final int PAGE_SIZE = 20;
    private int offset = 0;
    private boolean loading = false;
    private boolean endReached = false;

    private final List<CallRecord> all = new ArrayList<>();
    private String query = "";

    public MainViewModel(@NonNull Application app,
                         @NonNull CallRepository repo,
                         @NonNull TranscriptCache tcache,
                         @NonNull Transcriber transcriber) {
        super(app);
        this.repo = repo;
        this.tcache = tcache;
        this.transcriber = transcriber;
    }

    public LiveData<List<CallRecord>> records()      { return records; }
    public LiveData<Set<Uri>> transcribing()         { return transcribing; }


    /** 초기 진입: 저장소 스캔 보장 후 첫 페이지 로드 */
    public void start() {
        repo.ensureScanned();
        loadFirstPage();
    }
    /** 트리 URI 변경 시 재로딩 */
    public void setFolderTreeUri(@Nullable Uri treeUriOrNull) {
        repo.setTreeUri(treeUriOrNull);
        loadFirstPage();
    }

    /** 검색어 설정. 파일명 또는 일자 */
    public void setQuery(@Nullable String q) {
        String next = q == null ? "" : q.trim();
        if (next.equals(query)) return;
        query = next;
        io.submit(() -> {
            List<CallRecord> filtered = filter(all, query);
            records.postValue(filtered);
        });
    }

    /** 캐시 존재 여부 */
    public boolean hasCache(@NonNull Uri uri) { return tcache.has(uri); }
    /** STT 전환 진행 여부 */
    public boolean isTranscribing(@NonNull Uri uri) {
        Set<Uri> s = transcribing.getValue();
        return s != null && s.contains(uri);
    }


    /** 첫 페이지 리셋 로딩 */
    @MainThread
    public void loadFirstPage() {
        if (loading) return;
        loading = true;
        offset = 0;
        endReached = false;
        all.clear();

        io.submit(() -> {
            List<CallRecord> page = safeList(repo.getRecent(0, PAGE_SIZE));
            List<CallRecord> patched = applyCacheMeta(page);
            all.addAll(patched);
            offset += page.size();
            endReached = page.size() < PAGE_SIZE;

            List<CallRecord> out = filter(all, query);
            main.post(() -> {
                records.setValue(out);
                loading = false;
            });
        });
    }
    /** 스크롤 하단 근접 시 다음 페이지 로딩 */
    @MainThread
    public void loadMore(@NonNull RecyclerView.LayoutManager lm) {
        if (loading || endReached || !(lm instanceof LinearLayoutManager)) return;
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        int last = llm.findLastVisibleItemPosition();
        int total = llm.getItemCount();
        if (total == 0 || last < total - 6) return;

        loading = true;
        io.submit(() -> {
            List<CallRecord> page = safeList(repo.getRecent(offset, PAGE_SIZE));
            List<CallRecord> patched = applyCacheMeta(page);
            all.addAll(patched);
            offset += page.size();
            endReached = page.size() < PAGE_SIZE;

            List<CallRecord> out = filter(all, query);
            main.post(() -> {
                records.setValue(out);
                loading = false;
            });
        });
    }



    /**
     * 미-STT 항목 클릭 시 호출. 진행 상태 등록 → STT 실행 → 캐시 저장(Transcriber 내부/외부) →
     * 요약 재조회 → 해당 아이템만 갱신 → 진행 상태 해제
     */
    @MainThread
    public void startStt(@NonNull CallRecord cr) {
        final Uri uri = cr.uri;
        if (hasCache(uri) || isTranscribing(uri)) return;

        // 진행 상태 등록
        Set<Uri> cur = new HashSet<>(transcribing.getValue());
        cur.add(uri);
        transcribing.setValue(cur);

        io.submit(() -> {
            try {
                // 1) STT 실행
                transcriber.transcribe(uri);

                // 2) 캐시 메타 확인
                final String latestSummary = tcache.getSummary(uri);
                final TranscriptCache.Meta latestMeta = tcache.peekMeta(uri);

                main.post(() -> {
                    List<CallRecord> curList = records.getValue();
                    if (curList != null && !curList.isEmpty()) {
                        List<CallRecord> patched = new ArrayList<>(curList.size());
                        for (CallRecord it : curList) {
                            if (it.uri.equals(uri)) {
                                CallRecord copy = copyOf(it);
                                if (!TextUtils.isEmpty(latestSummary)) copy.summary = latestSummary;
                                if (latestMeta != null && copy.durationMs <= 0 && latestMeta.durationMs > 0) {
                                    copy.durationMs = latestMeta.durationMs;
                                }
                                patched.add(copy);
                            } else {
                                patched.add(it);
                            }
                        }
                        records.setValue(patched);
                    }

                    // 진행 상태 해제
                    Set<Uri> done = new HashSet<>(transcribing.getValue());
                    done.remove(uri);
                    transcribing.setValue(done);
                });
            } catch (Exception e) {
                Log.w(TAG, "stt failed: " + uri + ", cause=" + e.getMessage(), e);
                main.post(() -> {
                    Set<Uri> done = new HashSet<>(transcribing.getValue());
                    done.remove(uri);
                    transcribing.setValue(done);
                });
            }
        });
    }



    /** 페이지에서 받아온 항목들에 캐시 메타(summary/duration) 보강 */
    private List<CallRecord> applyCacheMeta(@NonNull List<CallRecord> page) {
        List<CallRecord> out = new ArrayList<>(page.size());
        for (CallRecord cr : page) {
            String s = TextUtils.isEmpty(cr.summary) ? tcache.getSummary(cr.uri) : cr.summary;
            if (!TextUtils.isEmpty(s)) {
                TranscriptCache.Meta m = tcache.peekMeta(cr.uri);
                CallRecord copy = copyOf(cr);
                copy.summary = s;
                if (copy.durationMs <= 0 && m != null && m.durationMs > 0) copy.durationMs = m.durationMs;
                out.add(copy);
            } else {
                out.add(cr);
            }
        }
        return out;
    }

    /** 검색어 필터링: 파일명 또는 yyyy.MM.dd 포함 여부 */
    private List<CallRecord> filter(@NonNull List<CallRecord> src, @NonNull String q) {
        if (q.isEmpty()) return new ArrayList<>(src);
        String needle = q.toLowerCase(Locale.ROOT);
        SimpleDateFormat df = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);

        List<CallRecord> out = new ArrayList<>();
        for (CallRecord cr : src) {
            String name = cr.fileName != null ? cr.fileName : "";
            String dateStr = df.format(cr.startedAtEpochMs);
            if (name.toLowerCase(Locale.ROOT).contains(needle) || dateStr.contains(needle)) {
                out.add(cr);
            }
        }
        return out;
    }

    private static <T> List<T> safeList(@Nullable List<T> in) {
        return in == null ? Collections.emptyList() : in;
    }

    private static CallRecord copyOf(@NonNull CallRecord src) {
        CallRecord dst = new CallRecord(src.uri);
        dst.fileName = src.fileName;
        dst.durationMs = src.durationMs;
        dst.startedAtEpochMs = src.startedAtEpochMs;
        dst.summary = src.summary;
        // 다른 필드가 있다면 여기에 추가 복제
        return dst;
    }

}
