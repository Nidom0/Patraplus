package ir.patraplus.webui

enum class RecordStatus(val label: String) {
    PENDING("در انتظار بررسی"),
    ACCEPTED("تایید شده"),
    REJECTED("رد شده")
}

data class CustomerRecord(
    val name: String,
    val mobile: String,
    val phone: String,
    val province: String,
    val city: String,
    val postalCode: String,
    val address: String,
    val notes: String,
    val registeredAt: String,
    val seller: String,
    val deliveryStatus: String,
    var status: RecordStatus = RecordStatus.PENDING
) {
    fun key(): String = (name + mobile).replace("\\s+".toRegex(), "")
}

data class RecordStoreResult(
    val records: List<CustomerRecord>,
    val addedCount: Int
)
