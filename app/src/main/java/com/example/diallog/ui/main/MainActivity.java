package com.example.diallog.ui.main;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.config.AppConfig;
import com.example.diallog.data.model.CallRecord;
import com.example.diallog.ui.adapter.CallRecordAdapter;
import com.example.diallog.ui.viewmodel.MainVMFactory;
import com.example.diallog.ui.viewmodel.MainViewModel;
import com.example.diallog.utils.PermissionHelper;

import java.util.Collections;


public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainUI";

    private MainViewModel vm;
    private RecyclerView rv;
    private CallRecordAdapter adapter;


    @Override protected void onCreate(Bundle b) {
        setTheme(R.style.Theme_DialLog);
        super.onCreate(b);
        AppConfig.get().setIntentOverride(getIntent());

        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        if (!PermissionHelper.hasReadAudioPermission(this)) {
            PermissionHelper.requestReadAudio(this);
            return;
        }
        initAfterPermission();
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQ_READ_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) initAfterPermission();
            // 권한 거부 시 별도 처리
        }
    }
    @Override public void onResume() {
        super.onResume();
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) vm.loadMore(recyclerView.getLayoutManager());
            }
        });
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            View actionView = searchItem.getActionView();
            if (actionView instanceof SearchView) {
                SearchView sv = (SearchView) actionView;
                sv.setQueryHint(getString(R.string.search_name_hint));
                sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String query) {
                        vm.setQuery(query == null ? "" : query);
                        return true;
                    }
                    @Override public boolean onQueryTextChange(String newText) {
                        vm.setQuery(newText == null ? "" : newText);
                        return true;
                    }
                });
            }
        }
        return true;
    }


    private void initAfterPermission() {
        rv = findViewById(R.id.rv_sections);
        rv.setLayoutManager(new LinearLayoutManager(this));

        vm = new ViewModelProvider(this, new MainVMFactory(getApplication())).get(MainViewModel.class);
        adapter = new CallRecordAdapter(cr -> {
            Uri uri = cr.uri;
            if (vm.isTranscribing(uri)) return;
            if (vm.hasCache(uri)) {
                startActivity(new Intent(this, SummaryActivity.class).setData(uri));
            } else {
                vm.startStt(cr);
                adapter.setTranscribingUris(vm.transcribing().getValue());
            }},
                (anchor, cr) -> {
                    adapter.setMenuState(cr.uri, true);
                    anchor.setPressed(true);
                    showRecordMenu(anchor, cr);
                });
        rv.setAdapter(adapter);

        vm.records().observe(this, list -> {
            rv.setVisibility(list == null || list.isEmpty() ? View.GONE : View.VISIBLE);
            adapter.submitRecords(list != null ? list : Collections.emptyList());
            adapter.setTranscribingUris(vm.transcribing().getValue());
        });
        vm.transcribing().observe(this, set -> {
            adapter.setTranscribingUris(set);
        });

        vm.start();
    }
    private void showRecordMenu(@NonNull View anchor, @NonNull CallRecord cr) {
        View content = LayoutInflater.from(this).inflate(R.layout.popup_call_record_menu, null, false);
        PopupWindow win = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        win.setOutsideTouchable(true);
        win.setElevation(12f);
        win.setOnDismissListener(() -> {
            anchor.setPressed(false);
            adapter.setMenuState(cr.uri, false);
        });

        TextView tvBookmark = content.findViewById(R.id.tv_bookmark);
        TextView tvCache = content.findViewById(R.id.tv_cache);
        View rowBookmark = content.findViewById(R.id.row_bookmark);
        View rowRename = content.findViewById(R.id.row_rename);
        View rowCache = content.findViewById(R.id.row_cache);

        // 상태에 따른 토글 텍스트
        boolean isBookmarked = false;
        boolean hasCache = vm.hasCache(cr.uri);
        boolean canRename = true;
//        boolean isBookmarked = vm.isBookmarked(cr);               // 구현체 보유 전제. 없으면 false.
//        boolean hasCache = vm.hasCache(cr.uri());                 // MainViewModel 사양 참조
//        boolean canRename = vm.canRename(cr);                     // 조건부 노출. 없으면 false로.

        tvBookmark.setText(isBookmarked
                ? tvBookmark.getContext().getString(R.string.btn_remove_bookmark)
                : tvBookmark.getContext().getString(R.string.btn_add_bookmark));
        tvCache.setText(hasCache
                ? tvCache.getContext().getString(R.string.btn_delete_cache)
                : tvCache.getContext().getString(R.string.btn_stt_and_make_summary));
        rowRename.setVisibility(canRename ? View.VISIBLE : View.GONE);

        // 클릭 동작은 당장 미구현. 닫기만 수행.
        rowBookmark.setOnClickListener(v -> win.dismiss());
        rowRename.setOnClickListener(v -> win.dismiss());
        rowCache.setOnClickListener(v -> win.dismiss());

        // 위치 표시
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        // anchor 우측 상단 기준 조금 띄워서 표시
        win.showAsDropDown(anchor, anchor.getWidth() - content.getMeasuredWidth(), -anchor.getHeight());
    }


}