package io.github.kkocaerm.otobustakip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    private fun soap(method: String, inner: String) =
        """<?xml version="1.0" encoding="utf-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">""" +
            """<soap:Body><${method}Response xmlns="http://tempuri.org/"><${method}Result>$inner</${method}Result>""" +
            """</${method}Response></soap:Body></soap:Envelope>"""

    @Test
    fun envelopeEscapesParams() {
        val e = Parser.envelope("GetHat_json", mapOf("HatKodu" to "A&<B"))
        assertTrue(e.contains("<GetHat_json xmlns=\"http://tempuri.org/\"><HatKodu>A&amp;&lt;B</HatKodu></GetHat_json>"))
    }

    @Test
    fun parsesBuses() {
        val json = """[{"kapino":"C-325","boylam":"28.8701","enlem":"41.0123","hatkodu":"500T",""" +
            """"guzergahkodu":"500T_D_D0","hatad":"TUZLA - CEVİZLİBAĞ","yon":"TUZLA &amp; ŞİFA",""" +
            """"son_konum_zamani":"2024-05-01 12:34:56","yakinDurakKodu":"113252"},""" +
            """{"kapino":"A-1","boylam":"28,5","enlem":"41,5","guzergahkodu":"500T_G_D0","yon":"X",""" +
            """"son_konum_zamani":"2024-05-01T07:05:00","yakinDurakKodu":null},""" +
            """{"kapino":"BAD","boylam":"","enlem":""}]"""
        val xml = soap("GetHatOtoKonum_json", json.replace("\"", "&quot;").replace("&amp;", "&amp;amp;"))
        val buses = Parser.buses(Parser.result(xml, "GetHatOtoKonum_json"))
        assertEquals(2, buses.size)
        val a = buses[0]
        assertEquals("A-1", a.doorNo)
        assertEquals("G", a.dir)
        assertEquals(41.5, a.lat, 1e-9)
        assertEquals("07:05:00", a.time)
        assertEquals("", a.nearStopCode)
        val c = buses[1]
        assertEquals("D", c.dir)
        assertEquals("12:34:56", c.time)
        assertEquals("113252", c.nearStopCode)
        assertEquals("TUZLA - CEVİZLİBAĞ", c.lineName)
    }

    @Test
    fun emptyResults() {
        assertTrue(Parser.buses(Parser.result(soap("GetHatOtoKonum_json", "[]"), "GetHatOtoKonum_json")).isEmpty())
        assertTrue(Parser.buses(Parser.result("<x/>", "GetHatOtoKonum_json")).isEmpty())
        assertNull(Parser.lineName("[]", "500T"))
        assertTrue(Parser.stops(soap("DurakDetay_GYY", "<NewDataSet />")).isEmpty())
    }

    @Test
    fun parsesLineName() {
        val json = """[{"SHATKODU":"500T","SHATADI":"TUZLA - CEVİZLİBAĞ","TARIFE_TIP":"Tam"}]"""
        assertEquals("TUZLA - CEVİZLİBAĞ", Parser.lineName(json, "500t"))
    }

    private val tables =
        """<NewDataSet xmlns=""><Table diffgr:id="Table1"><HATKODU>15F</HATKODU><YON>G</YON><SIRANO>2</SIRANO>""" +
            """<DURAKKODU>200002</DURAKKODU><DURAKADI>İKİNCİ</DURAKADI><XKOORDINATI>29.1</XKOORDINATI></Table>""" +
            """<Table><HATKODU>15F</HATKODU><YON>D</YON><SIRANO>1</SIRANO><DURAKKODU>200003</DURAKKODU>""" +
            """<DURAKADI>DÖNÜŞ &amp; BİR</DURAKADI></Table>""" +
            """<Table><HATKODU>15F</HATKODU><YON>G</YON><SIRANO>1</SIRANO><DURAKKODU>200001</DURAKKODU>""" +
            """<DURAKADI>BİRİNCİ</DURAKADI></Table></NewDataSet>"""

    @Test
    fun parsesStopsInlineXml() {
        val stops = Parser.stops(soap("DurakDetay_GYY", tables))
        assertEquals(listOf("200003", "200001", "200002"), stops.map { it.code })
        assertEquals("DÖNÜŞ & BİR", stops[0].name)
        assertEquals("D", stops[0].dir)
        assertEquals(2, stops[2].order)
    }

    @Test
    fun parsesStopsEscapedXml() {
        val escaped = tables.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val stops = Parser.stops(soap("DurakDetay_GYY", escaped))
        assertEquals(3, stops.size)
        assertEquals("DÖNÜŞ & BİR", stops[0].name)
    }
}
