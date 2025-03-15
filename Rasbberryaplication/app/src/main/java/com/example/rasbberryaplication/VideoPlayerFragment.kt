package com.example.rasbberryaplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class VideoPlayerFragment : Fragment() {
    private lateinit var videoList: RecyclerView
    private lateinit var loopCheckBox: CheckBox
    private lateinit var uploadVideoButton: MaterialButton
    private lateinit var tailscaleService: TailscaleService
    private lateinit var videoAdapter: VideoAdapter
    private var isLooping = false
    private var currentVideoPath: String? = null

    private val selectVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleVideoSelection(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        videoList = view.findViewById(R.id.videoList)
        loopCheckBox = view.findViewById(R.id.loopCheckBox)
        uploadVideoButton = view.findViewById(R.id.uploadVideoButton)
        
        tailscaleService = TailscaleService(requireContext())
        setupVideoList()
        setupLoopControl()
        setupButtons()
        loadVideos()
    }

    private fun setupVideoList() {
        videoAdapter = VideoAdapter { video ->
            playServerVideo(video.path)
        }
        videoList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = videoAdapter
        }
    }

    private fun setupLoopControl() {
        loopCheckBox.setOnCheckedChangeListener { _, isChecked ->
            isLooping = isChecked
            currentVideoPath?.let { path ->
                lifecycleScope.launch {
                    try {
                        tailscaleService.playVideo(path, isLooping)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to update loop setting: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        uploadVideoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
            }
            selectVideo.launch(intent)
        }
    }

    private fun loadVideos() {
        lifecycleScope.launch {
            try {
                val videos = tailscaleService.getVideos()
                if (videos.isEmpty()) {
                    Toast.makeText(context, "No videos available", Toast.LENGTH_SHORT).show()
                }
                videoAdapter.updateVideos(videos)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load videos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleVideoSelection(uri: Uri) {
        lifecycleScope.launch {
            try {
                val contentResolver = requireContext().contentResolver
                
                // Get the file name from the URI
                var fileName = uri.lastPathSegment ?: "video"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            fileName = cursor.getString(displayNameIndex)
                        }
                    }
                }
                
                // Create a temporary file with the correct extension
                val extension = fileName.substringAfterLast('.', "mp4")
                val tempFile = File(requireContext().cacheDir, "temp_video.$extension")
                
                Toast.makeText(context, "Uploading video...", Toast.LENGTH_SHORT).show()
                
                // Copy the content to the temporary file
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                try {
                    // Upload the video
                    val response = tailscaleService.uploadVideo(tempFile)
                    
                    // Play the uploaded video
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Video uploaded successfully", Toast.LENGTH_SHORT).show()
                        playServerVideo(response.path)
                        loadVideos() // Refresh the video list
                    } else {
                        Toast.makeText(context, "Failed to upload video: ${response.error}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    // Clean up the temporary file
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to process video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playServerVideo(path: String) {
        lifecycleScope.launch {
            try {
                currentVideoPath = path
                Toast.makeText(context, "Starting video playback...", Toast.LENGTH_SHORT).show()
                tailscaleService.playVideo(path, isLooping)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to play video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 