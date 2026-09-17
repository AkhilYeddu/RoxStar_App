package com.roxstar.app

import android.app.Application
import com.roxstar.app.data.DraftRepository
import com.roxstar.app.network.ApiService
import com.roxstar.app.network.SocketManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RoxStarApplication : Application() {

    lateinit var draftRepository: DraftRepository
        private set

    lateinit var apiService: ApiService
        private set

    lateinit var socketManager: SocketManager
        private set

    // ── Server URL ────────────────────────────────────────────────────────────
    // Android Emulator  → 10.0.2.2 is the host-machine loopback alias
    // Physical Device   → use your machine's LAN IP: 172.20.197.141
    var serverBaseUrl: String = "https://roxstar-app.azurewebsites.net/" // default: emulator

    override fun onCreate() {
        super.onCreate()
        draftRepository = DraftRepository(applicationContext)
        socketManager = SocketManager()
        setupRetrofit(serverBaseUrl)
    }

    fun setupRetrofit(baseUrl: String) {
        serverBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)   // large base64 audio upload can take time
            .writeTimeout(90, TimeUnit.SECONDS)  // must be long enough to push entire WAV payload
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(serverBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}
