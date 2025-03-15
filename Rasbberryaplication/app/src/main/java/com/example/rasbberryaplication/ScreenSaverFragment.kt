package com.example.rasbberryaplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rasbberryaplication.databinding.FragmentScreenSaverBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class ScreenSaverFragment : Fragment() {
    private var _binding: FragmentScreenSaverBinding? = null
    private val binding get() = _binding!!
    private lateinit var tailscaleService: TailscaleService
    private lateinit var imageAdapter: ImageAdapter
    private var selectedImageUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            selectedImageUri = it
            binding.uploadImageButton.isEnabled = true
            binding.selectImageButton.text = "Change Image"
            // Show the filename
            val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected Image"
            Toast.makeText(context, "Selected: $filename", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScreenSaverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tailscaleService = TailscaleService(requireContext())
        setupUI()
        loadImages()
    }

    private fun setupUI() {
        imageAdapter = ImageAdapter(
            onItemClick = { image ->
                // Optional: Add any additional handling when an image is clicked
            },
            tailscaleService = tailscaleService
        )

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = imageAdapter
        }

        // Initially disable upload button
        binding.uploadImageButton.isEnabled = false

        binding.selectImageButton.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        binding.uploadImageButton.setOnClickListener {
            selectedImageUri?.let { uri ->
                uploadImage(uri)
            }
        }
    }

    private fun loadImages() {
        lifecycleScope.launch {
            try {
                val images = tailscaleService.getImages()
                imageAdapter.submitList(images)
                if (images.isEmpty()) {
                    Toast.makeText(context, "No images found. Try uploading some!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading images: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.uploadImageButton.isEnabled = false
                binding.uploadImageButton.text = "Uploading..."
                
                val file = createTempFileFromUri(uri)
                val response = tailscaleService.uploadImage(file)
                
                if (response.isSuccessful) {
                    Toast.makeText(context, "Image uploaded successfully", Toast.LENGTH_SHORT).show()
                    // Reset UI
                    binding.selectImageButton.text = "Select Image"
                    binding.uploadImageButton.text = "Upload Image"
                    selectedImageUri = null
                    // Refresh the list
                    loadImages()
                } else {
                    Toast.makeText(context, "Failed to upload image: ${response.error}", Toast.LENGTH_SHORT).show()
                    binding.uploadImageButton.text = "Retry Upload"
                    binding.uploadImageButton.isEnabled = true
                }
                
                file.delete() // Clean up temp file
            } catch (e: Exception) {
                Toast.makeText(context, "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.uploadImageButton.text = "Retry Upload"
                binding.uploadImageButton.isEnabled = true
            }
        }
    }

    private fun createTempFileFromUri(uri: Uri): File {
        // Get the original file name and extension
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        val originalFileName = cursor?.use { c ->
            val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && nameIndex >= 0) {
                c.getString(nameIndex)
            } else {
                null
            }
        } ?: uri.lastPathSegment ?: "image.jpg"

        // Create a temporary file with the original name
        val file = File(requireContext().cacheDir, originalFileName)
        
        // Copy the file content
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Failed to open input stream")

        return file
    }

    private fun activateScreensaver() {
        lifecycleScope.launch {
            try {
                val response = tailscaleService.activateScreensaver()
                if (response.isSuccessful) {
                    Toast.makeText(context, "Screensaver activated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to activate screensaver: ${response.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearScreen() {
        lifecycleScope.launch {
            try {
                val response = tailscaleService.clearScreen()
                if (response.isSuccessful) {
                    Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to clear screen: ${response.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 