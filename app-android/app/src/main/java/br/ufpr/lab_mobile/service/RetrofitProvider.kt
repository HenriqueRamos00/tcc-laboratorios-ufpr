package br.ufpr.lab_mobile.service

import br.ufpr.lab_mobile.session.SessionInterceptor
import br.ufpr.lab_mobile.session.SessionManager
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String? = null

    fun publicRetrofit(baseUrl: String): Retrofit = builder(baseUrl).build()

    @Synchronized
    fun initialize(baseUrl: String, sessions: SessionManager) {
        currentBaseUrl = "${baseUrl.trimEnd('/')}/"
        retrofit = builder(baseUrl).client(client().addInterceptor(SessionInterceptor(currentBaseUrl!!.toHttpUrl(), sessions)).build()).build()
    }

    /** Some portal pages wait on slow laboratory queries, beyond OkHttp's 10 second default. */
    private const val READ_TIMEOUT_SECONDS = 30L

    // Redirects are deliberately disabled: a portal bearer must never follow a document redirect.
    private fun client() = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun builder(baseUrl: String) = Retrofit.Builder()
        .baseUrl("${baseUrl.trimEnd('/')}/")
        .client(client().build())
        .addConverterFactory(GsonConverterFactory.create())

    @Synchronized
    fun retrofit(baseUrl: String): Retrofit {
        val normalizedUrl = "${baseUrl.trimEnd('/')}/"
        check(currentBaseUrl == normalizedUrl) { "Portal API root must match the configured session origin" }
        return checkNotNull(retrofit) { "PortalApplication must initialize the shared API client" }
    }
}
