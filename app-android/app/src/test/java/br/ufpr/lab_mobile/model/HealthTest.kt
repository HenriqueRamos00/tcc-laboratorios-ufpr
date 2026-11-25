package br.ufpr.lab_mobile.model

import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.adapter.formatDifference
import br.ufpr.lab_mobile.adapter.formatQuantity
import br.ufpr.lab_mobile.adapter.keepUnitsTogether
import br.ufpr.lab_mobile.adapter.label
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {
    private fun quantity(value: String) = MeasuredValue.Quantity(BigDecimal(value))

    private fun indicator(vararg values: MeasuredValue) = HealthIndicator(
        "h2", "Hidrogênio", "H₂", "ppm", null, null,
        values.mapIndexed { index, value -> HealthReading("2026-0${index + 1}-10", value) },
    )

    @Test
    fun `the four forms of a result are never confused`() {
        assertEquals(quantity("25.5"), measuredValue(JsonPrimitive(BigDecimal("25.5"))))
        assertEquals(quantity("0"), measuredValue(JsonPrimitive(0)))
        assertEquals(MeasuredValue.NotDetected, measuredValue(JsonPrimitive(" ND ")))
        assertEquals(MeasuredValue.Text("0,01%"), measuredValue(JsonPrimitive("0,01%")))
        assertEquals(MeasuredValue.NotPerformed, measuredValue(null))
        assertEquals(MeasuredValue.NotPerformed, measuredValue(JsonNull.INSTANCE))
        assertEquals(MeasuredValue.NotPerformed, measuredValue(JsonPrimitive(" ")))
    }

    @Test
    fun `variation compares numbers with the previous reading`() {
        assertEquals(Variation.Difference(BigDecimal("-2.5"), "2026-01-10"), indicator(quantity("12.5"), quantity("10")).variation)
        assertEquals("scale does not create a change", Variation.Unchanged("2026-01-10"), indicator(quantity("10.0"), quantity("10")).variation)
        assertEquals("zero is a number", Variation.Difference(BigDecimal("10"), "2026-01-10"), indicator(quantity("0"), quantity("10")).variation)
    }

    @Test
    fun `variation skips analyses without this test and says which one it compares`() {
        val result = indicator(quantity("8"), MeasuredValue.NotPerformed, quantity("10")).variation
        assertEquals(Variation.Difference(BigDecimal("2"), "2026-01-10"), result)
    }

    @Test
    fun `not detected and text are never subtracted`() {
        assertEquals(Variation.Replaced(MeasuredValue.NotDetected, "2026-01-10"), indicator(MeasuredValue.NotDetected, quantity("3")).variation)
        assertEquals(Variation.Replaced(quantity("3"), "2026-01-10"), indicator(quantity("3"), MeasuredValue.NotDetected).variation)
        assertEquals(Variation.Unchanged("2026-01-10"), indicator(MeasuredValue.NotDetected, MeasuredValue.NotDetected).variation)
        assertEquals(Variation.Replaced(MeasuredValue.Text("<1"), "2026-01-10"), indicator(MeasuredValue.Text("<1"), quantity("3")).variation)
    }

    @Test
    fun `there is no variation without two results`() {
        assertNull(indicator(quantity("10")).variation)
        assertNull(indicator(MeasuredValue.NotPerformed, quantity("10")).variation)
        assertNull("the latest analysis has no result", indicator(quantity("10"), MeasuredValue.NotPerformed).variation)
        assertNull(indicator().variation)
        assertEquals(MeasuredValue.NotPerformed, indicator().latest)
    }

    @Test
    fun `gas results are matched to analyses by position and a missing one is not performed`() {
        val panel = DissolvedGasBlock(
            analysisDates = listOf("2025-01-10", "2026-01-10"),
            gases = listOf(
                DissolvedGas("h2", "Hidrogênio", "H₂", listOf(JsonPrimitive(20), JsonPrimitive(25))),
                DissolvedGas("co", "Monóxido de Carbono", "CO", listOf(JsonPrimitive(300))),
                DissolvedGas("combustiveis", "Total", "", listOf(JsonNull.INSTANCE, JsonPrimitive(361))),
            ),
            report = null,
        ).toPanel()
        assertEquals(quantity("25"), panel.indicators[0].latest)
        assertEquals("a short list never shifts an old result to the latest date", MeasuredValue.NotPerformed, panel.indicators[1].latest)
        assertEquals("2026-01-10", panel.date)
        assertEquals("ppm", panel.indicators[0].unit)
        assertNull("totals have no formula", panel.indicators[2].formula)
        assertEquals("order of importance, only what exists", listOf("combustiveis", "h2"), panel.highlights.map { it.key })
    }

    @Test
    fun `physical chemical tests keep limit and method only when registered`() {
        val panel = PhysicalChemicalBlock(
            columns = listOf(AnalysisColumn("0001-01-01")),
            tests = listOf(
                PhysicalChemicalTest("agua", "Teor de Água (ppm m/m)", "NBR 10710", "máx. 40", listOf(JsonPrimitive("ND"))),
                PhysicalChemicalTest("densidade", "Densidade", "-", " ", listOf(JsonPrimitive(0.87))),
                PhysicalChemicalTest("rigidez", "Rigidez Dielétrica (kV)", null, null, null),
            ),
            report = null,
        ).toPanel()
        assertEquals("máx. 40", panel.indicators[0].limit)
        assertEquals("NBR 10710", panel.indicators[0].method)
        assertNull(panel.indicators[1].limit)
        assertNull(panel.indicators[1].method)
        assertEquals(listOf("rigidez", "agua"), panel.highlights.map { it.key })
        assertNull("the minimum date is not a date", panel.date)
    }

    @Test
    fun `absent blocks are empty panels`() {
        val empty = HealthIndicators(null, null, null)
        assertTrue(empty.gases.indicators.isEmpty())
        assertTrue(empty.oil.highlights.isEmpty())
        assertTrue(DissolvedGasBlock(null, null, null).toPanel().indicators.isEmpty())
        assertTrue(PhysicalChemicalBlock(null, null, null).toPanel().indicators.isEmpty())
    }

    @Test
    fun `only the known classification codes become a classification`() {
        val expected = mapOf("normal" to LabClassification.NORMAL, "alerta" to LabClassification.ALERT, " nao-conforme " to LabClassification.NON_COMPLIANT)
        expected.forEach { (title, classification) ->
            val report = TestReport(title, null)
            assertEquals(classification, report.classification)
            assertNull("a classification is not repeated as a heading", report.heading)
        }
        val autoLab = TestReport("ENSAIOS EM ÓLEO ISOLANTE", null)
        assertNull(autoLab.classification)
        assertEquals("ENSAIOS EM ÓLEO ISOLANTE", autoLab.heading)
        listOf(null, " ", "-").forEach { assertNull(TestReport(it, null).heading) }
        assertEquals(R.string.health_classification_non_compliant, LabClassification.NON_COMPLIANT.label)
    }

    @Test
    fun `report paragraphs are joined without blanks`() {
        assertEquals("Um.\n\nDois.", TestReport(null, listOf(" Um. ", null, " ", "Dois.")).text)
        assertEquals("", TestReport(null, null).text)
    }

    @Test
    fun `units are kept in one line without changing what is read`() {
        assertEquals("Densidade (g/\u2060mL)", "Densidade (g/mL)".keepUnitsTogether())
        assertEquals("Hidrogênio H₂", "Hidrogênio H₂".keepUnitsTogether())
    }

    @Test
    fun `numbers use Brazilian format and keep every decimal of the laboratory`() {
        assertEquals("55.961", formatQuantity(BigDecimal("55961.0")))
        assertEquals("0,0004", formatQuantity(BigDecimal("0.0004")))
        assertEquals("25", formatQuantity(BigDecimal("25.0")))
        assertEquals("+3", formatDifference(BigDecimal("3")))
        assertEquals("−0,02", formatDifference(BigDecimal("-0.02")))
    }
}
