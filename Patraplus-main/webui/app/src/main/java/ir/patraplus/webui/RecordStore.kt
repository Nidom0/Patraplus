package ir.patraplus.webui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RecordStore(context: Context) {
    private val prefs = context.getSharedPreferences("patra_records", Context.MODE_PRIVATE)
    private val recordsKey = "records_json"

    fun load(): MutableList<CustomerRecord> {
        val raw = prefs.getString(recordsKey, null) ?: return mutableListOf()
        val array = JSONArray(raw)
        val list = mutableListOf<CustomerRecord>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(obj.toRecord())
        }
        return list
    }

    fun upsertAll(incoming: List<CustomerRecord>): RecordStoreResult {
        val existing = load()
        val map = existing.associateBy { it.key() }.toMutableMap()
        var added = 0
        for (record in incoming) {
            val key = record.key()
            val current = map[key]
            if (current == null) {
                map[key] = record
                added++
            } else {
                map[key] = record.copy(
                    status = current.status,
                    operatorNotes = current.operatorNotes
                )
            }
        }
        val merged = map.values.toList()
        save(merged)
        return RecordStoreResult(merged, added)
    }

    fun updateStatus(records: List<CustomerRecord>, recordKey: String, status: RecordStatus): List<CustomerRecord> {
        val updated = records.map { record ->
            if (record.key() == recordKey) record.copy(status = status) else record
        }
        save(updated)
        return updated
    }

    private fun save(records: List<CustomerRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(record.toJson())
        }
        prefs.edit().putString(recordsKey, array.toString()).apply()
    }

    private fun JSONObject.toRecord(): CustomerRecord {
        val statusValue = optString("status", RecordStatus.PENDING.name)
        val status = runCatching { RecordStatus.valueOf(statusValue) }.getOrDefault(RecordStatus.PENDING)
        return CustomerRecord(
            name = optString("name"),
            mobile = optString("mobile"),
            phone = optString("phone"),
            province = optString("province"),
            city = optString("city"),
            postalCode = optString("postalCode"),
            address = optString("address"),
            notes = optString("notes"),
            registeredAt = optString("registeredAt"),
            seller = optString("seller"),
            deliveryStatus = optString("deliveryStatus"),
            operatorNotes = optString("operatorNotes"),
            status = status
        )
    }

    private fun CustomerRecord.toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("mobile", mobile)
            .put("phone", phone)
            .put("province", province)
            .put("city", city)
            .put("postalCode", postalCode)
            .put("address", address)
            .put("notes", notes)
            .put("registeredAt", registeredAt)
            .put("seller", seller)
            .put("deliveryStatus", deliveryStatus)
            .put("operatorNotes", operatorNotes)
            .put("status", status.name)
    }

    fun updateOperatorNotes(
        records: List<CustomerRecord>,
        recordKey: String,
        operatorNotes: String
    ): List<CustomerRecord> {
        val updated = records.map { record ->
            if (record.key() == recordKey) record.copy(operatorNotes = operatorNotes) else record
        }
        save(updated)
        return updated
    }
}
