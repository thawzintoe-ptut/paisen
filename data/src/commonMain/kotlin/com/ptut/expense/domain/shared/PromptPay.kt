package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang

/**
 * #199: Thailand's PromptPay QR, the EMVCo "Merchant Presented" payload every Thai bank app
 * scans. Nothing moves through PaiSen: the QR only fills in the payee and the amount.
 *
 * An ID is a mobile number (10 digits from 0), a national or tax ID (13) or an e-wallet ID (15).
 */
object PromptPay {

    /** Digits only, or null when it is not one of the three shapes. */
    fun normalize(input: String): String? {
        val digits = input.filter { it.isDigit() }
        return digits.takeIf {
            (it.length == PHONE_LENGTH && it.startsWith("0")) || it.length == NATIONAL_ID_LENGTH || it.length == E_WALLET_LENGTH
        }
    }

    /** "••••••5678": enough for the payer to know it is the right person, no more. */
    fun masked(id: String): String = "••••••${id.takeLast(VISIBLE_DIGITS)}"

    /**
     * The QR's text for paying [amount] to [id], a dynamic QR because it carries the amount. Null
     * for an ID that is not one of the three shapes: nothing else may reach a QR a payer scans.
     */
    fun payload(rawId: String, amount: Satang): String? {
        val id = normalize(rawId)?.takeIf { it == rawId } ?: return null
        val account = when (id.length) {
            PHONE_LENGTH -> field(TAG_PHONE, COUNTRY_PREFIX + id.drop(1))
            NATIONAL_ID_LENGTH -> field(TAG_NATIONAL_ID, id)
            else -> field(TAG_E_WALLET, id)
        }
        val minor = amount.toMinorUnits()
        val withoutCrc = field(PAYLOAD_FORMAT, "01") +
            field(POINT_OF_INITIATION, DYNAMIC) +
            field(MERCHANT_ACCOUNT, field(TAG_AID, AID) + account) +
            field(COUNTRY, "TH") +
            field(CURRENCY, THB_NUMERIC) +
            field(AMOUNT, "${minor / CENTS}.${(minor % CENTS).toString().padStart(2, '0')}") +
            CRC + CRC_LENGTH
        return withoutCrc + crc16(withoutCrc)
    }

    private fun field(tag: String, value: String) = tag + value.length.toString().padStart(2, '0') + value

    /** CRC-16/CCITT-FALSE over the payload, the "63" tag and its length included, as EMVCo asks. */
    private fun crc16(text: String): String {
        var crc = CRC_INIT
        text.encodeToByteArray().forEach { byte ->
            crc = crc xor ((byte.toInt() and BYTE_MASK) shl BYTE_BITS)
            repeat(BYTE_BITS) {
                crc = if (crc and HIGH_BIT != 0) (crc shl 1) xor CRC_POLY else crc shl 1
                crc = crc and WORD_MASK
            }
        }
        return crc.toString(HEX).uppercase().padStart(CRC_DIGITS, '0')
    }

    private const val PHONE_LENGTH = 10
    private const val NATIONAL_ID_LENGTH = 13
    private const val E_WALLET_LENGTH = 15
    private const val VISIBLE_DIGITS = 4
    private const val COUNTRY_PREFIX = "0066"
    private const val PAYLOAD_FORMAT = "00"
    private const val POINT_OF_INITIATION = "01"
    private const val DYNAMIC = "12"
    private const val MERCHANT_ACCOUNT = "29"
    private const val TAG_AID = "00"
    private const val TAG_PHONE = "01"
    private const val TAG_NATIONAL_ID = "02"
    private const val TAG_E_WALLET = "03"
    private const val AID = "A000000677010111"
    private const val COUNTRY = "58"
    private const val CURRENCY = "53"
    private const val THB_NUMERIC = "764"
    private const val AMOUNT = "54"
    private const val CRC = "63"
    private const val CRC_LENGTH = "04"
    private const val CENTS = 100L
    private const val CRC_INIT = 0xFFFF
    private const val CRC_POLY = 0x1021
    private const val HIGH_BIT = 0x8000
    private const val WORD_MASK = 0xFFFF
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8
    private const val HEX = 16
    private const val CRC_DIGITS = 4
}
