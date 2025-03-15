package com.example.rasbberryaplication

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.rasbberryaplication.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var timeTextView: TextView
    private lateinit var connectButton: MaterialButton
    private lateinit var videoButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var screensaverButton: MaterialButton
    private lateinit var connectionIndicator: View
    private lateinit var tailscaleService: TailscaleService
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        timeTextView = binding.timeTextView
        tailscaleService = TailscaleService(this)
        setupUI()
        startTimeUpdates()
        updateConnectionStatus(false)
    }

    private fun setupUI() {
        binding.connectButton.setOnClickListener {
            connectToTailscale()
        }

        binding.videoButton.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showVideoPlayer()
        }

        binding.clearButton.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            clearScreen()
        }

        binding.screensaverButton.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showScreensaver()
        }

        // Initially disable buttons except connect
        updateButtonStates(false)
    }

    private fun updateButtonStates(enabled: Boolean) {
        binding.videoButton.isEnabled = enabled
        binding.clearButton.isEnabled = enabled
        binding.screensaverButton.isEnabled = enabled
    }

    private fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        binding.connectionIndicator.isSelected = connected
        updateButtonStates(connected)
    }

    private fun connectToTailscale() {
        lifecycleScope.launch {
            try {
                val connected = tailscaleService.connect()
                updateConnectionStatus(connected)
                val message = if (connected) "Connected to Tailscale" else "Failed to connect"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                updateConnectionStatus(false)
                Toast.makeText(this@MainActivity, "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showVideoPlayer() {
        val fragment = VideoPlayerFragment()
        loadFragment(fragment)
        currentFragment = fragment
    }

    private fun clearScreen() {
        if (!isConnected) {
            Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val response = tailscaleService.clearScreen()
                if (response.isSuccessful) {
                    // Remove current fragment if it exists
                    currentFragment?.let {
                        supportFragmentManager.beginTransaction()
                            .remove(it)
                            .commit()
                    }
                    currentFragment = null
                    Toast.makeText(this@MainActivity, response.message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to clear screen: ${response.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showScreensaver() {
        if (!isConnected) {
            Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fragment = ScreenSaverFragment()
        loadFragment(fragment)
        currentFragment = fragment
    }

    private fun startTimeUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                updateDateTime()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateDateTime() {
        val currentTime = System.currentTimeMillis()
        binding.timeTextView.text = dateFormat.format(Date(currentTime))
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAILSCALE_HOST = "your-tailscale-ip"  // Replace with your Tailscale IP
        private const val TAILSCALE_PORT = 8080  // Replace with your desired port
    }
}