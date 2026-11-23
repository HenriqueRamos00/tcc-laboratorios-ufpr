package br.ufpr.lab_mobile.model

data class Equipment(
    val id: String,
    val serialNumber: String?,
    val tag: String?,
    val type: String?,
    val location: String?,
    val equipmentTag: String? = null,
) {
    /** TAG mostrada na tela; ver [preferredTag]. */
    val displayTag: String? get() = preferredTag(equipmentTag, tag)
}

/** Identificação de um equipamento, como devolvida por GET /api/equipments/{id}. */
data class EquipmentDetail(
    val id: String?,
    val serialNumber: String?,
    val tag: String?,
    val equipmentTag: String?,
    val type: String?,
    /** Subestação; só a fonte Spring informa. */
    val location: String?,
    val company: String?,
    val oilType: String?,
    /** Tensão, potência e volume já chegam como texto, com a unidade que a fonte informou. */
    val voltage: String?,
    val power: String?,
    val manufacturer: String?,
    val manufacturingYear: String?,
    val oilVolume: String?,
    /** `false` quando o cadastro não conhece o equipamento, e não apenas um dos campos. */
    val nameplateKnown: Boolean?,
) {
    /** TAG mostrada na tela; ver [preferredTag]. */
    val displayTag: String? get() = preferredTag(equipmentTag, tag)
}

/** Uma linha do histórico, como devolvida por GET /api/equipments/{id}/reports. */
data class EquipmentReport(
    val id: String?,
    val date: String?,
    val analysisType: String?,
    val status: String?,
    val hasSignedDocument: Boolean = false,
    val pdfAvailable: Boolean? = null,
) {
    /** A fonte nova informa [pdfAvailable]; a antiga só sabe se há documento assinado. */
    val canDownload: Boolean get() = !id.isNullOrBlank() && (pdfAvailable ?: hasSignedDocument)

    /** Data do relatório, ou `null` quando a fonte não tem: a API então envia a data mínima do C#. */
    val knownDate: String? get() = date?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith(MISSING_DATE) }

    /** Nome sugerido para o arquivo salvo, sem caracteres que o sistema de arquivos recusa. */
    val fileName: String
        get() = listOfNotNull("relatorio", analysisType?.trim()?.takeIf(String::isNotEmpty), knownDate)
            .joinToString("-").replace(Regex("[^\\w.-]+"), "-") + ".pdf"

    private companion object {
        const val MISSING_DATE = "0001-01-01"
    }
}

/**
 * A TAG que o cadastro conhece ou, na falta dela, o nome que a API dá ao equipamento, como no
 * portal web. Na fonte AutoLAB esse nome é o número de série ou o do laudo, e não uma TAG.
 */
private fun preferredTag(equipmentTag: String?, tag: String?): String? =
    equipmentTag?.takeIf(String::isNotBlank) ?: tag?.takeIf(String::isNotBlank)

internal fun filterEquipment(equipment: List<Equipment>, search: String): List<Equipment> {
    val term = search.trim()
    if (term.isEmpty()) return equipment
    return equipment.filter {
        listOf(it.serialNumber, it.equipmentTag, it.tag).any { value -> value.orEmpty().contains(term, ignoreCase = true) }
    }
}
