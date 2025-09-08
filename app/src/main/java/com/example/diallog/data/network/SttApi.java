package com.example.diallog.data.network;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface SttApi {
    @Multipart
    @POST("v1/stt")
    Call<SttResponse> uploadAudio(
            @Part MultipartBody.Part file,
            @Part("language") RequestBody language // 필요 없으면 제거
    );
}
