package br.ufpr.lab_mobile.service

import retrofit2.HttpException
import retrofit2.Response

/** Uma página de uma lista da API e o total informado no header X-Total-Count. */
internal class PageResult<T>(val items: List<T>, val total: Int?)

/** Percorre as páginas até o total informado pela API e junta os itens na ordem recebida. */
internal suspend fun <T> collectPages(pageSize: Int, loadPage: suspend (page: Int) -> PageResult<T>): List<T> {
    val all = mutableListOf<T>()
    var page = 1
    while (true) {
        val result = loadPage(page)
        all += result.items
        // Sem total no header, ou com uma página vazia, não há como saber se existe a próxima.
        val total = result.total ?: return all
        if (result.items.isEmpty() || page * pageSize >= total) return all
        page++
    }
}

/** O corpo de uma resposta de sucesso; 204 (sem conteúdo) vira `null`, erro HTTP vira exceção. */
internal fun <T> successBody(response: Response<T>): T? {
    if (!response.isSuccessful) {
        response.errorBody()?.close()
        throw HttpException(response)
    }
    return if (response.code() == 204) null else response.body()
}

internal fun totalCount(response: Response<*>): Int? = response.headers()["X-Total-Count"]?.toIntOrNull()
