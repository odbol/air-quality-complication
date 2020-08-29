package com.odbol.wear.airquality.purpleair

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.io.File

const val PREFS_KEY_DOWNLOAD_ID = "PREFS_KEY_DOWNLOAD_ID"

class DownloadManagerRx(context: Context) {
    private val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager

    private val prefs = context.getSharedPreferences("DOWNLOAD_MANAGER_RX_", Context.MODE_PRIVATE)

    var downloadId: Long
        get() = prefs.getLong(PREFS_KEY_DOWNLOAD_ID, -1)
        set(value) = prefs.edit().putLong(PREFS_KEY_DOWNLOAD_ID, value).apply()

    fun startDownload(url: String, toFile: File, notificationTitle: CharSequence): Long {

        val request = DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) // Visibility of the download Notification
                .setDestinationUri(Uri.fromFile(toFile)) // Uri of the destination file
                .setTitle(notificationTitle) // Title of the Download Notification
                .setDescription("Downloading sensors") // Description of the Download Notification
                .setRequiresCharging(false) // Set if charging is required to begin the download
                .setAllowedOverMetered(true) // Set if download is allowed on Mobile network
                .setAllowedOverRoaming(false) // Set if download is allowed on roaming network


        val downloadID = downloadManager.enqueue(request) // enqueue puts the download request in the queue.

        Log.d(TAG, "Started download $downloadID")

        this.downloadId = downloadID

        return downloadID
    }

    /**
     * @return 1 if download finished. -1 if download failed or not started. Otherwise, the progress
     *         of the download.
     */
    fun getProgress(): Double {
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.moveToFirst()) {
            val status: Int = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_FAILED -> {
                    return -1.0
                }
                DownloadManager.STATUS_PAUSED -> {
                }
                DownloadManager.STATUS_PENDING -> {
                }
                DownloadManager.STATUS_RUNNING -> {
                    val total: Long = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total >= 0) {
                        val downloaded: Long = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        return (downloaded / total) - 0.01 // so we never get to the success state (1.0)
                        // if you use downloadmanger in async task, here you can use like this to display progress.
                        // Don't forget to do the division in long to get more digits rather than double.
                        //  publishProgress((int) ((downloaded * 100L) / total));
                    }
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    return 1.0
                }
            }
        }

        return -1.0
    }

    fun clearDownloadId() {
        downloadId = -1
    }
//
//    fun getDownloadedFile() {
//        downloadManager.getUriForDownloadedFile(downloadId)
//    }
}