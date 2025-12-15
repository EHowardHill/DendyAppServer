package com.dendy.market

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// --- CONFIGURATION ---
const val BASE_URL = "https://cinemint.online/dendy/"

// --- DATA MODELS ---
data class AppModel(
    val name: String,
    @SerializedName("package_name") val packageName: String,
    val version: String,
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("icon_url") val iconUrl: String
) : java.io.Serializable

// --- RETROFIT SERVICE ---
interface ApiService {
    @GET("api/list")
    suspend fun getApps(): List<AppModel>
}

object RetrofitClient {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}