package com.example.sqllite_notes.helpers

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
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
        // Get the image dimensions to calculate aspect ratio
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        try {
            activity.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Calculate the aspect ratio - default to 4:3 if we can't determine
        val aspectRatio = if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        } else {
            4f / 3f  // Default aspect ratio if unable to determine
        }

        return ImageView(activity).apply {
            id = ViewGroup.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            adjustViewBounds = true  // This is key for maintaining aspect ratio
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(imageUri)
            contentDescription = "Note Image"
        }
    }
}