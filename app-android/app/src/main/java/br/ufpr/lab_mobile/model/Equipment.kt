package br.ufpr.lab_mobile.model

data class Equipment(
    val id: String,
    val serialNumber: String?,
    val tag: String?,
    val type: String?,
    val location: String?,
)

internal fun filterEquipment(equipment: List<Equipment>, search: String): List<Equipment> {
    val term = search.trim()
    if (term.isEmpty()) return equipment
    return equipment.filter {
        it.serialNumber.orEmpty().contains(term, ignoreCase = true) ||
            it.tag.orEmpty().contains(term, ignoreCase = true)
    }
}
