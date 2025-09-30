package com.example.diallog.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.diallog.R;
import com.example.diallog.config.AppConfig;
import com.example.diallog.ui.adapter.CallRecordSectionAdapter;
import com.example.diallog.ui.viewmodel.MainVMFactory;
import com.example.diallog.ui.viewmodel.MainViewModel;
import com.example.diallog.utils.PermissionHelper;

import java.util.List;


public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainUI";

    private MainViewModel vm;
    private RecyclerView rv;
    private CallRecordSectionAdapter adapter;


    @Override
    protected void onCreate(Bundle b) {
        setTheme(R.style.Theme_DialLog);
        super.onCreate(b);
        AppConfig.get().setIntentOverride(getIntent());

        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        if (!PermissionHelper.hasReadAudioPermission(this)) {
            PermissionHelper.requestReadAudio(this);
            return;
        }

        rv = findViewById(R.id.rv_sections);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CallRecordSectionAdapter(uri -> {
            Intent i = new Intent(this, SummaryActivity.class);
            i.putExtra(SummaryActivity.EXTRA_URI, uri);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        vm = new ViewModelProvider(this, new MainVMFactory(getApplication()))
                .get(MainViewModel.class);
        vm.getSections().observe(this, this::renderSections);
        vm.start();
//        vm.loading().observe(this, loading -> {
//        });
//        vm.error().observe(this, e -> {
//        });

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) vm.loadMore(rv.getLayoutManager());
            }
        });
    }

    private void renderSections(List<?> sections) {
        boolean empty = sections == null || sections.isEmpty();
        rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        //noinspection unchecked
        adapter.submitList(empty ? null : (List) sections);
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

}