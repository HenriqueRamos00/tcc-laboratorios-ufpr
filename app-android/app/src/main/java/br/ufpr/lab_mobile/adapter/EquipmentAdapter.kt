package br.ufpr.lab_mobile.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.ufpr.lab_mobile.R
import br.ufpr.lab_mobile.databinding.ItemEquipmentBinding
import br.ufpr.lab_mobile.model.Equipment

class EquipmentAdapter(private val onViewEquipment: (Equipment) -> Unit) :
    RecyclerView.Adapter<EquipmentAdapter.EquipmentViewHolder>() {
    private var equipment = emptyList<Equipment>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EquipmentViewHolder(
        ItemEquipmentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: EquipmentViewHolder, position: Int) = holder.bind(equipment[position])

    override fun getItemCount() = equipment.size

    fun updateEquipment(items: List<Equipment>) {
        val previousCount = equipment.size
        equipment = items
        if (previousCount > 0) notifyItemRangeRemoved(0, previousCount)
        if (items.isNotEmpty()) notifyItemRangeInserted(0, items.size)
    }

    inner class EquipmentViewHolder(private val binding: ItemEquipmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(equipment: Equipment) = with(binding) {
            fun String?.display() = this?.takeIf(String::isNotBlank)
                ?: root.context.getString(R.string.equipment_not_informed)
            serialNumber.text = equipment.serialNumber.display()
            tag.text = equipment.tag.display()
            equipmentType.text = equipment.type.display()
            location.text = equipment.location.display()
            viewEquipment.contentDescription = root.context.getString(
                R.string.equipment_view_accessibility, serialNumber.text, tag.text,
            )
            viewEquipment.setOnClickListener { onViewEquipment(equipment) }
        }
    }
}
