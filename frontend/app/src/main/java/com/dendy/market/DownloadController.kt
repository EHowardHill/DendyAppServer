package com.dendy.market

object DownloadController {
    // Maps Package Name -> DownloadManager ID
    private val activeDownloads = mutableMapOf<String, Long>()

    fun isDownloading(packageName: String): Boolean {
        return activeDownloads.containsKey(packageName)
    }

    fun getDownloadId(packageName: String): Long? {
        return activeDownloads[packageName]
    }

    fun trackDownload(packageName: String, id: Long) {
        activeDownloads[packageName] = id
    }

    fun clearDownload(packageName: String) {
        activeDownloads.remove(packageName)
    }
}