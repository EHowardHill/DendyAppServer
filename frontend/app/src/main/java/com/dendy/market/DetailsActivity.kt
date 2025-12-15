package com.dendy.market

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    private lateinit var tvInstalledVersion: TextView
    private var progressJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        // Retrieve data passed from Main Activity
        appData = intent.getSerializableExtra("APP_DATA") as AppModel

        // Bind Views
        val imgIcon = findViewById<ImageView>(R.id.imgDetailIcon)
        val tvName = findViewById<TextView>(R.id.tvDetailName)
        val tvVersion = findViewById<TextView>(R.id.tvDetailVersion)
        tvInstalledVersion = findViewById(R.id.tvInstalledVersion)
        btnAction = findViewById(R.id.btnAction)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        // Set Static Data
        tvName.text = appData.name
        tvVersion.text = "Latest Version: ${appData.version}"
        imgIcon.load(BASE_URL + appData.iconUrl)

        // Initial State Check
        refreshUiState()

        // Register Receiver (Fixed for Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh state when returning to the app (e.g. after installing)
        refreshUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        unregisterReceiver(onDownloadComplete)
    }

    // --- MAIN LOGIC ---

    private fun refreshUiState() {
        // 1. Check if we are currently downloading
        if (DownloadController.isDownloading(appData.packageName)) {
            setupDownloadingState()
            return
        }

        // 2. Reset specific download UI elements if idle
        progressBar.visibility = View.INVISIBLE
        tvStatus.visibility = View.INVISIBLE
        progressJob?.cancel()

        // 3. Get currently installed data
        val installedCode = getInstalledVersionCode(appData.packageName)
        val installedVerName = getInstalledVersionName(appData.packageName)
        val fileExists = apkFileExists()

        // 4. Update the "Installed: ..." text
        if (installedCode == -1) {
            tvInstalledVersion.text = "Installed: Not Installed"
        } else {
            tvInstalledVersion.text = "Installed: $installedVerName (Code: $installedCode)"
        }

        // 5. Determine Button State
        if (installedCode == -1) {
            // --- NOT INSTALLED ---
            if (fileExists) {
                // File exists -> "INSTALL"
                btnAction.text = "INSTALL"
                btnAction.isEnabled = true
                btnAction.setOnClickListener { installApk() }
            } else {
                // No file -> "DOWNLOAD"
                btnAction.text = "DOWNLOAD"
                btnAction.isEnabled = true
                btnAction.setOnClickListener { startDownload() }
            }
        } else {
            // --- INSTALLED ---
            if (appData.versionCode > installedCode) {
                // Server version is newer -> "UPDATE"
                btnAction.text = "UPDATE"
                btnAction.isEnabled = true
                btnAction.setOnClickListener { startDownload() }
            } else {
                // App is up to date -> "CHECK FOR UPDATES"
                btnAction.text = "CHECK FOR UPDATES"
                btnAction.isEnabled = true
                btnAction.setOnClickListener {
                    if (appData.versionCode > installedCode) {
                        refreshUiState()
                    } else {
                        Toast.makeText(this, "App is up to date!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupDownloadingState() {
        btnAction.text = "Downloading..."
        btnAction.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        startProgressPoller()
    }

    // --- DOWNLOAD & INSTALLATION ---

    private fun startDownload() {
        deleteExistingApk() // Clear old files first

        Toast.makeText(this, "Starting Download...", Toast.LENGTH_SHORT).show()

        val fullDownloadUrl = BASE_URL + appData.downloadUrl
        val fileName = "${appData.packageName}.apk"

        val request = DownloadManager.Request(Uri.parse(fullDownloadUrl))
            .setTitle(appData.name)
            .setDescription("Downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

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
        } else {
            Toast.makeText(this, "Error: File not found. Downloading again...", Toast.LENGTH_SHORT).show()
            // Fallback: If file is missing for some reason, download it again
            startDownload()
        }
    }

    // --- PROGRESS POLLING ---

    @SuppressLint("Range")
    private fun startProgressPoller() {
        if (progressJob?.isActive == true) return

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
                        // We wait for the BroadcastReceiver to trigger the final state change
                        break
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        btnAction.text = "Retry"
                        btnAction.isEnabled = true
                        DownloadController.clearDownload(appData.packageName)
                        break
                    } else {
                        // Update Progress
                        val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                            progressBar.progress = progress
                            tvStatus.text = "$progress%"
                        }
                    }
                } else {
                    // Download lost
                    DownloadController.clearDownload(appData.packageName)
                    refreshUiState()
                    break
                }
                cursor.close()
                delay(500) // Update every half second
            }
        }
    }

    // --- SYSTEM EVENTS & HELPERS ---

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            // Verify this download belongs to this specific app page
            if (id == DownloadController.getDownloadId(appData.packageName)) {
                DownloadController.clearDownload(appData.packageName)
                installApk()
                refreshUiState()
            }
        }
    }

    private fun apkFileExists(): Boolean {
        val fileName = "${appData.packageName}.apk"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        return file.exists()
    }

    private fun getInstalledVersionCode(packageName: String): Int {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                pInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    private fun getInstalledVersionName(packageName: String): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            // Fix: If pInfo.versionName is null, return "N/A" instead
            pInfo.versionName ?: "N/A"
        } catch (e: PackageManager.NameNotFoundException) {
            "N/A"
        }
    }
}