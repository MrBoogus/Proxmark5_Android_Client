package com.proxmark5.client.protocol

data class DecodedBadge(
    val technology: String,
    val uidHex: String,
    val cardId: String,
    val facilityCode: String? = null,
    val formatLen: String? = null,
    val wiegandHex: String? = null,
    val rawString: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object BadgeParser {
    private val ansiRegex = Regex("\\u001B\\[[;\\d]*[A-Za-z]")

    private val hidRegex = Regex(
        "TAG\\s+ID:\\s*([0-9a-fA-F]+)(?:\\s*\\((\\d+)\\))?(?:\\s*-\\s*Format\\s*Len:\\s*(\\d+)\\s*bit)?(?:\\s*-\\s*FC:\\s*(\\d+))?(?:\\s*-\\s*Card:\\s*(\\d+))?",
        RegexOption.IGNORE_CASE
    )

    private val awidRegex = Regex(
        "AWID Found\\s*-\\s*Bit length:\\s*(\\d+)(?:,\\s*FC:\\s*(\\d+))?,\\s*Card:\\s*(\\d+)(?:\\s*-\\s*Wiegand:\\s*([0-9a-fA-F]+))?(?:,\\s*Raw:\\s*([0-9a-fA-F]+))?",
        RegexOption.IGNORE_CASE
    )

    private val ioRegex = Regex(
        "IO Prox\\s*XSF\\((\\d+)\\)([0-9a-fA-F]+):(\\d+)\\s*(?:\\(([0-9a-fA-F]+)\\))?",
        RegexOption.IGNORE_CASE
    )

    private val emRegex = Regex(
        "EM(?:\\s+XL)?\\s+TAG\\s+ID:\\s*([0-9a-fA-F]{10,24})",
        RegexOption.IGNORE_CASE
    )

    private val indalaRegex = Regex(
        "(?:Indala|INDALA)\\s*(?:Found|ID)?\\s*[:-]?\\s*([0-9a-fA-F]{8,32})(?:.*?Card:?\\s*(\\d+))?(?:.*?FC:?\\s*(\\d+))?",
        RegexOption.IGNORE_CASE
    )

    private val wiegandGenericRegex = Regex(
        "(?:Wiegand|WIEGAND):?\\s*([0-9a-fA-F]+).*?(?:FC|Facility):?\\s*(\\d+).*?(?:Card|CN):?\\s*(\\d+)",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawLog: String): DecodedBadge? {
        val clean = ansiRegex.replace(rawLog, "").trim()

        // 1. Try HID Prox
        val hidMatch = hidRegex.find(clean)
        if (hidMatch != null && clean.contains("TAG ID:", ignoreCase = true) && !clean.contains("EM TAG", ignoreCase = true)) {
            val uid = hidMatch.groupValues[1].uppercase()
            val shortCard = hidMatch.groupValues.getOrNull(2)?.ifEmpty { null }
            val formatLen = hidMatch.groupValues.getOrNull(3)?.ifEmpty { null }
            val fc = hidMatch.groupValues.getOrNull(4)?.ifEmpty { null }
            val card = hidMatch.groupValues.getOrNull(5)?.ifEmpty { null } ?: shortCard ?: uid

            val formatDesc = when (formatLen) {
                "26" -> "26-bit (H10301)"
                "37" -> "37-bit (H10302/H10304)"
                "35" -> "35-bit (Corporate 1000)"
                "34" -> "34-bit"
                null -> if (uid.length > 10) "88/192-bit Extended" else "Standard HID"
                else -> "$formatLen-bit"
            }

            return DecodedBadge(
                technology = "HID Prox (LF 125 kHz FSK)",
                uidHex = uid,
                cardId = card,
                facilityCode = fc,
                formatLen = formatDesc,
                wiegandHex = uid,
                rawString = clean
            )
        }

        // 2. Try AWID
        val awidMatch = awidRegex.find(clean)
        if (awidMatch != null) {
            val bitLen = awidMatch.groupValues[1]
            val fc = awidMatch.groupValues.getOrNull(2)?.ifEmpty { null }
            val card = awidMatch.groupValues.getOrNull(3)?.ifEmpty { null } ?: "Unknown"
            val wiegand = awidMatch.groupValues.getOrNull(4)?.ifEmpty { null }
            val raw = awidMatch.groupValues.getOrNull(5)?.ifEmpty { null } ?: wiegand ?: card

            return DecodedBadge(
                technology = "AWID (LF 125 kHz FSK)",
                uidHex = raw.uppercase(),
                cardId = card,
                facilityCode = fc,
                formatLen = "$bitLen-bit AWID",
                wiegandHex = wiegand?.uppercase(),
                rawString = clean
            )
        }

        // 3. Try IO Prox
        val ioMatch = ioRegex.find(clean)
        if (ioMatch != null) {
            val version = ioMatch.groupValues[1]
            val fc = ioMatch.groupValues[2]
            val card = ioMatch.groupValues[3]
            val raw = ioMatch.groupValues.getOrNull(4)?.ifEmpty { null } ?: "$fc$card"

            return DecodedBadge(
                technology = "IO Prox (LF 125 kHz FSK)",
                uidHex = raw.uppercase(),
                cardId = card,
                facilityCode = fc,
                formatLen = "XSF($version)",
                wiegandHex = null,
                rawString = clean
            )
        }

        // 4. Try EM4100
        val emMatch = emRegex.find(clean)
        if (emMatch != null) {
            val uid = emMatch.groupValues[1].uppercase()
            val decoded = Em4100Decoder.fromHex(uid)
            val card = decoded?.cardNumber?.toString() ?: uid
            val fc = decoded?.facilityCode?.toString()

            return DecodedBadge(
                technology = "EM4100 / EM410x (LF 125 kHz ASK)",
                uidHex = uid,
                cardId = card,
                facilityCode = fc,
                formatLen = "64-bit Manchester",
                wiegandHex = null,
                rawString = clean
            )
        }

        // 5. Try Indala
        val indalaMatch = indalaRegex.find(clean)
        if (indalaMatch != null) {
            val uid = indalaMatch.groupValues[1].uppercase()
            val card = indalaMatch.groupValues.getOrNull(2)?.ifEmpty { null } ?: uid
            val fc = indalaMatch.groupValues.getOrNull(3)?.ifEmpty { null }

            return DecodedBadge(
                technology = "Indala (LF 125 kHz PSK)",
                uidHex = uid,
                cardId = card,
                facilityCode = fc,
                formatLen = if (uid.length >= 16) "64-bit Indala" else "26-bit Indala",
                wiegandHex = uid,
                rawString = clean
            )
        }

        // 6. Generic Wiegand pattern
        val wiegandMatch = wiegandGenericRegex.find(clean)
        if (wiegandMatch != null) {
            val wiegandHex = wiegandMatch.groupValues[1].uppercase()
            val fc = wiegandMatch.groupValues[2]
            val card = wiegandMatch.groupValues[3]

            return DecodedBadge(
                technology = "Generic Wiegand Badge",
                uidHex = wiegandHex,
                cardId = card,
                facilityCode = fc,
                formatLen = "${wiegandHex.length * 4}-bit",
                wiegandHex = wiegandHex,
                rawString = clean
            )
        }

        return null
    }
}
