package com.example.rasbberryaplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class VideoAdapter(
    private val onVideoPlay: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private var videos: List<VideoItem> = emptyList()

    fun updateVideos(newVideos: List<VideoItem>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video)
    }

    override fun getItemCount(): Int = videos.size

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val videoName: TextView = itemView.findViewById(R.id.videoName)
        private val playButton: MaterialButton = itemView.findViewById(R.id.playButton)

        fun bind(video: VideoItem) {
            videoName.text = video.name
            playButton.setOnClickListener {
                onVideoPlay(video)
            }
            itemView.setOnClickListener {
                onVideoPlay(video)
            }
        }
    }
} 