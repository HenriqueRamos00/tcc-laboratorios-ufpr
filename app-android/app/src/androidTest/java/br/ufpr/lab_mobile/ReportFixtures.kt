package br.ufpr.lab_mobile

import okhttp3.Request

/** Respostas falsas da API usadas pelos testes instrumentados da tela de relatórios. */
internal object ReportFixtures {
    const val PUBLISHED_REPORTS = """[
        {"id":"r-307","identification":"LACTEC-00307/2026","requestNumber":"00307/2026","sampleName":"Areia",
         "status":"Publicado","laboratory":"Lab. de Óleos","publicationDate":"2026-03-07","pdfAvailable":true},
        {"id":"r-308","identification":"LACTEC-00308/2026","requestNumber":"00308/2026","sampleName":"Água",
         "status":"Publicado","laboratory":"Lab. de Águas","publicationDate":"2026-03-08","pdfAvailable":true}
    ]"""

    /** Dois laudos em GET /api/reports; lista vazia para as demais rotas. */
    fun body(request: Request): String =
        if (request.url.encodedPath == "/api/reports") PUBLISHED_REPORTS else "[]"
}

/** Uma página de uma lista numerada de 1 a [total], como a API responde. */
internal fun numberedPage(request: Request, total: Int, item: (Int) -> String): String {
    val size = request.url.queryParameter("pageSize")!!.toInt()
    val first = (request.url.queryParameter("page")!!.toInt() - 1) * size + 1
    return (first..minOf(first + size - 1, total)).joinToString(",", "[", "]") { item(it) }
}
