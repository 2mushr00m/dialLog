package com.example.diallog.data.network;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ClovaSpeechApi {
    @Multipart
    @POST("recognizer/upload")
    Call<ClovaSpeechResponse> recognize(
            @Header("X-CLOVASPEECH-API-KEY") String secretKey,
            @Part MultipartBody.Part audio,
            @Part("params") RequestBody paramsJson,
            @Part("type") RequestBody typePlain
    );
}
