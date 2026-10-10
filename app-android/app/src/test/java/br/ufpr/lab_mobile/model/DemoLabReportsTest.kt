package br.ufpr.lab_mobile.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoLabReportsTest {
    @Test
    fun exposesTheTwoOrderedDemoReportsWithEveryField() {
        assertEquals(
            listOf(
                DemoLabReport("LACTEC-00307/2026", "00307/2026", "Areia", "Lab. de Óleos", "07/03/2026"),
                DemoLabReport("LACTEC-00308/2026", "00308/2026", "Água", "Lab. de Águas", "08/03/2026"),
            ),
            DemoLabReports.reports,
        )
    }
}
