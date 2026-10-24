package com.ridediary.maps

enum class MapStyle { SIMPLE, STANDARD, SATELLITE, CUSTOM }

data class MapAppearance(
    val style: MapStyle=MapStyle.SIMPLE,
    val speedColors: Boolean=true,
    val stops: Boolean=true,
    val endpoints: Boolean=true,
    val places: Boolean=false,
    val traffic: Boolean=false,
) {
    // Preview suppression never changes the user's expanded-map preference.
    fun forSurface(expanded: Boolean)=if(expanded || !stops) this else copy(stops=false)

    companion object {
        fun styleFromStored(value: String?,hasCustomStyle: Boolean): MapStyle =
            MapStyle.entries.firstOrNull { it.name==value } ?: if(hasCustomStyle) MapStyle.CUSTOM else MapStyle.SIMPLE
    }
}
