package br.ufpr.lab_mobile.model

import br.ufpr.lab_mobile.adapter.formatDisplayDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    private fun report(
        id: String? = "r-1",
        identification: String? = "LACTEC-00307/2026",
        requestNumber: String? = null,
        quoteCode: String? = null,
        hasSignedDocument: Boolean = false,
        pdfAvailable: Boolean? = null,
    ) = Report(id, identification, requestNumber, quoteCode, null, null, null, null, null,
        hasSignedDocument, pdfAvailable)

    @Test
    fun `download follows pdfAvailable and falls back to the signed document`() {
        assertTrue(report(pdfAvailable = true).canDownload)
        assertFalse(report(pdfAvailable = false, hasSignedDocument = true).canDownload)
        assertTrue(report(hasSignedDocument = true).canDownload)
        assertFalse(report().canDownload)
        assertFalse(report(id = null, pdfAvailable = true).canDownload)
    }

    @Test
    fun `sample and publication combine the available fields`() {
        val base = report()
        assertNull(base.sample)
        assertEquals("AM-1 · Óleo", base.copy(sampleId = " AM-1 ", sampleName = "Óleo").sample)
        assertEquals("Óleo", base.copy(sampleId = " ", sampleName = "Óleo").sample)
        assertEquals("2026-03-08", base.copy(issueDate = "2026-03-01", publicationDate = "2026-03-08").publishedOn)
        assertEquals("2026-03-01", base.copy(issueDate = "2026-03-01").publishedOn)
    }

    @Test
    fun `file name is safe for the file system`() {
        assertEquals("relatorio-LACTEC-00307-2026.pdf", report().fileName)
        assertEquals("relatorio-r-1.pdf", report(identification = null).fileName)
    }

    @Test
    fun `search matches report request or quote ignoring case`() {
        val items = listOf(
            report(id = "1", identification = "LACTEC-1"),
            report(id = "2", identification = "X", requestNumber = "SOL-77"),
            report(id = "3", identification = "Y", quoteCode = "Q-lactec"),
        )
        assertEquals(listOf("1", "3"), filterReports(items, " lactec ").map(Report::id))
        assertEquals(listOf("2"), filterReports(items, "sol-77").map(Report::id))
        assertTrue(filterReports(items, "nada").isEmpty())
        assertSame(items, filterReports(items, "  "))
    }

    @Test
    fun `dates from the API are shown as dd-MM-yyyy`() {
        assertEquals("07/03/2026", formatDisplayDate("2026-03-07"))
        assertEquals("07/03/2026", formatDisplayDate("2026-03-07T10:15:00"))
        assertEquals("07/03/2026", formatDisplayDate("2026-03-07T10:15:00-03:00"))
        assertEquals("março de 2026", formatDisplayDate("março de 2026"))
        assertNull(formatDisplayDate(" "))
        assertNull(formatDisplayDate(null))
    }
}
