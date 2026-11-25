package br.ufpr.lab_mobile.service

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RetrofitProviderTest {
    @Test
    fun `slow laboratory pages have thirty seconds to answer and redirects stay disabled`() {
        val client = RetrofitProvider.publicRetrofit("http://localhost").callFactory() as OkHttpClient
        assertEquals(30_000, client.readTimeoutMillis)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }
}
