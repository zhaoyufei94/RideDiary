package com.ridediary

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.data.RideDatabase
import com.ridediary.data.RideDocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Synthetic files only; never opens the daily database. */
@RunWith(AndroidJUnit4::class)
class RideDocumentsProviderTest {
    private lateinit var directory: File
    private lateinit var provider: RideDocumentsProvider
    @Before fun setUp() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        directory=Files.createTempDirectory(app.cacheDir.toPath(),"documents-test").toFile()
        val context=object: ContextWrapper(app) {
            override fun getDatabasePath(name: String)=File(directory,name)
        }
        provider=RideDocumentsProvider()
        provider.attachInfo(context,ProviderInfo().apply {
            authority=RideDocumentsProvider.AUTHORITY
            exported=true; grantUriPermissions=true
            readPermission="android.permission.MANAGE_DOCUMENTS"
            writePermission="android.permission.MANAGE_DOCUMENTS"
        })
    }
    @After fun tearDown() { provider.shutdown(); directory.deleteRecursively() }

    @Test fun browserSeesOnlyTripDatabaseAndCompanionFilesWithRequestedColumns() {
        val files=listOf(RideDatabase.NAME,RideDatabase.NAME+"-wal",RideDatabase.NAME+"-shm")
        (files+listOf("other.db","weather-secrets.xml")).forEach { File(directory,it).writeText("synthetic") }
        provider.queryRoots(arrayOf(Root.COLUMN_ROOT_ID)).use {
            assertTrue(it.moveToFirst()); assertEquals("trips",it.getString(0)); assertEquals(1,it.columnCount)
        }
        provider.queryChildDocuments("trips",arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_FLAGS),sortOrder=null).use {
            val ids=mutableSetOf<String>()
            while(it.moveToNext()) { ids+=it.getString(0); assertEquals(0,it.getInt(1)) }
            assertEquals(files.toSet(),ids)
        }
        provider.queryDocument("trips",arrayOf(Document.COLUMN_MIME_TYPE)).use {
            assertTrue(it.moveToFirst()); assertEquals(Document.MIME_TYPE_DIR,it.getString(0))
        }
        assertTrue(provider.isChildDocument("trips",RideDatabase.NAME))
        assertFalse(provider.isChildDocument("trips","weather-secrets.xml"))
        assertEquals(listOf("trips",RideDatabase.NAME),provider.findDocumentPath(null,RideDatabase.NAME).path)
        assertEquals("trips",provider.findDocumentPath(null,RideDatabase.NAME).rootId)
    }

    @Test fun readsAreAllowedButWritesTraversalAndSettingsAreRejected() {
        File(directory,RideDatabase.NAME).writeText("synthetic trip data")
        ParcelFileDescriptor.AutoCloseInputStream(provider.openDocument(RideDatabase.NAME,"r",null)).use {
            assertEquals("synthetic trip data",it.bufferedReader().readText())
        }
        for(mode in listOf("w","rw","rwt","wa")) {
            assertThrows(FileNotFoundException::class.java) { provider.openDocument(RideDatabase.NAME,mode,null) }
        }
        for(id in listOf("../shared_prefs/weather.xml","/data/data/com.ridediary/shared_prefs/weather.xml","other.db","trips")) {
            assertThrows(FileNotFoundException::class.java) { provider.openDocument(id,"r",null) }
        }
        assertEquals("synthetic trip data",File(directory,RideDatabase.NAME).readText())
        val signal=CancellationSignal().apply { cancel() }
        assertThrows(OperationCanceledException::class.java) { provider.openDocument(RideDatabase.NAME,"r",signal) }
    }

    @Test fun emptyStorageStillProvidesBrowsableRoot() {
        provider.queryChildDocuments("trips",null,sortOrder=null).use { assertEquals(0,it.count) }
        assertEquals(listOf("trips"),provider.findDocumentPath(null,"trips").path)
        assertThrows(FileNotFoundException::class.java) { provider.queryDocument(RideDatabase.NAME,null) }
        assertThrows(FileNotFoundException::class.java) { provider.queryChildDocuments("invalid",null,sortOrder=null) }
    }
}
