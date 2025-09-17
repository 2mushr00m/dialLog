package com.example.diallog.data.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface GoogleSttApi {
    @POST("v1/speech:recognize")
    Call<GoogleSttResponse> recognize(
            @Header("Authorization") String bearerToken,
            @Body GoogleSttRequest request
    );

    @POST("v1/speech:longrunningrecognize")
    Call<GoogleOperationResponse> longRunningRecognize(
            @Header("Authorization") String bearerToken,
            @Body GoogleSttRequest request
    );

    @GET("v1/operations/{name}")
    Call<GoogleOperationResponse> getOperation(
            @Header("Authorization") String bearerToken,
            @Path(value = "name", encoded = true) String name
    );
}
