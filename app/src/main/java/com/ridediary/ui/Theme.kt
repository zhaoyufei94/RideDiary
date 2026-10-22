package com.ridediary.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background=Color(0xffefeee7)
val Ink=Color(0xff24382e)
val Muted=Color(0xff53634f) // Stronger contrast than the prototype's small secondary text.
val Line=Color(0xffd2d8ca)
val Accent=Color(0xffc9ed94)
val Soft=Color(0xffe0e7d7)
val Surface=Color(0xfffafbf6)
val Wait=Color(0xffdbb98b)
val Danger=Color(0xff9b443c)
val DangerSoft=Color(0xfff6e9e4)
val Moving=Color(0xff52715a)
val RideCard=Color(0xff233d30)
@Composable fun RideDiaryTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Ink,onPrimary=Surface,secondary=Ink,background=Background,onBackground=Ink,
        surface=Surface,onSurface=Ink,surfaceVariant=Soft,onSurfaceVariant=Muted,surfaceTint=Color.Transparent,outline=Line,secondaryContainer=Soft,error=Danger,onError=Color.White,errorContainer=DangerSoft,onErrorContainer=Danger),content=content)
}
