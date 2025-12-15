package com.dendy.market

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class DetailsActivity : AppCompatActivity() {

    private lateinit var appData: AppModel
    private lateinit var btnAction: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private var progressJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        appData = intent.getSerializableExtra("APP_DATA") as AppModel

        val imgIcon = findViewById<ImageView>(R.id.imgDetailIcon)
        val tvName = findViewById<TextView>(R.id.tvDetailName)
        val tvVersion = findViewById<TextView>(R.id.tvDetailVersion)
        btnAction = findViewById(R.id.btnAction)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        tvName.text = appData.name
        tvVersion.text = "Version: ${appData.version}"
        imgIcon.load(BASE_URL + appData.iconUrl)

        // 1. Initial State Check
        refreshUiState()

        // 2. Listen for install completion (to refresh button from "Installing" to "Open")
        registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onResume() {
        super.onResume()
        // If we left the screen and came back, refresh state immediately
        refreshUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        unregisterReceiver(onDownloadComplete)
    }

    // --- LOGIC CORE ---

    private fun refreshUiState() {
        // CASE A: Currently Downloading
        if (DownloadController.isDownloading(appData.packageName)) {
            setupDownloadingState()
            return
        }

        // CASE B: Not Downloading (Idle)
        progressBar.visibility = View.INVISIBLE
        tvStatus.visibility = View.INVISIBLE
        progressJob?.cancel() // Stop polling if we aren't downloading

        val installedCode = getInstalledVersionCode(appData.packageName)

        if (installedCode == -1) {
            // Not Installed
            btnAction.text = "Install"
            btnAction.isEnabled = true
            btnAction.setOnClickListener { startDownload() }
        } else if (appData.versionCode > installedCode) {
            // Update Available
            btnAction.text = "Update"
            btnAction.isEnabled = true
            btnAction.setOnClickListener { startDownload() }
        } else {
            // Up to Date
            btnAction.text = "Open"
            btnAction.isEnabled = true
            btnAction.setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(appData.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDownloadingState() {
        btnAction.text = "Downloading..."
        btnAction.isEnabled = false // Disable button so they can't click it twice
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE

        // Start polling for progress
        startProgressPoller()
    }

    // --- DOWNLOAD & INSTALLATION ---

    private fun startDownload() {
        // QoL: Delete old file first to avoid "app-1.apk", "app-2.apk" clutter
        deleteExistingApk()

        Toast.makeText(this, "Starting Download...", Toast.LENGTH_SHORT).show()

        val fullDownloadUrl = BASE_URL + appData.downloadUrl
        val fileName = "${appData.packageName}.apk"

        val request = DownloadManager.Request(Uri.parse(fullDownloadUrl))
            .setTitle(appData.name)
            .setDescription("Downloading update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE) // Don't notify completed, we handle it
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // Track state in Singleton
        DownloadController.trackDownload(appData.packageName, downloadId)

        refreshUiState()
    }

    private fun deleteExistingApk() {
        val fileName = "${appData.packageName}.apk"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun installApk() {
        val fileName = "${appData.packageName}.apk"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (file.exists()) {
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    // --- PROGRESS POLLING ---

    @SuppressLint("Range")
    private fun startProgressPoller() {
        if (progressJob?.isActive == true) return // Already polling

        val downloadId = DownloadController.getDownloadId(appData.packageName) ?: return
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        progressJob = lifecycleScope.launch {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)

                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        progressBar.progress = 100
                        tvStatus.text = "100%"
                        btnAction.text = "Installing..."
                        // We wait for the BroadcastReceiver to trigger the install
                        break
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        btnAction.text = "Retry"
                        btnAction.isEnabled = true
                        DownloadController.clearDownload(appData.packageName)
                        break
                    } else {
                        // Calculate Progress
                        val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                            progressBar.progress = progress
                            tvStatus.text = "$progress%"
                        }
                    }
                } else {
                    // Download cancelled or lost
                    DownloadController.clearDownload(appData.packageName)
                    refreshUiState()
                    break
                }
                cursor.close()
                delay(500) // Poll every 0.5 seconds
            }
        }
    }

    // --- SYSTEM EVENTS ---

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            // Check if the finished download matches THIS app
            if (id == DownloadController.getDownloadId(appData.packageName)) {
                DownloadController.clearDownload(appData.packageName)
                installApk()
                refreshUiState() // This will likely switch button to "Open" or "Install" depending on if user completes install
            }
        }
    }

    private fun getInstalledVersionCode(packageName: String): Int {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }
}