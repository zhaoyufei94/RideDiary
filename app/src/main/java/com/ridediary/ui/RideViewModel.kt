package com.ridediary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ridediary.RideDiaryApp
import com.ridediary.data.RouteRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*

class RideViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as RideDiaryApp
    private val repository=app.repository
    val rides=repository.rides.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val matching=repository.matching
    val routes=repository.routes.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val error=MutableStateFlow(false)
    private fun task(block: suspend ()->Unit) {
        viewModelScope.launch {
            try { app.ready.await(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value=true }
        }
    }
    fun createRoute(id: String,name: String,direction: String,start: Double,end: Double,groupId: String?=null,
        excluded: Boolean?=null,reason: String?=null) = task {
        repository.createRoute(id,name,direction,start,end,groupId,excluded,reason)
    }
    fun classify(id: String,route: RouteRecord?,direction: String,excluded: Boolean,reason: String) = task {
        repository.classify(id,route,direction,excluded,reason)
    }
    fun boundaries(id: String,start: Long,end: Long) = task { repository.boundaries(id,start,end) }
    fun deleteRide(id: String,onDeleted: ()->Unit) = task { repository.deleteRide(id); onDeleted() }
    suspend fun mergeRides(ids: List<String>): String = withContext(Dispatchers.Default) {
        app.ready.await(); repository.mergeRides(ids)
    }
    fun undoMerge(id: String,onUndone: ()->Unit) = task { repository.undoMerge(id); onUndone() }
    fun editRoute(id: String,name: String,direction: String,start: Double,end: Double) = task {
        repository.editRoute(id,name,direction,start,end)
    }
    fun updatePlan(id: String,days: Int,minute: Int?,mode: String,onSaved: ()->Unit) = task {
        repository.updatePlan(id,days,minute,mode); onSaved()
    }
    fun deleteRoute(id: String) = task { repository.deleteRoute(id) }
    fun deleteRouteGroup(id: String,onDeleted: ()->Unit={}) = task { repository.deleteRouteGroup(id); onDeleted() }
    fun unlinkRide(id: String,groupId: String,onUnlinked: ()->Unit) = task {
        repository.unlinkRide(id,groupId); onUnlinked()
    }
}
