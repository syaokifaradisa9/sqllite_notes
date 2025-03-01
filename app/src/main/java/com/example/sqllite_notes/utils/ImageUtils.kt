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

/**
 * Utility class for image operations
 * Handles conversion between image formats for database storage
 */
object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val IMAGE_PREFIX = "IMG:"
    private const val TEXT_PREFIX = "TXT:"
    private const val MAX_IMAGE_DIMENSION = 1024
    private const val JPEG_QUALITY = 80

    /**
     * Convert URI to Base64 encoded string
     * @param contentResolver ContentResolver to use for accessing the image
     * @param imageUri URI of the image to convert
     * @return Base64 encoded string of the image, or null if conversion failed
     */
    fun uriToBase64(contentResolver: ContentResolver, imageUri: Uri): String? {
        return try {
            Log.d(TAG, "Converting image URI to Base64: $imageUri")
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
                Log.e(TAG, "Failed to decode bitmap from URI")
                return null
            }

            // Resize the bitmap to save space
            val resizedBitmap = resizeBitmap(bitmap)

            // Convert bitmap to byte array
            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            if (byteArray.isEmpty()) {
                Log.e(TAG, "Compressed image produced empty byte array")
                return null
            }

            // Convert byte array to Base64 string
            val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)
            val result = IMAGE_PREFIX + encoded

            Log.d(TAG, "Successfully converted image to Base64 (length: ${result.length})")
            return result

        } catch (e: IOException) {
            Log.e(TAG, "Error converting URI to Base64: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while processing image: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during image conversion: ${e.message}")
            null
        }
    }

    /**
     * Resize bitmap to reduce memory usage
     * @param bitmap Bitmap to resize
     * @return Resized bitmap
     */
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

    /**
     * Convert Base64 encoded string to bitmap
     * @param base64String Base64 encoded string to convert
     * @return Bitmap, or null if conversion failed
     */
    fun base64ToBitmap(base64String: String): Bitmap? {
        if (!isImage(base64String)) {
            Log.e(TAG, "String is not a valid image format")
            return null
        }

        return try {
            val cleanBase64 = base64String.removePrefix(IMAGE_PREFIX)

            // Decode the Base64 string to a byte array
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            if (imageBytes.isEmpty()) {
                Log.e(TAG, "Base64 decoding resulted in empty byte array")
                return null
            }

            // Set up decoding options to avoid OutOfMemoryError
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            // First pass to get image dimensions without loading into memory
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${options.outWidth}x${options.outHeight}")
                return null
            }

            // Calculate appropriate sampling rate
            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            options.inJustDecodeBounds = false

            // Actually decode the bitmap with the calculated sample size
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

    /**
     * Calculate an appropriate inSampleSize value for bitmap decoding
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        Log.d(TAG, "Calculated sample size: $inSampleSize")
        return inSampleSize
    }

    /**
     * Check if a string is an encoded image
     * @param content String to check
     * @return True if the string is an encoded image, false otherwise
     */
    fun isImage(content: String): Boolean {
        return content.startsWith(IMAGE_PREFIX)
    }

    /**
     * Check if a string is text
     * @param content String to check
     * @return True if the string is text, false otherwise
     */
    fun isText(content: String): Boolean {
        return content.startsWith(TEXT_PREFIX) || (!isImage(content))
    }

    /**
     * Wrap text content with a prefix for consistent handling
     * @param text Text to wrap
     * @return Wrapped text
     */
    fun wrapText(text: String): String {
        return if (text.startsWith(TEXT_PREFIX)) {
            text
        } else {
            TEXT_PREFIX + text
        }
    }

    /**
     * Unwrap text content
     * @param content Wrapped text
     * @return Unwrapped text
     */
    fun unwrapText(content: String): String {
        return content.removePrefix(TEXT_PREFIX)
    }

    /**
     * Serialize a list of note parts to a single string for database storage
     * @param parts List of NotePart objects
     * @return Serialized string
     */
    fun serializeNoteParts(parts: List<NotePart>): String {
        val stringBuilder = StringBuilder()

        // Filter out any empty text parts
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
            }

            // Add delimiter except for the last item
            if (index < filteredParts.size - 1) {
                stringBuilder.append("\n---PART_SEPARATOR---\n")
            }
        }

        return stringBuilder.toString()
    }

    /**
     * Deserialize a string from the database to a list of note parts
     * @param serialized Serialized string
     * @return List of NotePart objects
     */
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
                if (segment.startsWith(IMAGE_PREFIX)) {
                    Log.d(TAG, "Found image segment: ${segment.take(30)}...")
                    parts.add(NotePart.ImagePart(segment))
                } else if (segment.startsWith(TEXT_PREFIX)) {
                    val text = unwrapText(segment)
                    Log.d(TAG, "Found text segment: ${text.take(20)}...")
                    parts.add(NotePart.TextPart(text))
                } else {
                    // Assume it's text if it doesn't have a prefix
                    Log.d(TAG, "Found untagged text: ${segment.take(20)}...")
                    parts.add(NotePart.TextPart(segment))
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