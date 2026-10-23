package com.ridediary

import android.app.Application
import androidx.room.Room
import com.ridediary.data.*
import kotlinx.coroutines.*

class RideDiaryApp : Application() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    lateinit var repository: RideRepository
    lateinit var ready: Deferred<Unit>
    override fun onCreate() {
        super.onCreate()
        repository=RideRepository(Room.databaseBuilder(this,RideDatabase::class.java,"ridediary.db").addMigrations(RideDatabase.MIGRATION_1_2,RideDatabase.MIGRATION_2_3,RideDatabase.MIGRATION_3_4).build())
        ready=scope.async { repository.recoverInterrupted(System.currentTimeMillis()) }
    }
}
