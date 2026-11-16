package com.ptut.expense.domain.shared

/**
 * The currency the Add sheet opens on before anyone picks one: the country the phone reports
 * (ISO 3166 two letters), mapped to its own currency. A country with no currency in [Currencies]
 * and an unknown country start in baht, as the app always has.
 */
object StartingCurrency {

    private val byCountry: Map<String, String> = mapOf(
        "TH" to "THB", "US" to "USD", "GB" to "GBP", "JP" to "JPY", "CN" to "CNY", "KR" to "KRW",
        "SG" to "SGD", "MY" to "MYR", "VN" to "VND", "LA" to "LAK", "KH" to "KHR", "MM" to "MMK",
        "ID" to "IDR", "PH" to "PHP", "HK" to "HKD", "TW" to "TWD", "AU" to "AUD", "IN" to "INR",
        "CH" to "CHF",
        // The euro area.
        "AT" to "EUR", "BE" to "EUR", "CY" to "EUR", "DE" to "EUR", "EE" to "EUR", "ES" to "EUR",
        "FI" to "EUR", "FR" to "EUR", "GR" to "EUR", "HR" to "EUR", "IE" to "EUR", "IT" to "EUR",
        "LT" to "EUR", "LU" to "EUR", "LV" to "EUR", "MT" to "EUR", "NL" to "EUR", "PT" to "EUR",
        "SI" to "EUR", "SK" to "EUR",
    )

    fun forCountry(country: String?): Currency =
        country?.trim()?.uppercase()?.let(byCountry::get)?.let(Currencies::of) ?: Currencies.Thb
}
