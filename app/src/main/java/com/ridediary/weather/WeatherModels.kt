package com.ridediary.weather

import java.time.*
import kotlin.math.round

/** Only rounded coordinates cross the weather service boundary. */
@ConsistentCopyVisibility
data class WeatherPoint private constructor(val lat: Double,val lon: Double) {
    val key get()="$lat,$lon"
    companion object {
        fun of(lat: Double,lon: Double): WeatherPoint {
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
            return WeatherPoint(round(lat*100)/100,round(lon*100)/100)
        }
    }
}
enum class WeatherProvider { OPEN_METEO, QWEATHER }
data class WeatherConfig(val enabled: Boolean=true,val provider: WeatherProvider=WeatherProvider.OPEN_METEO,
    val host: String="",val apiKey: String="") {
    val ready get()=provider==WeatherProvider.OPEN_METEO || (validHost(host) && Regex("[!-~]{1,512}").matches(apiKey.trim()))
    override fun toString()="WeatherConfig(enabled=$enabled, provider=$provider, credentials=<redacted>)"
    companion object {
        fun normalizeHost(value: String)=value.trim().removePrefix("https://").trimEnd('/').lowercase(java.util.Locale.ROOT)
        fun validHost(value: String)=Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+qweatherapi\\.com").matches(normalizeHost(value))
    }
}
enum class WeatherKind { CLEAR, CLOUDY, RAIN, SNOW, THUNDER, FOG, UNKNOWN }
data class WeatherHour(val time: Long,val temperature: Double?,val feelsLike: Double?,val rainChance: Double?,
    val precipitation: Double?,val wind: Double?,val gust: Double?,val kind: WeatherKind,val text: String?=null,
    val intervalStart: Long=time,val intervalEnd: Long=time+3_600_000)
data class WeatherDay(val date: LocalDate,val low: Double?,val high: Double?,val rainChance: Double?,val precipitation: Double?,val gust: Double?)
data class WeatherSnapshot(val provider: WeatherProvider,val fetchedAt: Long,val zone: String,val current: WeatherHour?,
    val days: List<WeatherDay>,val hours: List<WeatherHour>,val attribution: List<String>) {
    val stale get()=System.currentTimeMillis()-fetchedAt>20*60_000
}
enum class WeatherFailure { LOCATION, CONFIGURATION, AUTH, RATE_LIMIT, NETWORK, DATA }
data class WeatherResult(val snapshot: WeatherSnapshot?=null,val failure: WeatherFailure?=null)

/** Forecast intervals are [hour, hour+1h); never interpolate missing probability as zero. */
fun forecastWindow(snapshots: List<WeatherSnapshot>,start: Long,end: Long): List<WeatherHour> =
    snapshots.flatMap { it.hours }.filter { it.time < end.coerceAtLeast(start+1) && it.time+3_600_000>start }

/** Rain/gust intervals can end at the stated hour (Open-Meteo), unlike instant temperatures. */
fun precipitationChance(snapshots: List<WeatherSnapshot>,start: Long,end: Long): Double? = snapshots.flatMap { it.hours }
    .filter { it.intervalStart<end && it.intervalEnd>start }.mapNotNull { it.rainChance }.maxOrNull()
fun maximumGust(snapshots: List<WeatherSnapshot>,start: Long,end: Long): Double? = snapshots.flatMap { it.hours }
    .filter { it.intervalStart<end && it.intervalEnd>start }.mapNotNull { it.gust }.maxOrNull()
