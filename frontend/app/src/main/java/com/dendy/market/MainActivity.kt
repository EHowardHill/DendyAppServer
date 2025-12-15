package com.dendy.market

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recycler = findViewById<RecyclerView>(R.id.recyclerView)
        recycler.layoutManager = GridLayoutManager(this, 3) // 3 columns

        lifecycleScope.launch {
            try {
                val apps = RetrofitClient.api.getApps()
                recycler.adapter = AppsAdapter(apps) { app ->
                    val intent = Intent(this@MainActivity, DetailsActivity::class.java)
                    intent.putExtra("APP_DATA", app)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
            // Construct full icon URL
            val fullIconUrl = BASE_URL + app.iconUrl
            holder.icon.load(fullIconUrl)
            holder.itemView.setOnClickListener { onClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}