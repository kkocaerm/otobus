package io.github.kkocaerm.otobustakip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.util.concurrent.Executors

class MainActivity : Activity() {

    /** Listede gösterilen satır: bir durak (üzerindeki araçlarla) ya da durağı bilinmeyen tek araç. */
    private class Row(val stop: Stop?, val buses: List<Bus>)

    private class Direction(val key: String, val label: String)

    private val io = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    private val autoRefresh = Runnable { refresh() }

    private var lineCode: String? = null
    private var lineName = ""
    private var stops: List<Stop> = emptyList()
    private var buses: List<Bus> = emptyList()
    private var dirs: List<Direction> = emptyList()
    private var dirIdx = 0
    private var rows: List<Row> = emptyList()
    private var favs: MutableList<Pair<String, String>> = mutableListOf()
    /** Eski isteklerin yanıtlarını yok saymak için. */
    private var generation = 0
    private var busy = 0

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnBack: View
    private lateinit var btnFav: TextView
    private lateinit var btnRefresh: View
    private lateinit var progress: View
    private lateinit var home: View
    private lateinit var line: View
    private lateinit var etLine: EditText
    private lateinit var tvHomeMsg: TextView
    private lateinit var tvNoFavs: View
    private lateinit var dirBar: View
    private lateinit var btnDirA: Button
    private lateinit var btnDirB: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        btnBack = findViewById(R.id.btnBack)
        btnFav = findViewById(R.id.btnFav)
        btnRefresh = findViewById(R.id.btnRefresh)
        progress = findViewById(R.id.progress)
        home = findViewById(R.id.home)
        line = findViewById(R.id.line)
        etLine = findViewById(R.id.etLine)
        tvHomeMsg = findViewById(R.id.tvHomeMsg)
        tvNoFavs = findViewById(R.id.tvNoFavs)
        dirBar = findViewById(R.id.dirBar)
        btnDirA = findViewById(R.id.btnDirA)
        btnDirB = findViewById(R.id.btnDirB)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<View>(R.id.btnSearch).setOnClickListener { search() }
        etLine.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        btnBack.setOnClickListener { showHome() }
        btnRefresh.setOnClickListener { refresh() }
        btnFav.setOnClickListener { toggleFav() }
        btnDirA.setOnClickListener { selectDir(0) }
        btnDirB.setOnClickListener { selectDir(1) }

        findViewById<ListView>(R.id.lvFavs).apply {
            adapter = favAdapter
            setOnItemClickListener { _, _, pos, _ -> openLine(favs[pos].first) }
        }
        findViewById<ListView>(R.id.lvRows).apply {
            adapter = rowAdapter
            setOnItemClickListener { _, _, pos, _ -> rows[pos].buses.firstOrNull()?.let(::openMap) }
        }

        favs = loadFavs()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (lineCode != null) refresh()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(autoRefresh)
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (lineCode != null) showHome() else super.onBackPressed()
    }

    // ---- Ana ekran ----

    private fun showHome() {
        generation++
        lineCode = null
        ui.removeCallbacks(autoRefresh)
        setBusy(reset = true)
        home.visibility = View.VISIBLE
        line.visibility = View.GONE
        btnBack.visibility = View.GONE
        btnFav.visibility = View.GONE
        btnRefresh.visibility = View.GONE
        tvTitle.setText(R.string.app_name)
        tvSubtitle.setText(R.string.home_subtitle)
        tvNoFavs.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
        favAdapter.notifyDataSetChanged()
    }

    private fun search() {
        val code = etLine.text.toString().trim().uppercase().replace(" ", "")
        if (code.isEmpty()) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etLine.windowToken, 0)
        openLine(code)
    }

    private fun homeMessage(msg: String?) {
        tvHomeMsg.text = msg
        tvHomeMsg.visibility = if (msg == null) View.GONE else View.VISIBLE
    }

    // ---- Hat ekranı ----

    private fun openLine(code: String) {
        val gen = ++generation
        homeMessage(null)
        setBusy(+1)
        io.execute {
            val b = runCatching { Iett.buses(code) }
            val s = runCatching { Iett.stops(code) }
            val n = runCatching { Iett.lineName(code) }
            ui.post {
                if (gen != generation) return@post
                setBusy(-1)
                val busList = b.getOrDefault(emptyList())
                val stopList = s.getOrDefault(emptyList())
                val name = n.getOrNull() ?: busList.firstOrNull()?.lineName.orEmpty()
                when {
                    b.isFailure && s.isFailure ->
                        homeMessage("Bağlantı kurulamadı. İnternetinizi kontrol edip tekrar deneyin.")
                    busList.isEmpty() && stopList.isEmpty() && name.isEmpty() ->
                        homeMessage("“$code” hattı bulunamadı.")
                    else -> showLine(code, name, stopList, busList)
                }
            }
        }
    }

    private fun showLine(code: String, name: String, stopList: List<Stop>, busList: List<Bus>) {
        lineCode = code
        lineName = name
        stops = stopList
        buses = busList
        dirIdx = 0
        home.visibility = View.GONE
        line.visibility = View.VISIBLE
        btnBack.visibility = View.VISIBLE
        btnFav.visibility = View.VISIBLE
        btnRefresh.visibility = View.VISIBLE
        tvTitle.text = code
        tvSubtitle.text = name
        updateFavIcon()
        render()
        scheduleRefresh()
    }

    private fun refresh() {
        val code = lineCode ?: return
        val gen = generation
        ui.removeCallbacks(autoRefresh)
        setBusy(+1)
        io.execute {
            val b = runCatching { Iett.buses(code) }
            ui.post {
                if (gen != generation) return@post
                setBusy(-1)
                b.onSuccess { buses = it; render() }
                    .onFailure { tvStatus.text = "Güncellenemedi, son bilinen konumlar gösteriliyor." }
                scheduleRefresh()
            }
        }
    }

    private fun scheduleRefresh() {
        ui.removeCallbacks(autoRefresh)
        ui.postDelayed(autoRefresh, REFRESH_MS)
    }

    private fun selectDir(i: Int) {
        if (i >= dirs.size) return
        dirIdx = i
        render()
    }

    private fun render() {
        // Yönler: durak listesi varsa oradan (etiket = son durak), yoksa araçlardan.
        dirs = if (stops.isNotEmpty()) {
            stops.groupBy { it.dir }.map { (k, v) -> Direction(k, "→ " + v.last().name) }
        } else {
            buses.groupBy { it.dir }.map { (k, v) -> Direction(k, "→ " + v.first().heading) }
        }.take(2)
        if (dirIdx >= dirs.size) dirIdx = 0

        dirBar.visibility = if (dirs.size > 1) View.VISIBLE else View.GONE
        listOf(btnDirA, btnDirB).forEachIndexed { i, btn ->
            val d = dirs.getOrNull(i)
            btn.visibility = if (d == null) View.GONE else View.VISIBLE
            btn.text = d?.label
            val on = i == dirIdx
            btn.backgroundTintList = ColorStateList.valueOf(getColor(if (on) R.color.primary else R.color.chip_off))
            btn.setTextColor(getColor(if (on) R.color.on_primary else R.color.text))
        }

        val dir = dirs.getOrNull(dirIdx)
        val dirStops = stops.filter { it.dir == dir?.key }
        // Tek yön varsa ya da aracın yönü bilinmiyorsa aracı gizlemek yerine göster.
        val dirBuses = buses.filter { dirs.size < 2 || it.dir == dir?.key || dirs.none { d -> d.key == it.dir } }
        val byStop = dirBuses.groupBy { it.nearStopCode }
        val known = dirStops.map { it.code }.toSet()

        rows = dirBuses.filter { it.nearStopCode !in known }.map { Row(null, listOf(it)) } +
            dirStops.map { Row(it, byStop[it.code].orEmpty()) }
        rowAdapter.notifyDataSetChanged()

        tvStatus.text = when {
            buses.isEmpty() -> "Şu an bu hatta konum bildiren araç yok."
            dirBuses.isEmpty() -> "Bu yönde şu an araç yok."
            else -> "Bu yönde ${dirBuses.size} araç · araca dokunarak haritada açın"
        }
    }

    private fun openMap(bus: Bus) {
        val uri = Uri.parse("geo:${bus.lat},${bus.lon}?q=${bus.lat},${bus.lon}(${Uri.encode(bus.doorNo)})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, "Harita uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setBusy(delta: Int = 0, reset: Boolean = false) {
        busy = if (reset) 0 else (busy + delta).coerceAtLeast(0)
        progress.visibility = if (busy > 0) View.VISIBLE else View.INVISIBLE
    }

    // ---- Favoriler ----

    private fun prefs() = getSharedPreferences("app", Context.MODE_PRIVATE)

    private fun loadFavs(): MutableList<Pair<String, String>> = runCatching {
        val arr = JSONArray(prefs().getString("favs", "[]"))
        MutableList(arr.length()) { i -> arr.getJSONArray(i).let { it.getString(0) to it.getString(1) } }
    }.getOrDefault(mutableListOf())

    private fun saveFavs() {
        val arr = JSONArray()
        favs.forEach { arr.put(JSONArray().put(it.first).put(it.second)) }
        prefs().edit().putString("favs", arr.toString()).apply()
    }

    private fun toggleFav() {
        val code = lineCode ?: return
        if (!favs.removeAll { it.first == code }) favs.add(code to lineName)
        saveFavs()
        updateFavIcon()
    }

    private fun updateFavIcon() {
        btnFav.text = if (favs.any { it.first == lineCode }) "★" else "☆"
    }

    // ---- Adaptörler ----

    private val favAdapter = object : BaseAdapter() {
        override fun getCount() = favs.size
        override fun getItem(position: Int) = favs[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.row_fav, parent, false)
            v.findViewById<TextView>(R.id.tvCode).text = favs[position].first
            v.findViewById<TextView>(R.id.tvLineName).text = favs[position].second
            return v
        }
    }

    private val rowAdapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = rows[position].buses.isNotEmpty()
        override fun areAllItemsEnabled() = false
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.row_stop, parent, false)
            val row = rows[position]
            val has = row.buses.isNotEmpty()
            v.findViewById<TextView>(R.id.tvOrder).text = row.stop?.order?.toString() ?: "•"
            v.findViewById<TextView>(R.id.tvName).apply {
                text = row.stop?.name ?: "Durak bilgisi yok"
                setTextColor(getColor(if (has || row.stop == null) R.color.text else R.color.text_dim))
            }
            v.findViewById<TextView>(R.id.tvBus).apply {
                visibility = if (has) View.VISIBLE else View.GONE
                text = row.buses.joinToString("\n") { "🚌 ${it.doorNo}  ${it.time}" }
            }
            return v
        }
    }

    private companion object {
        const val REFRESH_MS = 20_000L
    }
}
