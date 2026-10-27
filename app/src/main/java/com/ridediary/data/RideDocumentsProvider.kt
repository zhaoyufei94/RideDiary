package com.ridediary.data

import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.ridediary.R
import java.io.File
import java.io.FileNotFoundException

/** A read-only view of trip storage, accessed through the system document browser. */
class RideDocumentsProvider : DocumentsProvider() {
    companion object {
        const val AUTHORITY="com.ridediary.documents"
        const val ROOT_ID="trips"
        private val FILE_NAMES=setOf(RideDatabase.NAME,RideDatabase.NAME+"-wal",RideDatabase.NAME+"-shm")
        private val ROOT_COLUMNS=arrayOf(Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,Root.COLUMN_FLAGS,Root.COLUMN_MIME_TYPES,Root.COLUMN_ICON)
        private val DOCUMENT_COLUMNS=arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED)

        fun browseIntent()=Intent(Intent.ACTION_VIEW).setDataAndType(
            DocumentsContract.buildRootUri(AUTHORITY,ROOT_ID),Document.MIME_TYPE_DIR)

        fun pickerIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*").putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri(AUTHORITY,ROOT_ID))
    }

    override fun onCreate()=true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val ctx=requireNotNull(context)
        return MatrixCursor(projection ?: ROOT_COLUMNS).apply {
            newRow().apply {
                put(this,Root.COLUMN_ROOT_ID,ROOT_ID)
                put(this,Root.COLUMN_DOCUMENT_ID,ROOT_ID)
                put(this,Root.COLUMN_TITLE,ctx.getString(R.string.app_brand))
                put(this,Root.COLUMN_SUMMARY,ctx.getString(R.string.data_location))
                put(this,Root.COLUMN_FLAGS,Root.FLAG_LOCAL_ONLY)
                put(this,Root.COLUMN_MIME_TYPES,"application/octet-stream")
                put(this,Root.COLUMN_ICON,R.drawable.ic_ridediary_logo)
            }
        }
    }

    override fun queryDocument(documentId: String,projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply { include(documentId) }

    override fun queryChildDocuments(parentDocumentId: String,projection: Array<out String>?,sortOrder: String?): Cursor {
        if(parentDocumentId!=ROOT_ID) throw FileNotFoundException("Unknown directory")
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            FILE_NAMES.forEach { id -> if(file(id).isFile) include(id) }
        }
    }

    override fun isChildDocument(parentDocumentId: String,documentId: String)=
        parentDocumentId==ROOT_ID && documentId in FILE_NAMES && file(documentId).isFile

    override fun findDocumentPath(parentDocumentId: String?,childDocumentId: String): DocumentsContract.Path {
        if(childDocumentId!=ROOT_ID && !file(childDocumentId).isFile) throw FileNotFoundException("Missing document")
        val path=when(parentDocumentId) {
            null,ROOT_ID -> if(childDocumentId==ROOT_ID) listOf(ROOT_ID) else listOf(ROOT_ID,childDocumentId)
            childDocumentId -> listOf(childDocumentId)
            else -> throw FileNotFoundException("Unknown parent")
        }
        return DocumentsContract.Path(if(parentDocumentId==null) ROOT_ID else null,path)
    }

    override fun openDocument(documentId: String,mode: String,signal: CancellationSignal?): ParcelFileDescriptor {
        if(mode!="r") throw FileNotFoundException("Trip storage is read-only")
        signal?.throwIfCanceled()
        return ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun file(id: String): File {
        if(id !in FILE_NAMES) throw FileNotFoundException("Unknown document")
        val directory=requireNotNull(context).getDatabasePath(RideDatabase.NAME).parentFile!!
        val file=File(directory,id).canonicalFile
        if(file.parentFile!=directory.canonicalFile) throw FileNotFoundException("Invalid document")
        return file
    }

    private fun MatrixCursor.include(id: String) {
        val directory=id==ROOT_ID
        val file=if(directory) null else file(id).also { if(!it.isFile) throw FileNotFoundException("Missing document") }
        newRow().apply {
            put(this,Document.COLUMN_DOCUMENT_ID,id)
            put(this,Document.COLUMN_DISPLAY_NAME,if(directory) requireNotNull(context).getString(R.string.data_location) else id)
            put(this,Document.COLUMN_MIME_TYPE,if(directory) Document.MIME_TYPE_DIR else "application/octet-stream")
            put(this,Document.COLUMN_FLAGS,0)
            put(this,Document.COLUMN_SIZE,file?.length())
            put(this,Document.COLUMN_LAST_MODIFIED,file?.lastModified())
        }
    }

    // Document browsers may request any subset of columns.
    private fun put(row: MatrixCursor.RowBuilder,column: String,value: Any?) {
        row.add(column,value)
    }
}
