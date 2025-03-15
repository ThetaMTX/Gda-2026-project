package com.example.rasbberryaplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.rasbberryaplication.databinding.ItemImageBinding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Protocol
import android.graphics.BitmapFactory
import android.util.Log

class ImageAdapter(
    private val onItemClick: (ImageItem) -> Unit,
    private val tailscaleService: TailscaleService
) : ListAdapter<ImageItem, ImageAdapter.ImageViewHolder>(ImageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding, onItemClick, tailscaleService)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ImageViewHolder(
        private val binding: ItemImageBinding,
        private val onItemClick: (ImageItem) -> Unit,
        private val tailscaleService: TailscaleService
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ImageItem) {
            binding.imageName.text = item.name
            
            // Simple direct image loading for thumbnail
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .protocols(listOf(Protocol.HTTP_1_1))
                .build()

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(item.url)
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bitmap = BitmapFactory.decodeStream(response.body?.byteStream())
                        withContext(Dispatchers.Main) {
                            binding.imageView.setImageBitmap(bitmap)
                        }
                    } else {
                        Log.e("ImageAdapter", "Failed to load image: ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.e("ImageAdapter", "Error loading image: ${e.message}")
                }
            }

            // Handle click to display full image
            binding.root.setOnClickListener {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        tailscaleService.playImage(item.path)
                        withContext(Dispatchers.Main) {
                            onItemClick(item)
                        }
                    } catch (e: Exception) {
                        Log.e("ImageAdapter", "Error displaying image: ${e.message}")
                        withContext(Dispatchers.Main) {
                            // Show error toast
                            android.widget.Toast.makeText(
                                binding.root.context,
                                "Failed to display image: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private class ImageDiffCallback : DiffUtil.ItemCallback<ImageItem>() {
        override fun areItemsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
            return oldItem == newItem
        }
    }

    override fun submitList(list: List<ImageItem>?) {
        // Make a copy of the list to prevent modification
        super.submitList(list?.toList())
    }
} 