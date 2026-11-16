package com.proxmark5.client.comm

import android.content.Context

data class LastDeviceRecord(
    val transportKind: TransportKind,
    val addressOrHost: String,
    val port: Int,
    val displayName: String
) {
    val displayLabel: String
        get() = when (transportKind) {
            TransportKind.WIFI_TCP -> "${transportKind.label}: $addressOrHost:$port"
            else -> "${transportKind.label}: $displayName ($addressOrHost)"
        }
}

/**
 * Remembers the last successfully connected Proxmark device and transport across app restarts.
 */
class LastDeviceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("proxmark_last_device", Context.MODE_PRIVATE)

    fun save(record: LastDeviceRecord) {
        prefs.edit()
            .putString(KEY_KIND, record.transportKind.name)
            .putString(KEY_ADDRESS, record.addressOrHost)
            .putInt(KEY_PORT, record.port)
            .putString(KEY_NAME, record.displayName)
            .apply()
    }

    fun load(): LastDeviceRecord? {
        val kindName = prefs.getString(KEY_KIND, null) ?: return null
        val kind = TransportKind.entries.firstOrNull { it.name == kindName } ?: return null
        val address = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
        val port = prefs.getInt(KEY_PORT, 0)
        val name = prefs.getString(KEY_NAME, address) ?: address
        return LastDeviceRecord(
            transportKind = kind,
            addressOrHost = address,
            port = port,
            displayName = name
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_KIND = "transport_kind"
        const val KEY_ADDRESS = "address_or_host"
        const val KEY_PORT = "port"
        const val KEY_NAME = "display_name"
    }
}
