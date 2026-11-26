package br.ufpr.lab_mobile

import android.app.Application
import android.content.Context
import br.ufpr.lab_mobile.service.LoginApiService
import br.ufpr.lab_mobile.service.RetrofitProvider
import br.ufpr.lab_mobile.session.EncryptedSessionStorage
import br.ufpr.lab_mobile.session.SessionApi
import br.ufpr.lab_mobile.session.SessionManager

class PortalApplication : Application() {
    lateinit var sessions: SessionManager
        private set
    lateinit var loginApi: LoginApiService
        private set

    override fun onCreate() {
        super.onCreate()
        val root = getString(R.string.api_base_url)
        loginApi = RetrofitProvider.publicRetrofit(root).create(LoginApiService::class.java)
        // Legacy plaintext tokens have no validated identity/expiry and must not restore a session.
        getSharedPreferences("session", MODE_PRIVATE).edit().clear().apply()
        sessions = SessionManager(root, EncryptedSessionStorage(this), object : SessionApi {
            override fun refresh(token: String) = loginApi.refresh("Bearer $token").execute()
            override fun me(token: String) = loginApi.me("Bearer $token").execute()
        })
        RetrofitProvider.initialize(root, sessions)
    }
}

val Context.portalApplication: PortalApplication get() = applicationContext as PortalApplication
