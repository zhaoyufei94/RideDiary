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
    private lateinit var routeGroupId: String
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
        routeGroupId=repository.dao.routes().single().groupKey
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
    private fun hold(tag: String)=compose.onNodeWithTag(tag).performScrollTo().performTouchInput { longClick(androidx.compose.ui.geometry.Offset(24f,24f)) }
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
        repository.dao.saveRoute(base.copy(id="stats-home",name=base.name,direction="HOME"))
        repository.dao.saveRoute(base.copy(id="other-route",name="另一条路线",groupId="other-group"))
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
        awaitCount(4)
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
        compose.onNodeWithTag("stats-route-picker").performScrollTo().performClick()
        compose.onNodeWithTag("stats-route-other-group").performClick()
        compose.onNodeWithText(text(R.string.stats_empty_title)).assertExists()
        compose.onNodeWithTag("stats-route-picker").performScrollTo().performClick()
        compose.onNodeWithTag("stats-route-$routeGroupId").performClick()
        compose.onNodeWithTag("stats-direction-WORK").performScrollTo().performClick()
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
        awaitCount(4)
        capture("statistics-english-large")
        compose.onNodeWithText(text(R.string.clock_note)).performScrollTo()
        capture("statistics-english-large-details")
        click(R.string.routes)
        hold("route-$routeGroupId")
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
        compose.onNodeWithTag("route-$routeGroupId").assertDoesNotExist()
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
    private fun showPendingRecording(): String=runBlocking {
        val start=System.currentTimeMillis()
        val activeId=repository.start(start)
        Replay.points().forEach { repository.update(activeId,it.t,start+it.t,it) }
        scenario.onActivity { current -> current.setContent { RideDiaryTheme {
            RideDiaryScreen(onStart={},onLanguage={},
                onStop={ runBlocking { repository.update(activeId,220_000,start+220_000,finish=true) } },
                onDiscard={ runBlocking { repository.discardActive(activeId) } })
        } } }
        activeId
    }
    @Test fun liveCandidatesShowParallelAmbiguityAndCollectedOverlap() {
        val activeId=runBlocking {
            val base=repository.dao.routes().single()
            val shifted=base.route().points.map { it.copy(lat=it.lat+5/111_195.0) }
            repository.dao.saveRoute(base.copy(id="parallel-choice",name="沿河通勤",groupId="parallel-group",shape=RouteRecord.encode(shifted)))
            val now=System.currentTimeMillis()
            repository.start(now).also { repository.update(it,80_000,now+80_000,additionalFixes=Replay.points().filter { p->p.t<=80_000 }) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live-candidate-$routeId").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("live-route-candidates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("live-candidate-$routeId").assert(hasAnyDescendant(hasText("100.0%")))
        compose.onNodeWithTag("live-candidate-parallel-choice").assertExists()
        assertTrue(compose.onNodeWithTag("live-route-candidates").fetchSemanticsNode().size.height <= 80*app.resources.displayMetrics.density)
        assertTrue(compose.onNodeWithTag("live-ride-overview").fetchSemanticsNode().boundsInRoot.bottom <=
            compose.onNodeWithTag("live-route-candidates").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText(text(R.string.matching)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.unavailable)).assertDoesNotExist()
        assertTrue(repository.matching.value!!.report.ambiguous)
        assertNull(runBlocking { repository.dao.ride(activeId)!!.ride.routeId })
        capture("live-route-candidates-ambiguous")
    }
    @Test fun liveCandidatesReacquireReferenceStartLoopAndShowConfirmedRoute() {
        val now=System.currentTimeMillis()
        val trace=Replay.points()
        val activeId=runBlocking {
            val base=repository.dao.routes().single()
            val first=trace.first()
            val loop=listOf(first,first.copy(lon=first.lon-55/(111_195*kotlin.math.cos(Math.toRadians(first.lat)))),first)+base.route().points.drop(1)
            repository.dao.saveRoute(base.copy(shape=RouteRecord.encode(loop)))
            repository.start(now).also { repository.update(it,10_000,now+10_000,additionalFixes=trace.filter { p->p.t<=10_000 }) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live-candidate-$routeId").fetchSemanticsNodes().isNotEmpty() }
        val initial=repository.matching.value!!
        assertNull(initial.report.match)
        runBlocking { repository.update(activeId,80_000,now+80_000,additionalFixes=trace.filter { it.t in 10_001..80_000 }) }
        compose.waitUntil(5_000) {
            repository.matching.value?.report?.match?.route?.id==routeId
        }
        compose.onNodeWithTag("live-candidate-$routeId").performScrollTo().assertIsDisplayed()
        assertTrue(repository.matching.value!!.report.candidates.single().overlap>.85)
        assertEquals(routeId,runBlocking { repository.dao.ride(activeId)!!.ride.routeId })
        compose.onNodeWithText(text(R.string.matched)).assertDoesNotExist()
        capture("live-route-candidates-confirmed")
    }
    @Test fun liveLocationRecoversWhileDatabaseSnapshotIsStale() {
        val priorQuality=RecordingService.quality.value
        val priorFix=RecordingService.latestFixRealtime.value
        try {
            val activeId=runBlocking {
                val now=System.currentTimeMillis()
                repository.start(now).also { repository.update(it,120_000,now+120_000) }
            }
            RecordingService.active.value=true
            RecordingService.quality.value="GOOD"
            RecordingService.latestFixRealtime.value=android.os.SystemClock.elapsedRealtime()-20_000
            awaitText(R.string.quality_searching)
            compose.onNodeWithTag("location-status-problem").assertExists()
            compose.onNodeWithTag("live-recording-header").assert(hasAnyDescendant(hasText(text(R.string.ride_title))))
            compose.onNodeWithTag("live-recording-header").assert(hasAnyDescendant(hasText(text(R.string.quality_searching))))
            // A new callback must recover the indicator even before any DB write completes.
            RecordingService.latestFixRealtime.value=android.os.SystemClock.elapsedRealtime()
            awaitText(R.string.quality_good)
            compose.onNodeWithTag("location-status-good").assertExists()
            compose.onNodeWithTag("location-status-problem").assertDoesNotExist()
            val titleBounds=compose.onNodeWithText(text(R.string.ride_title)).fetchSemanticsNode().boundsInRoot
            val statusBounds=compose.onNodeWithText(text(R.string.quality_good)).fetchSemanticsNode().boundsInRoot
            assertTrue(statusBounds.left>titleBounds.right)
            assertTrue(kotlin.math.abs(statusBounds.center.y-titleBounds.center.y)<2*app.resources.displayMetrics.density)
            capture("live-location-normal")
            assertEquals(120_000,runBlocking { repository.dao.ride(activeId)!!.ride.elapsed })
            assertTrue(runBlocking { repository.dao.ride(activeId)!!.points.isEmpty() })
            RecordingService.quality.value="POOR"
            awaitText(R.string.quality_poor)
            compose.onNodeWithTag("location-status-problem").assertExists()
            compose.onNodeWithTag("location-status-good").assertDoesNotExist()
            capture("live-location-weak")
            RecordingService.quality.value="GOOD"
            RecordingService.latestFixRealtime.value=android.os.SystemClock.elapsedRealtime()
            awaitText(R.string.quality_good)
        } finally {
            RecordingService.active.value=false
            RecordingService.quality.value=priorQuality
            RecordingService.latestFixRealtime.value=priorFix
        }
    }
    @Test fun notificationIntentOpensReviewAgainAfterCancelAndRecreation() {
        val activeId=runBlocking { repository.start(System.currentTimeMillis()) }
        fun request() { scenario.onActivity { it.startActivity(android.content.Intent(it,MainActivity::class.java).setAction(RecordingService.REVIEW_STOP).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)) } }
        request()
        awaitText(R.string.finish_confirm)
        click(R.string.cancel)
        assertEquals("RECORDING",runBlocking { repository.dao.ride(activeId)!!.ride.state })
        scenario.recreate(); scenario.onActivity { activity=it }
        request()
        awaitText(R.string.finish_confirm)
        compose.onNodeWithText(text(R.string.delete)).assertExists()
        compose.onNodeWithText(text(R.string.save)).assertExists()
        click(R.string.cancel)
    }
    @Test fun arrivalConfirmationCanCancelThenDiscardWithoutSaving() {
        val activeId=showPendingRecording()
        awaitText(R.string.arrival_review_title)
        compose.onNodeWithText(text(R.string.save)).assertExists()
        compose.onNodeWithText(text(R.string.delete)).assertExists()
        capture("recording-finish-confirmation","finish-confirmation")
        click(R.string.cancel)
        compose.onNodeWithText(text(R.string.arrival_review_title)).assertDoesNotExist()
        assertEquals("RECORDING",runBlocking { repository.dao.ride(activeId)!!.ride.state })
        compose.onNodeWithText(text(R.string.finish)).performScrollTo().performClick()
        awaitText(R.string.delete)
        click(R.string.delete)
        compose.waitUntil(5_000) { runBlocking { repository.dao.ride(activeId)==null } }
        assertNotNull(runBlocking { repository.dao.ride(id) })
    }
    @Test fun arrivalSaveCompletesAndOpensAutomaticRouteCorrection() {
        val activeId=showPendingRecording()
        awaitText(R.string.arrival_review_title)
        click(R.string.save)
        compose.waitUntil(5_000) { runBlocking { repository.dao.ride(activeId)?.ride?.state=="COMPLETED" } }
        awaitText(R.string.choose_route)
        compose.onNodeWithTag("route-create-new").assertExists()
        compose.onNodeWithTag("route-choice-$routeGroupId-WORK").assertIsSelected()
        click(R.string.cancel)
        assertEquals(routeId,runBlocking { repository.dao.ride(activeId)!!.ride.routeId })
    }
    @Test fun routeCorrectionListsGroupsAndCreatesMissingDirection() {
        click(R.string.summary)
        compose.onNodeWithTag("ride-$id").performScrollTo().performClick()
        hold("ride-detail-$id")
        click(R.string.route_edit)
        compose.onNodeWithTag("route-create-new").assertExists()
        compose.onNodeWithTag("route-choice-$routeGroupId").assertExists()
        compose.onNodeWithTag("route-choice-$routeGroupId-HOME").performScrollTo().performClick()
        click(R.string.save)
        compose.onNodeWithText(text(R.string.route_name)).assertTextContains("东环通勤")
        capture("route-add-return","editor-sheet")
        click(R.string.save)
        compose.waitUntil(5_000) { runBlocking { repository.dao.routes().size==2 } }
        val variants=runBlocking { repository.dao.routes() }
        assertEquals(setOf(routeGroupId),variants.map { it.groupKey }.toSet())
        assertEquals(setOf("WORK","HOME"),variants.map { it.direction }.toSet())
        assertEquals("HOME",runBlocking { repository.dao.ride(id)!!.ride.direction })
    }
    @Test fun editAndDeleteRouteKeepRideAndUpdateAssociation() {
        click(R.string.routes)
        compose.onNodeWithText(text(R.string.edit_route)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.delete_route)).assertDoesNotExist()
        hold("route-$routeGroupId")
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
        hold("route-$routeGroupId")
        click(R.string.delete_route)
        click(R.string.cancel)
        compose.onNodeWithTag("route-$routeGroupId").assertExists()
        hold("route-$routeGroupId")
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
