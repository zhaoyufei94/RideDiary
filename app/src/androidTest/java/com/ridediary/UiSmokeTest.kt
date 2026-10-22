package com.ridediary

import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ridediary.ui.RideDiaryScreen
import com.ridediary.ui.RideDiaryTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.core.Replay
import com.ridediary.data.*
import com.ridediary.recording.RecordingService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Edits and deletions use a disposable database, preserving the user's rides. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var app: RideDiaryApp
    private lateinit var original: RideRepository
    private lateinit var db: RideDatabase
    private lateinit var repository: RideRepository
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var id: String
    private lateinit var routeId: String
    private var previewContext: android.content.Context?=null
    private lateinit var originalRides: List<RideData>

    @Before fun setUp(): Unit=runBlocking {
        app=ApplicationProvider.getApplicationContext()
        app.ready.await()
        check(!RecordingService.active.value) { "Finish the current recording before running tests" }
        original=app.repository
        originalRides=original.rides.first()
        db=Room.inMemoryDatabaseBuilder(app,RideDatabase::class.java).build()
        repository=RideRepository(db)
        val start=LocalDate.now().minusDays(1).atTime(8,10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        id=repository.start(start)
        Replay.points().forEach { repository.update(id,it.t,start+it.t,it) }
        repository.update(id,220_000,start+220_000,finish=true)
        repository.createRoute(id,"东环通勤","WORK",100.0,70.0)
        routeId=repository.dao.routes().single().id
        app.repository=repository
        scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            activity=it
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    @After fun tearDown()=runBlocking {
        if(::scenario.isInitialized) scenario.close()
        if(::original.isInitialized) {
            app.repository=original
            assertEquals(originalRides,original.rides.first())
        }
        if(::db.isInitialized) db.close()
    }
    private fun text(res: Int)=(previewContext ?: activity).getString(res)
    private fun click(res: Int)=compose.onNodeWithText(text(if(res==R.string.statistics) R.string.nav_statistics else res)).performClick()
    private fun hold(tag: String)=compose.onNodeWithTag(tag).performScrollTo().performTouchInput { longClick() }
    private fun awaitRideDeleted() {
        compose.waitUntil(5_000) { runBlocking { repository.dao.ride(id)==null } }
        awaitText(R.string.empty_rides)
    }
    private fun awaitText(res: Int)=compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text(res)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String,tag: String?=null) {
        val target=File(app.cacheDir,"ui-previews").apply { mkdirs() }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        File(target,"$name.png").outputStream().use { output ->
            if(tag==null) {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output)
            } else {
                compose.onNodeWithTag(tag).assertExists()
                val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                instrumentation.waitForIdleSync()
                if(android.os.Build.VERSION.SDK_INT>=29) {
                    // Copy only this process's dialog window, excluding unrelated floating windows.
                    fun windowFor(view: android.view.View): android.view.Window? {
                        if(view is androidx.compose.ui.window.DialogWindowProvider) return view.window
                        if(view is android.view.ViewGroup) for(i in 0 until view.childCount) {
                            windowFor(view.getChildAt(i))?.let { return it }
                        }
                        return null
                    }
                    val done=java.util.concurrent.CountDownLatch(1)
                    var status=-1
                    var bitmap: android.graphics.Bitmap?=null
                    val thread=android.os.HandlerThread("RideDiaryPreview").apply { start() }
                    try {
                        instrumentation.runOnMainSync {
                            val window=checkNotNull(android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOfOrNull { windowFor(it) })
                            bitmap=android.graphics.Bitmap.createBitmap(window.decorView.width,window.decorView.height,android.graphics.Bitmap.Config.ARGB_8888)
                            android.view.PixelCopy.request(window,bitmap!!,{ result -> status=result; done.countDown() },android.os.Handler(thread.looper))
                        }
                        check(done.await(5,java.util.concurrent.TimeUnit.SECONDS) && status==android.view.PixelCopy.SUCCESS)
                        bitmap!!.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output)
                    } finally { bitmap?.recycle(); thread.quitSafely() }
                } else compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output)
            }
        }
    }
    private fun seedStatistics()=runBlocking {
        val source=repository.dao.ride(id)!!
        val base=repository.dao.routes().single()
        repository.dao.saveRoute(base.copy(id="stats-home",name="沿河返程",direction="HOME"))
        repository.classify(id,base,"WORK",true,"Preview source")
        for(day in 1..8) {
            val rideId="stats-$day"; val home=day%2==0; val factor=4L+day%3
            val start=LocalDate.now().minusDays(day.toLong()).atTime(if(home) 18 else 8,5+day*2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val elapsed=source.ride.elapsed*factor
            repository.dao.saveRide(source.ride.copy(id=rideId,startUtc=start,endUtc=start+elapsed,lastSavedUtc=start+elapsed,elapsed=elapsed,
                routeId=if(home) "stats-home" else base.id,direction=if(home) "HOME" else "WORK",excluded=false,exclusionReason=""))
            source.points.forEach { repository.dao.insertPoint(it.copy(rideId=rideId,t=it.t*factor,utc=start+it.t*factor)) }
            repository.dao.segments(source.segments.map { it.copy(rideId=rideId,from=it.from*factor,to=it.to*factor) })
        }
    }
    private fun awaitCount(count: Int) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("stats-ride-count") and hasText(count.toString())).fetchSemanticsNodes().isNotEmpty()
        }
    }
    @Test fun statisticsFiltersAndSelectableChartKeepTheSameDataScope() {
        seedStatistics()
        click(R.string.statistics)
        awaitCount(8)
        capture("statistics-overview")
        compose.onNodeWithTag("stats-composition").performScrollTo()
        capture("statistics-composition")
        compose.onNodeWithTag("stats-chart").performScrollTo()
        capture("statistics-chart")
        compose.onNodeWithTag("stats-bar-stats-1").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.clock_note)).performScrollTo()
        capture("statistics-rhythm")
        compose.onNodeWithTag("stats-direction-HOME").performScrollTo().performClick()
        awaitCount(4)
        compose.onNodeWithTag("stats-route-$routeId").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.stats_empty_title)).assertExists()
        compose.onNodeWithTag("stats-direction-ALL").performScrollTo().performClick()
        awaitCount(4)
        compose.onNodeWithContentDescription(text(R.string.previous_month)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.stats_empty_title)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.next_month)).performScrollTo().performClick()
        awaitCount(4)
        click(R.string.summary)
        compose.onNodeWithTag("ride-stats-1").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.details)).assertExists()
        capture("ride-detail-overview")
        compose.onNodeWithTag("ride-stops").performScrollTo()
        capture("ride-detail-stops")
        compose.onNodeWithTag("ride-comparison").performScrollTo()
        capture("ride-detail-comparison")
    }
    @Test fun englishLargeTextStatisticsAndActionSheetRemainUsable() {
        seedStatistics()
        scenario.onActivity { current ->
            val configuration=android.content.res.Configuration(current.resources.configuration).apply { setLocale(Locale.ENGLISH) }
            val english=current.createConfigurationContext(configuration)
            previewContext=english
            current.setContent {
                CompositionLocalProvider(LocalContext provides english,LocalDensity provides Density(current.resources.displayMetrics.density,1.65f),
                    LocalActivityResultRegistryOwner provides current,LocalOnBackPressedDispatcherOwner provides current) {
                    RideDiaryTheme { RideDiaryScreen(onStart={},onStop={},onLanguage={}) }
                }
            }
        }
        click(R.string.statistics)
        awaitCount(8)
        capture("statistics-english-large")
        compose.onNodeWithText(text(R.string.clock_note)).performScrollTo()
        capture("statistics-english-large-details")
        click(R.string.routes)
        hold("route-$routeId")
        capture("actions-english-large","action-sheet")
        compose.onNodeWithText(text(R.string.edit_route)).performScrollTo().assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText(text(R.string.edit_route)).assertDoesNotExist()
    }
    @Test fun fourTabsAndSystemBackReturnToJournalAfterRecreation() {
        compose.onNodeWithText("体验轨迹回放").assertDoesNotExist()
        compose.onNodeWithText("Try replay").assertDoesNotExist()
        click(R.string.summary)
        compose.onNodeWithTag("ride-$id").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.details)).assertExists()
        scenario.recreate()
        scenario.onActivity { activity=it }
        compose.onNodeWithText(text(R.string.details)).assertExists()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.history)).assertExists()
        compose.onNodeWithText(text(R.string.details)).assertDoesNotExist()
        click(R.string.statistics)
        compose.onNodeWithText(text(R.string.commute_days)).performScrollTo().assertIsDisplayed()
        click(R.string.routes)
        compose.onNodeWithTag("ride-$id").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.details)).assertExists()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.history)).assertExists()
        compose.onNodeWithTag("route-$routeId").assertDoesNotExist()
        click(R.string.commute)
        compose.onNodeWithText(text(R.string.start)).performScrollTo().assertIsDisplayed()
    }
    @Test fun longPressShowsMenuWithoutOpeningDetailsAndBackDismissesIt() {
        click(R.string.summary)
        compose.onNodeWithText(text(R.string.delete_ride)).assertDoesNotExist()
        hold("ride-$id")
        compose.onNodeWithText(text(R.string.delete_ride)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details)).assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText(text(R.string.delete_ride)).assertDoesNotExist()
        compose.onNodeWithTag("ride-$id").performClick()
        compose.onNodeWithText(text(R.string.details)).assertExists()
    }
    @Test fun cancelAndConfirmRideDeletionFromJournal() {
        click(R.string.summary)
        compose.onNodeWithText(text(R.string.delete_ride)).assertDoesNotExist()
        hold("ride-$id")
        click(R.string.delete_ride)
        capture("delete-confirmation","delete-confirmation")
        click(R.string.cancel)
        compose.onNodeWithTag("ride-$id").assertExists()
        hold("ride-$id")
        click(R.string.delete_ride)
        click(R.string.delete)
        awaitRideDeleted()
        compose.onNodeWithText(text(R.string.empty_rides)).assertExists()
        assertEquals(1,runBlocking { repository.dao.routes().size })
    }
    @Test fun deleteFromDetailReturnsToJournal() {
        click(R.string.summary)
        compose.onNodeWithTag("ride-$id").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.delete_ride)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.route_edit)).assertDoesNotExist()
        hold("ride-detail-$id")
        click(R.string.delete_ride)
        click(R.string.delete)
        awaitRideDeleted()
        compose.onNodeWithText(text(R.string.history)).assertExists()
        compose.onNodeWithText(text(R.string.details)).assertDoesNotExist()
    }
    @Test fun editAndDeleteRouteKeepRideAndUpdateAssociation() {
        click(R.string.routes)
        compose.onNodeWithText(text(R.string.edit_route)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.delete_route)).assertDoesNotExist()
        hold("route-$routeId")
        capture("route-actions","action-sheet")
        click(R.string.edit_route)
        capture("route-editor","editor-sheet")
        compose.onNodeWithText(text(R.string.route_name)).performScrollTo().performTextReplacement("Evening route")
        compose.onNodeWithText(text(R.string.home)).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.start_radius)).performScrollTo().performTextReplacement("120")
        compose.onNodeWithText(text(R.string.end_radius)).performScrollTo().performTextReplacement("80")
        click(R.string.save)
        compose.waitUntil(5_000) { runBlocking { repository.dao.routes().single().name=="Evening route" } }
        val edited=runBlocking { repository.dao.routes().single() }
        assertEquals("HOME",edited.direction)
        assertEquals(120.0,edited.startRadius,0.0)
        assertEquals(80.0,edited.endRadius,0.0)
        assertEquals("HOME",runBlocking { repository.dao.ride(id)!!.ride.direction })
        hold("route-$routeId")
        click(R.string.delete_route)
        click(R.string.cancel)
        compose.onNodeWithTag("route-$routeId").assertExists()
        hold("route-$routeId")
        click(R.string.delete_route)
        click(R.string.delete)
        compose.waitUntil(5_000) { runBlocking { repository.dao.routes().isEmpty() } }
        awaitText(R.string.empty_routes)
        compose.onNodeWithText(text(R.string.empty_routes)).assertExists()
        val ride=runBlocking { repository.dao.ride(id)!! }
        assertNull(ride.ride.routeId)
        assertEquals(111,ride.points.size)
    }
}
