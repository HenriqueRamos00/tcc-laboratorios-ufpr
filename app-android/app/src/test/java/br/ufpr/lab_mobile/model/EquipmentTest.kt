package br.ufpr.lab_mobile.model

import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.analysisLabel
import br.ufpr.lab_mobile.adapter.identificationFields
import br.ufpr.lab_mobile.adapter.statusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentTest {
    private val detail = EquipmentDetail(
        id = "e-1", serialNumber = "SN-17", tag = "SN-17", equipmentTag = "TR-A", type = null, location = null,
        company = "Empresa", oilType = null, voltage = "13,800/220", power = null, manufacturer = " ",
        manufacturingYear = null, oilVolume = null, nameplateKnown = true,
    )

    private fun report(
        id: String? = "r-1",
        date: String? = "2026-03-07",
        analysisType: String? = "chromatography",
        status: String? = "completed",
        signed: Boolean = false,
        pdfAvailable: Boolean? = null,
    ) = EquipmentReport(id, date, analysisType, status, signed, pdfAvailable)

    @Test
    fun `nameplate TAG wins over the name the API gives to the equipment`() {
        val base = Equipment("e", "SN-17", "SN-17", null, null)
        assertEquals("SN-17", base.displayTag)
        assertEquals("TR-A", base.copy(equipmentTag = "TR-A").displayTag)
        assertEquals("SN-17", base.copy(equipmentTag = " ").displayTag)
        assertNull(base.copy(tag = "", equipmentTag = null).displayTag)
        assertEquals("TR-A", detail.displayTag)
    }

    @Test
    fun `identification follows the portal order and keeps blank nameplate fields of a known equipment`() {
        assertEquals(
            listOf(
                R.string.equipment_serial_number, R.string.equipment_tag, R.string.equipment_type,
                R.string.equipment_oil_type, R.string.equipment_company, R.string.equipment_voltage,
                R.string.equipment_power, R.string.equipment_manufacturer, R.string.equipment_manufacturing_year,
                R.string.equipment_oil_volume,
            ),
            identificationFields(detail).map { it.first },
        )
    }

    @Test
    fun `an unknown equipment hides blank nameplate fields but keeps what is known`() {
        val fields = identificationFields(detail.copy(serialNumber = null, voltage = null, nameplateKnown = false))
        assertEquals(
            listOf(R.string.equipment_serial_number, R.string.equipment_tag, R.string.equipment_company),
            fields.map { it.first },
        )
        assertNull("the serial number row stays, as not informed", fields.first().second)
    }

    @Test
    fun `location appears only when the source informs it`() {
        assertFalse(identificationFields(detail.copy(location = " ")).any { it.first == R.string.equipment_location })
        assertEquals(
            R.string.equipment_location to "SE Norte",
            identificationFields(detail.copy(location = "SE Norte")).last(),
        )
    }

    @Test
    fun `download follows pdfAvailable and falls back to the signed document`() {
        assertTrue(report(pdfAvailable = true).canDownload)
        assertFalse(report(pdfAvailable = false, signed = true).canDownload)
        assertTrue(report(signed = true).canDownload)
        assertFalse(report().canDownload)
        assertFalse(report(id = " ", pdfAvailable = true).canDownload)
    }

    @Test
    fun `the minimum date sent for a missing value is not a date`() {
        assertEquals("2026-03-07", report().knownDate)
        listOf(null, "", " ", "0001-01-01", "0001-01-01T00:00:00").forEach { assertNull(report(date = it).knownDate) }
    }

    @Test
    fun `file name is safe and omits what is missing`() {
        assertEquals("relatorio-chromatography-2026-03-07.pdf", report().fileName)
        assertEquals("relatorio-chromatography.pdf", report(date = "0001-01-01").fileName)
        assertEquals("relatorio-a-b-2026-03-07.pdf", report(analysisType = "a/b").fileName)
        assertEquals("relatorio.pdf", report(date = null, analysisType = null).fileName)
    }

    @Test
    fun `type and status use the portal labels`() {
        assertEquals(R.string.equipment_analysis_physical_chemical, report(analysisType = "physical-chemical").analysisLabel)
        assertEquals(R.string.equipment_analysis_chromatography, report().analysisLabel)
        listOf("other", "new-type", null).forEach { assertEquals(R.string.equipment_analysis_other, report(analysisType = it).analysisLabel) }
        assertEquals(R.string.equipment_report_completed, report().statusLabel)
        assertEquals(R.string.equipment_report_in_progress, report(status = "in-progress").statusLabel)
        listOf("new-status", null).forEach { assertEquals(R.string.equipment_not_informed, report(status = it).statusLabel) }
    }
}
