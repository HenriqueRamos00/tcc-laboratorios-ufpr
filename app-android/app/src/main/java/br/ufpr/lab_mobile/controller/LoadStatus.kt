package br.ufpr.lab_mobile.controller

import android.content.Context
import androidx.annotation.StringRes
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.ListFooter
import br.ufpr.lab_mobile.service.PagedList
import retrofit2.HttpException

/** Situação da leitura de uma lista da API. */
internal sealed interface LoadStatus {
    data object Loading : LoadStatus
    data object Complete : LoadStatus
    data class Failed(@param:StringRes val message: Int, val retryable: Boolean) : LoadStatus
}

/** A falha de leitura de uma lista, para a tela. Sem sessão, tentar de novo não adianta. */
internal fun loadFailure(error: Exception, @StringRes message: Int, hasSession: Boolean): LoadStatus.Failed {
    val unauthorized = error is HttpException && error.code() == 401
    return LoadStatus.Failed(if (unauthorized && !hasSession) R.string.session_expired else message, retryable = !unauthorized)
}

/**
 * O rodapé da lista, ou `null` quando ela está completa. Enquanto nada chegou também não há
 * rodapé: a situação aparece no lugar da lista.
 */
internal fun Context.listFooter(list: PagedList<*>, status: LoadStatus): ListFooter? {
    if (list.items.isEmpty()) return null
    val total = list.total ?: list.items.size
    return when (status) {
        LoadStatus.Complete -> null
        LoadStatus.Loading -> ListFooter(getString(R.string.list_loading_more, list.covered, total), loading = true, canRetry = false)
        is LoadStatus.Failed -> ListFooter(
            if (status.retryable) getString(R.string.list_partial_error, list.covered, total) else getString(status.message),
            loading = false,
            canRetry = status.retryable,
        )
    }
}
