package com.aipet.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aipet.app.service.PetService

class MainActivity : ComponentActivity() {

    // Daftar semua izin yang dibutuhkan aplikasi
    private val requiredPermissions = mutableListOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Pastikan semua izin penting (Kamera & Mic) disetujui
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkOverlayPermission()
        } else {
            Toast.makeText(this, "AI Pet membutuhkan semua izin untuk berfungsi penuh.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Memeriksa konfigurasi sensor AI Pet...")
            }
        }
        
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val hasAllPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasAllPermissions) {
            checkOverlayPermission()
        } else {
            permissionsLauncher.launch(requiredPermissions)
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan izin 'Tampilkan di atas aplikasi lain'", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else {
            startPetService()
        }
    }

    override fun onResume() {
        super.onResume()
        val hasAllPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (hasAllPermissions && Settings.canDrawOverlays(this)) {
            startPetService()
        }
    }

    private fun startPetService() {
        val intent = Intent(this, PetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }
}
