package br.ufpr.lab_mobile.service

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String? = null

    @Synchronized
    fun retrofit(baseUrl: String): Retrofit {
        val normalizedUrl = "${baseUrl.trimEnd('/')}/"
        if (retrofit == null || currentBaseUrl != normalizedUrl) {
            currentBaseUrl = normalizedUrl
            retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }
}
