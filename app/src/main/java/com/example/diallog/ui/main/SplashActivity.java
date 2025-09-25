//package com.example.diallog.ui.main;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.lifecycle.ViewModelProvider;
//
//import com.example.diallog.R;
//import com.example.diallog.ui.viewmodel.SplashViewModel;
//
//public class SplashActivity extends AppCompatActivity {
//    private static final int MIN_SPLASH_DELAY = 1500;
//    private boolean isMainReady = false;
//    private boolean isMinTimePassed = false;
//
//    private SplashViewModel vm;
//
//    @Override
//    protected void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_splash);
//
//        vm = new ViewModelProvider(this).get(SplashViewModel.class);
//
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            isMinTimePassed = true;
//            tryStartMain();
//        }, MIN_SPLASH_DELAY);
//
//        vm.getIsReady().observe(this, ready -> {
//            if (Boolean.TRUE.equals(ready)) {
//                isMainReady = true;
//                tryStartMain();
//            }
//        });
//        vm.initApp();
//    }
//
//    private void tryStartMain() {
//        if (isMinTimePassed && isMainReady) {
//            startActivity(new Intent(this, MainActivity.class));
//            finish();
//        }
//    }
//}
