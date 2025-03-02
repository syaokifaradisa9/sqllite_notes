package com.example.sqllite_notes.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.example.sqllite_notes.models.NotePart
import java.io.ByteArrayOutputStream
import java.io.IOException

object MultimediaUtils {
    private const val TAG = "MediaUtils"
    private const val IMAGE_PREFIX = "IMG:"
    private const val TEXT_PREFIX = "TXT:"
    private const val AUDIO_PREFIX = "AUDIO:"
    private const val AUDIO_TITLE_SEPARATOR = "::TITLE::"
    private const val MAX_IMAGE_DIMENSION = 1024
    private const val JPEG_QUALITY = 80

    fun uriToBase64(contentResolver: ContentResolver, imageUri: Uri): String? {
        return try {
            // Mendapatkan bitmap dari URI gambar
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            }

            if (bitmap == null) {
                Log.e(TAG, "Gagal mendekode bitmap dari URI")
                return null
            }

            // Mengubah ukuran bitmap untuk optimasi penyimpanan
            val resizedBitmap = resizeBitmap(bitmap)

            // Mengkonversi bitmap ke Base64 string
            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            if (byteArray.isEmpty()) {
                Log.e(TAG, "Gambar terkompresi menghasilkan array byte kosong")
                return null
            }

            val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)
            val result = IMAGE_PREFIX + encoded

            Log.d(TAG, "Berhasil mengkonversi gambar ke Base64 (panjang: ${result.length})")
            return result

        } catch (e: IOException) {
            Log.e(TAG, "Error mengkonversi URI ke Base64: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Kehabisan memori saat memproses gambar: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error tidak terduga selama konversi gambar: ${e.message}")
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = MAX_IMAGE_DIMENSION
            newHeight = (newWidth / ratio).toInt()
        } else {
            newHeight = MAX_IMAGE_DIMENSION
            newWidth = (newHeight * ratio).toInt()
        }

        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error resizing bitmap: ${e.message}")
            bitmap // Return original if resize fails
        }
    }

    fun base64ToBitmap(base64String: String): Bitmap? {
        if (!isImage(base64String)) {
            Log.e(TAG, "String is not a valid image format")
            return null
        }

        return try {
            val cleanBase64 = base64String.removePrefix(IMAGE_PREFIX)

            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            if (imageBytes.isEmpty()) {
                Log.e(TAG, "Base64 decoding resulted in empty byte array")
                return null
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${options.outWidth}x${options.outHeight}")
                return null
            }

            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from byte array")
            } else {
                Log.d(TAG, "Successfully decoded image: ${bitmap.width}x${bitmap.height}")
            }

            bitmap

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Base64 string: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error decoding image: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Base64 to Bitmap: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        Log.d(TAG, "Calculated sample size: $inSampleSize")
        return inSampleSize
    }

    fun isImage(content: String): Boolean {
        return content.startsWith(IMAGE_PREFIX)
    }

    fun isAudio(content: String): Boolean {
        return content.startsWith(AUDIO_PREFIX)
    }

    fun isText(content: String): Boolean {
        return content.startsWith(TEXT_PREFIX) || (!isImage(content) && !isAudio(content))
    }

    fun wrapText(text: String): String {
        return if (text.startsWith(TEXT_PREFIX)) {
            text
        } else {
            TEXT_PREFIX + text
        }
    }

    fun unwrapText(content: String): String {
        return content.removePrefix(TEXT_PREFIX)
    }

    fun wrapAudio(path: String, title: String): String {
        return "$AUDIO_PREFIX$path$AUDIO_TITLE_SEPARATOR$title"
    }

    fun unwrapAudio(content: String): Pair<String, String> {
        if (!isAudio(content)) {
            return Pair("", "Audio Recording")
        }

        val audioContent = content.removePrefix(AUDIO_PREFIX)
        val parts = audioContent.split(AUDIO_TITLE_SEPARATOR, limit = 2)

        return if (parts.size > 1) {
            Pair(parts[0], parts[1])
        } else {
            Pair(parts[0], "Audio Recording")
        }
    }

    fun serializeNoteParts(parts: List<NotePart>): String {
        val stringBuilder = StringBuilder()

        val filteredParts = parts.filter {
            !(it is NotePart.TextPart && it.text.trim().isEmpty())
        }

        if (filteredParts.isEmpty()) {
            Log.w(TAG, "No valid parts to serialize")
            return ""
        }

        Log.d(TAG, "Serializing ${filteredParts.size} parts")

        filteredParts.forEachIndexed { index, part ->
            when (part) {
                is NotePart.TextPart -> {
                    stringBuilder.append(TEXT_PREFIX).append(part.text)
                    Log.d(TAG, "Serialized text part: ${part.text.take(20)}...")
                }
                is NotePart.ImagePart -> {
                    // Make sure image paths start with the correct prefix
                    val imagePath = if (part.imagePath.startsWith(IMAGE_PREFIX)) {
                        part.imagePath
                    } else {
                        IMAGE_PREFIX + part.imagePath
                    }
                    stringBuilder.append(imagePath)
                    Log.d(TAG, "Serialized image part: ${imagePath.take(30)}...")
                }
                is NotePart.AudioPart -> {
                    // Make sure audio content is properly formatted
                    val audioContent = if (part.audioPath.startsWith(AUDIO_PREFIX)) {
                        part.audioPath
                    } else {
                        wrapAudio(part.audioPath, part.title)
                    }
                    stringBuilder.append(audioContent)
                    Log.d(TAG, "Serialized audio part: ${audioContent.take(30)}...")
                }
            }

            // Add delimiter except for the last item
            if (index < filteredParts.size - 1) {
                stringBuilder.append("\n---PART_SEPARATOR---\n")
            }
        }

        return stringBuilder.toString()
    }

    fun deserializeNoteParts(serialized: String): List<NotePart> {
        val parts = mutableListOf<NotePart>()

        if (serialized.isEmpty()) {
            Log.d(TAG, "Empty content to deserialize")
            return parts
        }

        try {
            val segments = serialized.split("\n---PART_SEPARATOR---\n")
            Log.d(TAG, "Deserializing ${segments.size} content segments")

            for (segment in segments) {
                when {
                    segment.startsWith(IMAGE_PREFIX) -> {
                        Log.d(TAG, "Found image segment: ${segment.take(30)}...")
                        parts.add(NotePart.ImagePart(segment))
                    }
                    segment.startsWith(TEXT_PREFIX) -> {
                        val text = unwrapText(segment)
                        Log.d(TAG, "Found text segment: ${text.take(20)}...")
                        parts.add(NotePart.TextPart(text))
                    }
                    segment.startsWith(AUDIO_PREFIX) -> {
                        val (audioPath, title) = unwrapAudio(segment)
                        Log.d(TAG, "Found audio segment with title: $title")
                        parts.add(NotePart.AudioPart(segment, title))
                    }
                    else -> {
                        // Assume it's text if it doesn't have a prefix
                        Log.d(TAG, "Found untagged text: ${segment.take(20)}...")
                        parts.add(NotePart.TextPart(segment))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing note parts: ${e.message}")
            // In case of error, return at least one text part with the original content
            parts.add(NotePart.TextPart(serialized))
        }

        // If no parts were added, add an empty text part
        if (parts.isEmpty()) {
            Log.w(TAG, "No parts could be deserialized, adding empty text part")
            parts.add(NotePart.TextPart(""))
        }

        return parts
    }
}