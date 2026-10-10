package br.ufpr.lab_mobile.model

data class DemoLabReport(
    val reportNumber: String,
    val protocol: String,
    val sample: String,
    val laboratory: String,
    val date: String,
)

object DemoLabReports {
    val reports = listOf(
        DemoLabReport("LACTEC-00307/2026", "00307/2026", "Areia", "Lab. de Óleos", "07/03/2026"),
        DemoLabReport("LACTEC-00308/2026", "00308/2026", "Água", "Lab. de Águas", "08/03/2026"),
    )
}
