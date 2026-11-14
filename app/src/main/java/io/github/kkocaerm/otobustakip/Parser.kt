package io.github.kkocaerm.otobustakip

import org.json.JSONArray
import org.json.JSONObject

/** Hat üzerindeki canlı araç. */
data class Bus(
    val doorNo: String,
    val lat: Double,
    val lon: Double,
    /** Güzergâh kodundan çıkarılan yön: "G" (gidiş), "D" (dönüş) ya da boş. */
    val dir: String,
    /** Aracın gittiği yönün adı (son durak). */
    val heading: String,
    val lineName: String,
    /** Son konum zamanı, "HH:mm:ss". */
    val time: String,
    val nearStopCode: String,
)

/** Hattın bir yönündeki durak. */
data class Stop(
    val code: String,
    val name: String,
    val dir: String,
    val order: Int,
)

/** İETT SOAP servis yanıtlarını çözen, Android'e bağımlı olmayan yardımcılar. */
object Parser {

    fun envelope(method: String, params: Map<String, String>): String {
        val body = params.entries.joinToString("") { "<${it.key}>${escape(it.value)}</${it.key}>" }
        return """<?xml version="1.0" encoding="utf-8"?>""" +
            """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">""" +
            """<soap:Body><$method xmlns="http://tempuri.org/">$body</$method></soap:Body>""" +
            """</soap:Envelope>"""
    }

    /** `<{method}Result>` içeriğini XML kaçışları çözülmüş olarak döndürür; yoksa boş. */
    fun result(xml: String, method: String): String {
        val m = Regex("<${method}Result[^>]*>(.*?)</${method}Result>", RegexOption.DOT_MATCHES_ALL)
            .find(xml) ?: return ""
        return unescape(m.groupValues[1]).trim()
    }

    fun buses(json: String): List<Bus> = objects(json).mapNotNull { o ->
        val lat = o["enlem"]?.replace(',', '.')?.toDoubleOrNull()
        val lon = o["boylam"]?.replace(',', '.')?.toDoubleOrNull()
        val door = o["kapino"].orEmpty()
        if (lat == null || lon == null || door.isEmpty()) return@mapNotNull null
        Bus(
            doorNo = door,
            lat = lat,
            lon = lon,
            dir = o["guzergahkodu"].orEmpty().split('_').getOrNull(1).orEmpty().uppercase(),
            heading = o["yon"].orEmpty(),
            lineName = o["hatad"].orEmpty(),
            time = clock(o["son_konum_zamani"].orEmpty()),
            nearStopCode = o["yakindurakkodu"].orEmpty(),
        )
    }.sortedBy { it.doorNo }

    /** GetHat_json yanıtından hat adını döndürür; hat yoksa null. */
    fun lineName(json: String, code: String): String? {
        val all = objects(json)
        val hit = all.firstOrNull { it["shatkodu"].equals(code, ignoreCase = true) } ?: all.firstOrNull()
        return hit?.get("shatadi")?.takeIf { it.isNotBlank() }
    }

    /** DurakDetay_GYY yanıtındaki `<Table>` satırlarını yön ve sıraya göre dizilmiş duraklara çevirir. */
    fun stops(xml: String): List<Stop> {
        val src = if (!xml.contains("<Table", true) && xml.contains("&lt;Table", true)) unescape(xml) else xml
        val row = Regex("<Table\\b[^>]*>(.*?)</Table>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val field = Regex("<(\\w+)[^>]*>([^<]*)</\\1>")
        return row.findAll(src).mapNotNull { r ->
            val f = field.findAll(r.groupValues[1])
                .associate { it.groupValues[1].lowercase() to unescape(it.groupValues[2]).trim() }
            val code = f["durakkodu"].orEmpty()
            val name = f["durakadi"].orEmpty()
            if (code.isEmpty() || name.isEmpty()) return@mapNotNull null
            Stop(code, name, f["yon"].orEmpty().uppercase(), f["sirano"]?.toIntOrNull() ?: 0)
        }.sortedWith(compareBy({ it.dir }, { it.order })).toList()
    }

    private fun objects(json: String): List<Map<String, String>> {
        val t = json.trim()
        if (!t.startsWith("[")) return emptyList()
        val arr = JSONArray(t)
        return (0 until arr.length()).mapNotNull { i ->
            val o: JSONObject = arr.optJSONObject(i) ?: return@mapNotNull null
            o.keys().asSequence().associate { k ->
                k.lowercase() to (if (o.isNull(k)) "" else o.get(k).toString().trim())
            }
        }
    }

    /** "2024-05-01 12:34:56" ya da "2024-05-01T12:34:56" -> "12:34:56". */
    private fun clock(s: String): String =
        Regex("(\\d{1,2}:\\d{2}(:\\d{2})?)").find(s)?.value ?: s

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun unescape(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&amp;", "&")
}
