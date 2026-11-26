package br.ufpr.lab_mobile.service

import retrofit2.HttpException
import retrofit2.Response

/** Uma página de uma lista da API e o total informado no header X-Total-Count. */
internal class PageResult<T>(val items: List<T>, val total: Int?)

/**
 * Uma lista da API lida página a página, na ordem recebida.
 *
 * [items] cresce a cada página. Uma falha não descarta o que já chegou: a exceção sai de
 * [loadRemaining] e a próxima chamada continua da página que falhou.
 */
class PagedList<T> internal constructor(
    private val pageSize: Int,
    private val loadPage: suspend (page: Int) -> PageResult<T>,
) {
    private var nextPage = 1

    var items: List<T> = emptyList()
        private set

    /** Total informado pela API, ou `null` enquanto ele não é conhecido. */
    var total: Int? = null
        private set

    var isComplete = false
        private set

    /** Quantos itens da API as páginas já lidas cobrem, para mostrar o andamento. */
    val covered: Int get() = ((nextPage - 1) * pageSize).coerceAtMost(total ?: items.size)

    /** Lê as páginas que faltam, chamando [onPage] depois de cada uma. */
    suspend fun loadRemaining(onPage: () -> Unit = {}) {
        while (!isComplete) {
            val result = loadPage(nextPage)
            items = items + result.items
            total = result.total
            // Sem total no header não há como saber se existe a próxima. Com ele, uma página vazia
            // não encerra a lista: a API omite da página os itens que ela não pode mostrar.
            isComplete = result.total == null || nextPage * pageSize >= result.total
            nextPage++
            onPage()
        }
    }
}

/** Todos os itens de uma lista da API, para as telas que só mostram a lista completa. */
internal suspend fun <T> collectPages(pageSize: Int, loadPage: suspend (page: Int) -> PageResult<T>): List<T> =
    PagedList(pageSize, loadPage).apply { loadRemaining() }.items

/** O corpo de uma resposta de sucesso; 204 (sem conteúdo) vira `null`, erro HTTP vira exceção. */
internal fun <T> successBody(response: Response<T>): T? {
    if (!response.isSuccessful) {
        response.errorBody()?.close()
        throw HttpException(response)
    }
    return if (response.code() == 204) null else response.body()
}

internal fun totalCount(response: Response<*>): Int? = response.headers()["X-Total-Count"]?.toIntOrNull()
