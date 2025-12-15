package com.dendy.market

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRefresh: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.mainProgressBar)
        btnRefresh = findViewById(R.id.btnRefresh)

        recycler.layoutManager = GridLayoutManager(this, 3)

        // Initial Load
        loadApps()

        // Button Listener
        btnRefresh.setOnClickListener {
            loadApps()
        }
    }

    private fun loadApps() {
        // Show loading state
        progressBar.visibility = View.VISIBLE
        btnRefresh.isEnabled = false // Prevent spamming
        recycler.alpha = 0.5f // Dim the list slightly while loading

        lifecycleScope.launch {
            try {
                val apps = RetrofitClient.api.getApps()

                // Update Adapter
                recycler.adapter = AppsAdapter(apps) { app ->
                    val intent = Intent(this@MainActivity, DetailsActivity::class.java)
                    intent.putExtra("APP_DATA", app)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Failed to connect to store", Toast.LENGTH_SHORT).show()
            } finally {
                // Restore UI state
                progressBar.visibility = View.GONE
                btnRefresh.isEnabled = true
                recycler.alpha = 1.0f
            }
        }
    }

    class AppsAdapter(
        private val apps: List<AppModel>,
        private val onClick: (AppModel) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.Holder>() {

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.imgIcon)
            val name: TextView = v.findViewById(R.id.tvAppName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = apps[position]
            holder.name.text = app.name

            val fullIconUrl = BASE_URL + app.iconUrl

            // 1. Create a "Loading Entertainment" Spinner
            val circularProgressDrawable = androidx.swiperefreshlayout.widget.CircularProgressDrawable(holder.itemView.context)
            circularProgressDrawable.strokeWidth = 5f
            circularProgressDrawable.centerRadius = 30f
            circularProgressDrawable.start()

            // 2. Load the image with the placeholder
            holder.icon.load(fullIconUrl) {
                crossfade(true)
                // This shows the spinner while downloading
                placeholder(circularProgressDrawable)
                // This shows the default icon if the server fails
                error(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener { onClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}