package ir.patraplus.webui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class RecordAdapter(
    private val onRecordClick: (CustomerRecord) -> Unit
) : ListAdapter<CustomerRecord, RecordAdapter.RecordViewHolder>(RecordDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view, onRecordClick)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RecordViewHolder(
        itemView: View,
        private val onRecordClick: (CustomerRecord) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.recordName)
        private val mobileText: TextView = itemView.findViewById(R.id.recordMobile)
        private val addressText: TextView = itemView.findViewById(R.id.recordAddress)
        private val dateText: TextView = itemView.findViewById(R.id.recordDate)
        private val statusText: TextView = itemView.findViewById(R.id.recordStatus)
        private val deliveryStatusText: TextView = itemView.findViewById(R.id.recordDeliveryStatus)

        fun bind(record: CustomerRecord) {
            nameText.text = record.name.ifBlank { "بدون نام" }
            mobileText.text = "موبایل: ${record.mobile}"
            addressText.text = "آدرس: ${record.address}"
            dateText.text = "تاریخ ثبت: ${record.registeredAt}"
            statusText.text = record.status.label
            deliveryStatusText.text = record.deliveryStatus.ifBlank { "نامشخص" }
            statusText.backgroundTintList = ContextCompat.getColorStateList(
                itemView.context,
                when (record.status) {
                    RecordStatus.PENDING -> R.color.patra_status_pending
                    RecordStatus.ACCEPTED -> R.color.patra_status_accepted
                    RecordStatus.REJECTED -> R.color.patra_status_rejected
                }
            )

            itemView.setOnClickListener { onRecordClick(record) }
        }
    }

    private object RecordDiffCallback : DiffUtil.ItemCallback<CustomerRecord>() {
        override fun areItemsTheSame(oldItem: CustomerRecord, newItem: CustomerRecord): Boolean {
            return oldItem.key() == newItem.key()
        }

        override fun areContentsTheSame(oldItem: CustomerRecord, newItem: CustomerRecord): Boolean {
            return oldItem == newItem
        }
    }
}
