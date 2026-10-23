package com.ridediary.weather

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.*
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.time.*
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

private class WeatherException(val failure: WeatherFailure): Exception()
private fun JSONObject.number(key: String): Double? = if(isNull(key)) null else optDouble(key,Double.NaN).takeIf { it.isFinite() }
private fun JSONObject.measure(key: String)=optJSONObject(key)?.number("value")
private fun JSONArray.number(index: Int): Double? = if(isNull(index)) null else optDouble(index,Double.NaN).takeIf { it.isFinite() }
private fun JSONObject.array(key: String)=optJSONArray(key) ?: JSONArray()
private fun JSONObject.epoch(key: String)=runCatching { OffsetDateTime.parse(getString(key)).toInstant().toEpochMilli() }.getOrNull()
private fun kind(code: Int,qweather: Boolean=false): WeatherKind = if(qweather) when(code) {
    100,150 -> WeatherKind.CLEAR
    in 101..199 -> WeatherKind.CLOUDY
    302,303,304 -> WeatherKind.THUNDER
    in 300..399 -> WeatherKind.RAIN
    in 400..499 -> WeatherKind.SNOW
    in 500..599 -> WeatherKind.FOG
    else -> WeatherKind.UNKNOWN
} else when(code) {
    0 -> WeatherKind.CLEAR
    1,2,3 -> WeatherKind.CLOUDY
    45,48 -> WeatherKind.FOG
    51,53,55,56,57,61,63,65,66,67,80,81,82 -> WeatherKind.RAIN
    71,73,75,77,85,86 -> WeatherKind.SNOW
    95,96,99 -> WeatherKind.THUNDER
    else -> WeatherKind.UNKNOWN
}

/** Bounded HTTPS requests; credentials are headers, never URL parameters or logs. */
class WeatherClient(context: Context) {
    private val directory=File(context.cacheDir,"weather-v1").apply { mkdirs() }
    private val gates=mutableMapOf<String,Mutex>()
    private val parallel=Semaphore(3)
    private fun digest(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    suspend fun clear()=withContext(Dispatchers.IO) { directory.listFiles()?.forEach { it.delete() }; Unit }
    suspend fun fetch(config: WeatherConfig,point: WeatherPoint,language: String,force: Boolean=false,freshAfter: Long=0): WeatherResult = withContext(Dispatchers.IO) {
        if(!config.enabled) return@withContext WeatherResult()
        if(!config.ready) return@withContext WeatherResult(failure=WeatherFailure.CONFIGURATION)
        val key=digest("72h|${config.provider}|${config.host}|${digest(config.apiKey)}|${point.key}|$language")
        val gate=synchronized(gates) { gates.getOrPut(key) { Mutex() } }
        gate.withLock {
            val file=File(directory,key)
            val cached=runCatching { JSONObject(file.readText()) }.getOrNull()
            val age=System.currentTimeMillis()-(cached?.optLong("fetched") ?: 0)
            val saved=if(age in 0..6*3_600_000L) runCatching { parse(config,cached!!) }.getOrNull() else null
            if(!force && age in 0..20*60_000L && saved!=null && saved.fetchedAt>=freshAfter) return@withLock WeatherResult(saved)
            try {
                val payload=parallel.withPermit { download(config,point,language) }
                currentCoroutineContext().ensureActive()
                val snapshot=parse(config,payload)
                require(snapshot.current?.temperature!=null || snapshot.hours.any { it.temperature!=null })
                runCatching {
                    val temp=File(directory,"$key.tmp"); temp.writeText(payload.toString()); temp.renameTo(file)
                    directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { it.delete() }
                }
                WeatherResult(snapshot)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { WeatherResult(saved,(e as? WeatherException)?.failure ?: if(e is JSONException || e is IllegalArgumentException) WeatherFailure.DATA else WeatherFailure.NETWORK) }
        }
    }
    private suspend fun download(config: WeatherConfig,p: WeatherPoint,language: String): JSONObject {
        val result=JSONObject().put("fetched",System.currentTimeMillis())
        if(config.provider==WeatherProvider.OPEN_METEO) {
            val url="https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}&timezone=auto&timeformat=unixtime&forecast_days=3&wind_speed_unit=ms"+
                "&current=temperature_2m,apparent_temperature,weather_code,precipitation,wind_speed_10m,wind_gusts_10m"+
                "&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_gusts_10m"+
                "&daily=temperature_2m_min,temperature_2m_max,precipitation_probability_max,precipitation_sum,wind_gusts_10m_max"
            result.put("open",get(url,null))
        } else {
            val base="https://${WeatherConfig.normalizeHost(config.host)}/weather/v1"
            result.put("current",get("$base/current/${p.lat}/${p.lon}?lang=$language",config.apiKey))
            currentCoroutineContext().ensureActive()
            result.put("hourly",get("$base/hourly/${p.lat}/${p.lon}?hours=72&lang=$language",config.apiKey))
            currentCoroutineContext().ensureActive()
            result.put("daily",get("$base/daily/${p.lat}/${p.lon}?days=2&localTime=true&lang=$language",config.apiKey))
        }
        return result
    }
    private fun get(url: String,key: String?): JSONObject {
        val connection=URL(url).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.connectTimeout=7_000; connection.readTimeout=7_000
            connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("Accept-Encoding","gzip")
            if(key!=null) connection.setRequestProperty("X-QW-Api-Key",key)
            when(connection.responseCode) {
                200 -> Unit
                401,403 -> throw WeatherException(WeatherFailure.AUTH)
                429 -> throw WeatherException(WeatherFailure.RATE_LIMIT)
                else -> throw WeatherException(WeatherFailure.NETWORK)
            }
            val raw=connection.inputStream
            val stream=if(connection.contentEncoding?.contains("gzip")==true) GZIPInputStream(raw) else raw
            val bytes=stream.use { input ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(output.size()<=2_000_000) {
                    val read=input.read(buffer); if(read<0) break
                    output.write(buffer,0,read)
                }
                output.toByteArray()
            }
            if(bytes.size>2_000_000) throw WeatherException(WeatherFailure.DATA)
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    private fun parse(config: WeatherConfig,root: JSONObject): WeatherSnapshot {
        val fetched=root.getLong("fetched")
        if(config.provider==WeatherProvider.OPEN_METEO) {
            val raw=root.getJSONObject("open"); val zone=raw.optString("timezone",ZoneId.systemDefault().id)
            val zoneId=ZoneId.of(zone)
            fun hour(o: JSONObject,t: Long)=WeatherHour(t,o.number("temperature_2m"),o.number("apparent_temperature"),o.number("precipitation_probability"),
                o.number("precipitation"),o.number("wind_speed_10m"),o.number("wind_gusts_10m"),kind(o.optInt("weather_code",-1)),intervalStart=t-3_600_000,intervalEnd=t)
            val current=raw.optJSONObject("current")?.takeIf { it.has("time") }?.let { hour(it,it.getLong("time")*1000) }
            val hourly=raw.optJSONObject("hourly") ?: JSONObject(); val times=hourly.array("time")
            val hours=(0 until times.length()).map { index ->
                val item=JSONObject(); hourly.keys().forEach { key -> item.put(key,hourly.array(key).opt(index)) }
                hour(item,times.getLong(index)*1000)
            }
            val daily=raw.optJSONObject("daily") ?: JSONObject(); val dates=daily.array("time")
            val days=(0 until dates.length()).map { i -> WeatherDay(Instant.ofEpochSecond(dates.getLong(i)).atZone(zoneId).toLocalDate(),
                daily.array("temperature_2m_min").number(i),daily.array("temperature_2m_max").number(i),daily.array("precipitation_probability_max").number(i),
                daily.array("precipitation_sum").number(i),daily.array("wind_gusts_10m_max").number(i)) }
            return WeatherSnapshot(config.provider,fetched,zone,current,days,hours,listOf("https://open-meteo.com/","https://creativecommons.org/licenses/by/4.0/"))
        }
        fun hour(o: JSONObject,time: Long): WeatherHour {
            val precipitation=o.optJSONObject("precipitation"); val wind=o.optJSONObject("wind")
            return WeatherHour(time,o.measure("temperature"),o.measure("feelsLike"),precipitation?.number("probability")?.times(100),
                precipitation?.measure("amount"),wind?.measure("speed"),o.measure("windGust"),kind(o.optJSONObject("condition")?.optInt("code",-1) ?: -1,true),
                o.optJSONObject("condition")?.optString("text")?.takeIf { it.isNotBlank() })
        }
        val current=hour(root.getJSONObject("current"),fetched)
        val hourly=root.getJSONObject("hourly").array("hours")
        val hours=(0 until hourly.length()).mapNotNull { i -> hourly.getJSONObject(i).let { o -> o.epoch("forecastTime")?.let { hour(o,it) } } }
        val daily=root.getJSONObject("daily").array("days")
        val zone=runCatching { OffsetDateTime.parse(daily.getJSONObject(0).getString("forecastStartTime")).offset }.getOrDefault(ZoneOffset.UTC)
        val days=(0 until daily.length()).mapNotNull { i -> daily.getJSONObject(i).let { o ->
            o.epoch("forecastStartTime")?.let { start ->
                val halves=listOfNotNull(o.optJSONObject("daytime"),o.optJSONObject("nighttime"))
                WeatherDay(Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),o.measure("temperatureMin"),o.measure("temperatureMax"),
                    halves.mapNotNull { it.optJSONObject("precipitation")?.number("probability")?.times(100) }.maxOrNull(),
                    halves.mapNotNull { it.optJSONObject("precipitation")?.measure("amount") }.takeIf { it.size==halves.size && it.isNotEmpty() }?.sum(),
                    halves.mapNotNull { it.measure("windGustMax") }.maxOrNull())
            }
        } }
        val sources=listOf("current","hourly","daily").flatMap { name ->
            val a=root.getJSONObject(name).optJSONObject("metadata")?.array("attributions") ?: JSONArray()
            (0 until a.length()).mapNotNull { a.optString(it).takeIf { value -> value.startsWith("https://") } }
        }.distinct()
        return WeatherSnapshot(config.provider,fetched,zone.id,current,days,hours,sources)
    }
}
