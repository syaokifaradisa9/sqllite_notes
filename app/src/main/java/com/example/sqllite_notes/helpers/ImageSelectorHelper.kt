package com.example.sqllite_notes.helpers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import android.widget.LinearLayout

class ImageSelectorHelper(
    private val activity: AppCompatActivity,
    private val onImageSelected: (Uri) -> Unit
) {
    private val pickImageLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                onImageSelected(uri)
            }
        }
    }

    fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    fun createImageView(imageUri: Uri): ImageView {
        return ImageView(activity).apply {
            id = ViewGroup.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600
            ).apply {
                setMargins(16, 0, 16, 0)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(imageUri)
            contentDescription = "Note Image"
        }
    }
}