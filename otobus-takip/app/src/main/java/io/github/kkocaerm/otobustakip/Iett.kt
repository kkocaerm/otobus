package io.github.kkocaerm.otobustakip

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** İBB / İETT açık veri SOAP servisleri. Çağrılar bloklar; arka plan iş parçacığında kullanın. */
object Iett {
    private const val BASE = "https://api.ibb.gov.tr/iett/"

    fun buses(line: String): List<Bus> {
        val m = "GetHatOtoKonum_json"
        return Parser.buses(Parser.result(post("FiloDurum/SeferGerceklesme.asmx", m, mapOf("HatKodu" to line)), m))
    }

    fun stops(line: String): List<Stop> =
        Parser.stops(post("ibb/ibb.asmx", "DurakDetay_GYY", mapOf("hat_kodu" to line)))

    fun lineName(line: String): String? {
        val m = "GetHat_json"
        return Parser.lineName(
            Parser.result(post("UlasimAnaVeri/HatDurakGuzergah.asmx", m, mapOf("HatKodu" to line)), m), line
        )
    }

    private fun post(path: String, method: String, params: Map<String, String>): String {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            c.setRequestProperty("SOAPAction", "\"http://tempuri.org/$method\"")
            c.outputStream.use { it.write(Parser.envelope(method, params).toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }
}
