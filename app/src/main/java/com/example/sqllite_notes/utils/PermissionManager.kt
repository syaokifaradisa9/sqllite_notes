package com.example.sqllite_notes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionManager(
    private val activity: AppCompatActivity,
    private val onPermissionGranted: (PermissionType) -> Unit,
    private val onPermissionDenied: (PermissionType) -> Unit = { _ -> }
) {

    enum class PermissionType {
        IMAGE, AUDIO, BOTH
    }

    private var currentPermissionRequest = PermissionType.IMAGE

    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val mediaPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true

        if (mediaPermissionGranted) {
            onPermissionGranted(currentPermissionRequest)
        } else {
            val permissionsToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            val shouldShowRationale = permissionsToCheck.any {
                activity.shouldShowRequestPermissionRationale(it)
            }

            if (shouldShowRationale) {
                showPermissionRationaleDialog(currentPermissionRequest)
            } else {
                showPermissionDeniedDialog()
            }

            onPermissionDenied(currentPermissionRequest)
        }
    }

    fun checkAndRequestPermission(permissionType: PermissionType) {
        currentPermissionRequest = permissionType

        val permissions = getPermissionsForType(permissionType)

        if (permissions.all { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }) {
            // Permission already granted
            onPermissionGranted(permissionType)
        } else {
            // Request permission
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun getPermissionsForType(permissionType: PermissionType): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (permissionType) {
                PermissionType.IMAGE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                PermissionType.AUDIO -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                PermissionType.BOTH -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionRationaleDialog(permissionType: PermissionType) {
        val message = when (permissionType) {
            PermissionType.IMAGE -> "Aplikasi memerlukan akses ke gambar untuk menambahkan gambar ke catatan Anda."
            PermissionType.AUDIO -> "Aplikasi memerlukan akses ke audio untuk menambahkan rekaman ke catatan Anda."
            PermissionType.BOTH -> "Aplikasi memerlukan akses ke media untuk menambahkan gambar dan audio ke catatan Anda."
        }

        AlertDialog.Builder(activity)
            .setTitle("Izin Diperlukan")
            .setMessage(message)
            .setPositiveButton("Coba Lagi") { _, _ ->
                requestPermissionLauncher.launch(getPermissionsForType(currentPermissionRequest))
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    activity,
                    "Fitur ini memerlukan izin media untuk berfungsi",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Izin Media Diperlukan")
            .setMessage("Aplikasi memerlukan akses ke media untuk berfungsi dengan baik. Silakan aktifkan izin di pengaturan aplikasi.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    activity,
                    "Fitur ini tidak dapat digunakan tanpa izin media",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }
}