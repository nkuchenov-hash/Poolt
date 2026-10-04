package app.poolt.drivers

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

class DriverCatalogRepository(private val context: Context) {
    companion object {
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/nkuchenov-hash/Poolt/main/drivers/catalog.json"
        private const val PREFS = "driver_catalog"
        private const val KEY_JSON = "json"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cached(): List<DriverPackage> {
        val json = prefs.getString(KEY_JSON, null)
            ?: context.assets.open("driver_catalog.json").bufferedReader().use { it.readText() }
        return DriverCatalogParser.parse(json)
    }

    fun refresh(url: String = DEFAULT_CATALOG_URL): Result<List<DriverPackage>> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("Driver catalog HTTP " + connection.responseCode)
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = DriverCatalogParser.parse(json)
            prefs.edit().putString(KEY_JSON, json).apply()
            parsed
        } finally {
            connection.disconnect()
        }
    }
}
