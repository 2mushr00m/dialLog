//package com.example.diallog.ui.viewmodel;
//
//import android.app.Application;
//import android.content.Context;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//import androidx.lifecycle.AndroidViewModel;
//import androidx.lifecycle.LiveData;
//import androidx.lifecycle.MutableLiveData;
//import androidx.lifecycle.ViewModel;
//
//import com.example.diallog.data.repository.CallRepository;
//import com.example.diallog.data.repository.RepositoryProvider;
//
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class SplashViewModel extends AndroidViewModel {
//    private final ExecutorService io = Executors.newSingleThreadExecutor();
//    private final MutableLiveData<Boolean> isReady = new MutableLiveData<>(false);
//
//
//    public SplashViewModel(@NonNull Application app) {
//        super(app);
//    }
//    public LiveData<Boolean> getIsReady() {
//        return isReady;
//    }
//
//    public void initApp() {
//        io.execute(() -> {
//            try {
//                CallRepository repo = RepositoryProvider.buildCallRepository(getApplication().getApplicationContext());
//                repo.getRecent(0, 1);
//
//                isReady.postValue(true);
//            } catch (Throwable t) {
//                Log.w("Splash", "초기화 실패", t);
//                isReady.postValue(true);
//            }
//        });
//    }
//}
