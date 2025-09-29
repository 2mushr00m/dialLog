package com.example.diallog.ui.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.data.model.CallRecord;
import com.example.diallog.data.model.FileSection;
import com.example.diallog.data.repository.CallRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainVM";

    private final MutableLiveData<List<FileSection>> sections = new MutableLiveData<>(Collections.emptyList());
    public LiveData<List<FileSection>> getSections() { return sections; }
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final CallRepository repo;

    private static final int PAGE_SIZE = 20;
    private int offset = 0;
    private boolean loading = false;
    private boolean endReached = false;
    private String error = null;

    private final List<CallRecord> all = new ArrayList<>();
    private String query = "";

    public MainViewModel(@NonNull Application app, CallRepository repo) {
        super(app);
        this.repo = repo;
    }

    public void start() { repo.ensureScanned(); loadFirstPage(); }

    public void setFolderTreeUri(@Nullable Uri treeUriOrNull) {
        repo.setTreeUri(treeUriOrNull);
        loadFirstPage();
    }

    public void loadFirstPage() {
        if (loading) return;
        loading = true;
        offset = 0;
        endReached = false;
        all.clear();
        io.submit(() -> {
            List<CallRecord> page = repo.getRecent(offset, PAGE_SIZE);
            if (page == null) page = Collections.emptyList();
            all.addAll(page);
            endReached = page.size() < PAGE_SIZE;
            offset += page.size();
            postSectionsFiltered();
            loading = false;
        });
    }

    @MainThread
    public void loadMore(RecyclerView.LayoutManager lm){
        if (loading || endReached || !(lm instanceof LinearLayoutManager)) return;
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        int last = llm.findLastVisibleItemPosition();
        int total = llm.getItemCount();
        if (total == 0 || last < total - 6) return;
        loading = true;
        io.submit(() -> {
            List<CallRecord> page = repo.getRecent(offset, PAGE_SIZE);
            if (page == null) page = Collections.emptyList();
            all.addAll(page);
            endReached = page.size() < PAGE_SIZE;
            offset += page.size();
            postSectionsFiltered();
            loading = false;
        });
    }


    public void setQuery(String q) {
        if (q == null) q = "";
        if (q.equals(query)) return;
        query = q.trim();
        io.submit(this::postSectionsFiltered);
    }

    private void postReload() { loadFirstPage(); }
    private void postSectionsFiltered() {
        List<CallRecord> src = all;
        if (!query.isEmpty()) {
            String q = query.toLowerCase(Locale.ROOT);
            List<CallRecord> filtered = new ArrayList<>();
            for (CallRecord cr : src) {
                String title = cr.fileName != null ? cr.fileName : "";
                String dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(cr.startedAtEpochMs);
                if (title.toLowerCase(Locale.ROOT).contains(q) || dateStr.contains(q)) {
                    filtered.add(cr);
                }
            }
            sections.postValue(groupByDay(filtered));
        } else {
            sections.postValue(groupByDay(src));
        }
    }

    private List<FileSection> groupByDay(List<CallRecord> list) {
        Map<String, List<CallRecord>> buckets = new LinkedHashMap<>();
        for (CallRecord cr : list) {

            Calendar c = Calendar.getInstance();
            Calendar now = Calendar.getInstance();
            Calendar yesterday = (Calendar) now.clone(); yesterday.add(Calendar.DAY_OF_YEAR, -1);
            c.setTimeInMillis(cr.startedAtEpochMs);


            String header;
            if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR))
                header = getApplication().getString(R.string.label_today);
            else if (yesterday.get(Calendar.YEAR) == c.get(Calendar.YEAR) && yesterday.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR))
                header = getApplication().getString(R.string.label_yesterday);
            else
                header = getApplication().getString(R.string.label_date_month_day, c.get(Calendar.DAY_OF_MONTH) + 1, c.get(Calendar.DATE));
            buckets.computeIfAbsent(header, k -> new ArrayList<>()).add(cr);
        }
        List<FileSection> out = new ArrayList<>();
        for (Map.Entry<String, List<CallRecord>> e : buckets.entrySet()) {
            out.add(new FileSection(e.getKey(), e.getValue()));
        }
        return out;
    }

}
