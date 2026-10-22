package com.ridediary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ridediary.RideDiaryApp
import com.ridediary.data.RouteRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

class RideViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as RideDiaryApp
    private val repository=app.repository
    val rides=repository.rides.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val routes=repository.routes.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val error=MutableStateFlow(false)
    private fun task(block: suspend ()->Unit) {
        viewModelScope.launch {
            try { app.ready.await(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value=true }
        }
    }
    fun createRoute(id: String,name: String,direction: String,start: Double,end: Double) = task {
        repository.createRoute(id,name,direction,start,end)
    }
    fun classify(id: String,route: RouteRecord?,direction: String,excluded: Boolean,reason: String) = task {
        repository.classify(id,route,direction,excluded,reason)
    }
    fun boundaries(id: String,start: Long,end: Long) = task { repository.boundaries(id,start,end) }
    fun deleteRide(id: String,onDeleted: ()->Unit) = task { repository.deleteRide(id); onDeleted() }
    fun editRoute(id: String,name: String,direction: String,start: Double,end: Double) = task {
        repository.editRoute(id,name,direction,start,end)
    }
    fun deleteRoute(id: String) = task { repository.deleteRoute(id) }
}
