package com.example.sqllite_notes.utils

import android.content.ContentResolver
import android.content.Context
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * MultimediaUtils adalah class utility singleton (object) yang menangani
 * operasi pemrosesan multimedia untuk aplikasi Notes.
 *
 * Class ini bertanggung jawab untuk:
 * 1. Konversi antara format data yang berbeda (URI ke Base64, Base64 ke Bitmap)
 * 2. Identifikasi jenis konten (teks, gambar, audio)
 * 3. Serialisasi dan deserialisasi konten catatan multi-bagian
 * 4. Pengelolaan format penyimpanan data terstandarisasi
 *
 * Desain sebagai singleton (object) memastikan konsistensi dalam penanganan
 * konten multimedia di seluruh aplikasi dan menghindari duplikasi kode.
 */
object MultimediaUtils {
    // Tag untuk logging
    private const val TAG = "MediaUtils"

    // Prefiks untuk mengidentifikasi jenis konten
    private const val IMAGE_PREFIX = "IMG:"
    private const val TEXT_PREFIX = "TXT:"
    private const val AUDIO_PREFIX = "AUDIO:"

    // Pemisah untuk judul audio dalam format penyimpanan
    private const val AUDIO_TITLE_SEPARATOR = "::TITLE::"

    // Konstanta untuk optimasi gambar
    private const val MAX_IMAGE_DIMENSION = 1024
    private const val JPEG_QUALITY = 80

    /**
     * Mengkonversi URI gambar menjadi string Base64.
     *
     * Proses ini melibatkan:
     * 1. Membaca bitmap dari URI gambar
     * 2. Mengubah ukuran bitmap untuk optimasi penyimpanan
     * 3. Mengkompresi bitmap ke format JPEG
     * 4. Mengkonversi data biner menjadi string Base64
     * 5. Menambahkan prefiks untuk mengidentifikasi sebagai gambar
     *
     * @param contentResolver ContentResolver untuk mengakses URI
     * @param imageUri URI gambar yang akan dikonversi
     * @return String Base64 dengan prefiks atau null jika gagal
     */
    fun uriToBase64(contentResolver: ContentResolver, imageUri: Uri): String? {
        return try {
            // Mendapatkan bitmap dari URI gambar dengan metode yang sesuai dengan versi Android
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Untuk Android 9 (Pie) ke atas, gunakan ImageDecoder
                val source = ImageDecoder.createSource(contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                // Untuk versi Android sebelum 9, gunakan MediaStore
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            }

            if (bitmap == null) {
                Log.e(TAG, "Gagal mendekode bitmap dari URI")
                return null
            }

            // Mengubah ukuran bitmap untuk optimasi penyimpanan
            val resizedBitmap = resizeBitmap(bitmap)

            // Mengkonversi bitmap ke array byte dengan kompresi JPEG
            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            if (byteArray.isEmpty()) {
                Log.e(TAG, "Gambar terkompresi menghasilkan array byte kosong")
                return null
            }

            // Mengkonversi array byte ke string Base64 dan tambahkan prefiks gambar
            val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)
            val result = IMAGE_PREFIX + encoded

            Log.d(TAG, "Berhasil mengkonversi gambar ke Base64 (panjang: ${result.length})")
            return result

        } catch (e: IOException) {
            Log.e(TAG, "Error mengkonversi URI ke Base64: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            // Penting menangani OutOfMemoryError karena gambar berukuran besar
            Log.e(TAG, "Kehabisan memori saat memproses gambar: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error tidak terduga selama konversi gambar: ${e.message}")
            null
        }
    }

    /**
     * Mengkonversi URI audio menjadi string Base64.
     *
     * @param contentResolver ContentResolver untuk mengakses URI
     * @param audioUri URI audio yang akan dikonversi
     * @return String Base64 dengan prefiks atau null jika gagal
     */
    fun audioUriToBase64(contentResolver: ContentResolver, audioUri: Uri): String? {
        return try {
            // Buka input stream dari URI audio
            contentResolver.openInputStream(audioUri)?.use { inputStream ->
                // Baca seluruh file audio ke array byte
                val bytes = inputStream.readBytes()

                // Jika tidak ada data, kembalikan null
                if (bytes.isEmpty()) {
                    Log.e(TAG, "No audio data read from URI")
                    return null
                }

                // Konversi array byte ke string Base64
                val encoded = Base64.encodeToString(bytes, Base64.DEFAULT)

                // Tambahkan prefiks audio dan judul default
                // Kita menggunakan format yang sama dengan wrapAudio tetapi langsung menyertakan data
                val result = AUDIO_PREFIX + "BASE64:" + encoded + AUDIO_TITLE_SEPARATOR + "Audio Recording"

                Log.d(TAG, "Successfully converted audio to Base64 (length: ${result.length})")
                return result
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error converting audio URI to Base64: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error when processing audio: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during audio conversion: ${e.message}")
            null
        }
    }

    /**
     * Memeriksa apakah sebuah string audio berisi data Base64.
     *
     * @param content String audio yang akan diperiksa
     * @return true jika konten berisi data Base64
     */
    fun isAudioBase64(content: String): Boolean {
        return content.startsWith(AUDIO_PREFIX) && content.contains("BASE64:")
    }

    /**
     * Mengekstrak data Base64 dan judul dari string audio.
     *
     * @param content String audio yang berisi data Base64
     * @return Pair(base64Data, title) atau null jika format tidak valid
     */
    fun extractAudioBase64(content: String): Pair<String, String>? {
        if (!isAudioBase64(content)) {
            return null
        }

        try {
            // Hapus prefix audio
            val withoutPrefix = content.removePrefix(AUDIO_PREFIX)

            // Hapus "BASE64:" marker
            val base64Content = withoutPrefix.removePrefix("BASE64:")

            // Split berdasarkan pembatas judul
            val parts = base64Content.split(AUDIO_TITLE_SEPARATOR, limit = 2)

            // Ekstrak data dan judul
            val base64Data = parts[0]
            val title = if (parts.size > 1) parts[1] else "Audio Recording"

            return Pair(base64Data, title)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio Base64 data: ${e.message}")
            return null
        }
    }

    /**
     * Menulis data Base64 audio ke file sementara untuk pemutaran.
     *
     * @param context Context aplikasi
     * @param base64Audio Data Base64 audio
     * @return Uri ke file sementara atau null jika gagal
     */
    fun base64AudioToTempFile(context: Context, base64Audio: String): Uri? {
        try {
            // Decode Base64 menjadi array byte
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)

            // Buat file sementara
            val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.mp3")

            // Tulis data ke file
            FileOutputStream(tempFile).use { output ->
                output.write(audioBytes)
                output.flush()
            }

            // Kembalikan URI ke file sementara
            return Uri.fromFile(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Base64 to audio file: ${e.message}")
            return null
        }
    }

    /**
     * Mengubah ukuran bitmap untuk optimasi penyimpanan.
     *
     * Metode ini mempertahankan rasio aspek gambar asli sambil memastikan
     * dimensi tidak melebihi MAX_IMAGE_DIMENSION (1024px).
     *
     * @param bitmap Bitmap asli yang akan diubah ukurannya
     * @return Bitmap dengan ukuran yang dioptimalkan
     */
    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Jika bitmap sudah cukup kecil, kembalikan apa adanya
        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        // Hitung rasio aspek untuk mempertahankan proporsi gambar
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        // Tentukan dimensi baru berdasarkan dimensi terpanjang
        if (width > height) {
            newWidth = MAX_IMAGE_DIMENSION
            newHeight = (newWidth / ratio).toInt()
        } else {
            newHeight = MAX_IMAGE_DIMENSION
            newWidth = (newHeight * ratio).toInt()
        }

        // Buat bitmap baru dengan ukuran yang dioptimalkan
        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error resizing bitmap: ${e.message}")
            bitmap // Kembalikan bitmap asli jika resize gagal
        }
    }

    /**
     * Mengkonversi string Base64 menjadi bitmap.
     *
     * Metode ini melakukan:
     * 1. Verifikasi bahwa string memiliki format gambar yang benar
     * 2. Ekstraksi Base64 dari string (menghapus prefiks)
     * 3. Dekode Base64 menjadi array byte
     * 4. Optimasi loading dengan sampling untuk menghindari OutOfMemoryError
     * 5. Konversi array byte menjadi bitmap
     *
     * @param base64String String Base64 dengan prefiks gambar
     * @return Bitmap hasil konversi atau null jika gagal
     */
    fun base64ToBitmap(base64String: String): Bitmap? {
        // Verifikasi bahwa string adalah gambar
        if (!isImage(base64String)) {
            Log.e(TAG, "String is not a valid image format")
            return null
        }

        return try {
            // Hapus prefiks gambar dari string
            val cleanBase64 = base64String.removePrefix(IMAGE_PREFIX)

            // Dekode Base64 menjadi array byte
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            if (imageBytes.isEmpty()) {
                Log.e(TAG, "Base64 decoding resulted in empty byte array")
                return null
            }

            // Gunakan BitmapFactory.Options untuk mendapatkan informasi dimensi
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true  // Hanya baca dimensi, bukan seluruh gambar
            }

            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${options.outWidth}x${options.outHeight}")
                return null
            }

            // Hitung sampel yang optimal untuk menghindari OutOfMemoryError
            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            options.inJustDecodeBounds = false

            // Dekode array byte menjadi bitmap dengan opsi yang dioptimalkan
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
     * Menghitung faktor sample size optimal untuk memuat gambar dengan efisien.
     *
     * Sample size adalah faktor penskalaan.
     * Nilai 1 berarti tidak ada penskalaan.
     * Nilai 2 berarti dimensi gambar dikurangi setengah (1/4 piksel total).
     * Nilai 4 berarti dimensi gambar dikurangi seperempat (1/16 piksel total).
     *
     * Teknik ini penting untuk memuat gambar besar secara efisien di perangkat
     * dengan memori terbatas.
     *
     * @param options Options dari BitmapFactory yang berisi dimensi gambar
     * @param reqWidth Lebar yang diinginkan
     * @param reqHeight Tinggi yang diinginkan
     * @return Nilai sample size optimal (power of 2)
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        // Hitung faktor penskalaan terbesar yang merupakan kelipatan 2
        // dan masih menghasilkan gambar yang lebih besar dari yang diminta
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

    /**
     * Memeriksa apakah sebuah string adalah konten gambar.
     *
     * @param content String yang akan diperiksa
     * @return true jika konten adalah gambar (diawali dengan prefiks gambar)
     */
    fun isImage(content: String): Boolean {
        return content.startsWith(IMAGE_PREFIX)
    }

    /**
     * Memeriksa apakah sebuah string adalah konten audio.
     *
     * @param content String yang akan diperiksa
     * @return true jika konten adalah audio (diawali dengan prefiks audio)
     */
    fun isAudio(content: String): Boolean {
        return content.startsWith(AUDIO_PREFIX)
    }

    /**
     * Memeriksa apakah sebuah string adalah konten teks.
     *
     * String dianggap sebagai teks jika:
     * 1. Diawali dengan prefiks teks, atau
     * 2. Bukan gambar dan bukan audio
     *
     * @param content String yang akan diperiksa
     * @return true jika konten adalah teks
     */
    fun isText(content: String): Boolean {
        return content.startsWith(TEXT_PREFIX) || (!isImage(content) && !isAudio(content))
    }

    /**
     * Membungkus teks dengan prefiks teks untuk standardisasi format penyimpanan.
     *
     * @param text Teks yang akan dibungkus
     * @return Teks dengan prefiks teks (jika belum ada)
     */
    fun wrapText(text: String): String {
        return if (text.startsWith(TEXT_PREFIX)) {
            text
        } else {
            TEXT_PREFIX + text
        }
    }

    /**
     * Membuka bungkusan teks dengan menghapus prefiks teks.
     *
     * @param content Konten teks yang dibungkus
     * @return Teks asli tanpa prefiks
     */
    fun unwrapText(content: String): String {
        return content.removePrefix(TEXT_PREFIX)
    }

    /**
     * Membungkus path audio dan judul untuk standardisasi format penyimpanan.
     *
     * Format: "AUDIO:[path]::TITLE::[title]"
     *
     * @param path Path ke file audio
     * @param title Judul/nama audio
     * @return String audio yang dibungkus dalam format standar
     */
    fun wrapAudio(path: String, title: String): String {
        return "$AUDIO_PREFIX$path$AUDIO_TITLE_SEPARATOR$title"
    }

    /**
     * Membuka bungkusan audio untuk mendapatkan path dan judul.
     *
     * @param content Konten audio yang dibungkus
     * @return Pair yang berisi (path, title)
     */
    fun unwrapAudio(content: String): Pair<String, String> {
        // Periksa bahwa konten adalah audio
        if (!isAudio(content)) {
            return Pair("", "Audio Recording")
        }

        // Ekstrak konten audio (hilangkan prefiks)
        val audioContent = content.removePrefix(AUDIO_PREFIX)

        // Pecah berdasarkan pemisah judul
        val parts = audioContent.split(AUDIO_TITLE_SEPARATOR, limit = 2)

        // Kembalikan path dan judul (atau default jika tidak ada judul)
        return if (parts.size > 1) {
            Pair(parts[0], parts[1])
        } else {
            Pair(parts[0], "Audio Recording")
        }
    }

    /**
     * Menyerialisasi daftar NotePart menjadi satu string terformat untuk penyimpanan.
     *
     * Metode ini mengkonversi struktur data kompleks (daftar NotePart) menjadi
     * format string tunggal yang dapat disimpan dalam database, dengan:
     * 1. Menggabungkan semua bagian dengan pemisah
     * 2. Memformat masing-masing bagian sesuai jenisnya
     * 3. Memfilter bagian teks kosong
     *
     * @param parts Daftar NotePart yang akan diserialisasi
     * @return String hasil serialisasi
     */
    fun serializeNoteParts(parts: List<NotePart>): String {
        val stringBuilder = StringBuilder()

        // Filter bagian teks kosong
        val filteredParts = parts.filter {
            !(it is NotePart.TextPart && it.text.trim().isEmpty())
        }

        if (filteredParts.isEmpty()) {
            Log.w(TAG, "No valid parts to serialize")
            return ""
        }

        Log.d(TAG, "Serializing ${filteredParts.size} parts")

        // Serialisasi setiap bagian dan gabungkan dengan pemisah
        filteredParts.forEachIndexed { index, part ->
            when (part) {
                is NotePart.TextPart -> {
                    // Format: TXT:[text]
                    stringBuilder.append(TEXT_PREFIX).append(part.text)
                    Log.d(TAG, "Serialized text part: ${part.text.take(20)}...")
                }
                is NotePart.ImagePart -> {
                    // Pastikan path gambar dimulai dengan prefiks yang benar
                    val imagePath = if (part.content.startsWith(IMAGE_PREFIX)) {
                        part.content
                    } else {
                        IMAGE_PREFIX + part.content
                    }
                    stringBuilder.append(imagePath)
                    Log.d(TAG, "Serialized image part: ${imagePath.take(30)}...")
                }
                is NotePart.AudioPart -> {
                    // Pastikan konten audio diformat dengan benar
                    val audioContent = if (part.audioPath.startsWith(AUDIO_PREFIX)) {
                        part.audioPath
                    } else {
                        wrapAudio(part.audioPath, part.title)
                    }
                    stringBuilder.append(audioContent)
                    Log.d(TAG, "Serialized audio part: ${audioContent.take(30)}...")
                }
            }

            // Tambahkan pemisah kecuali untuk item terakhir
            if (index < filteredParts.size - 1) {
                stringBuilder.append("\n---PART_SEPARATOR---\n")
            }
        }

        return stringBuilder.toString()
    }

    /**
     * Mendeserialisasi string terformat menjadi daftar NotePart.
     *
     * Metode ini mengkonversi string hasil serialisasi kembali ke struktur data
     * berupa daftar NotePart, dengan:
     * 1. Memecah string berdasarkan pemisah
     * 2. Mengidentifikasi jenis konten untuk setiap bagian
     * 3. Membuat objek NotePart yang sesuai
     *
     * @param serialized String terformat hasil serialisasi
     * @return Daftar NotePart hasil deserialisasi
     */
    fun deserializeNoteParts(serialized: String): List<NotePart> {
        val parts = mutableListOf<NotePart>()

        if (serialized.isEmpty()) {
            Log.d(TAG, "Empty content to deserialize")
            return parts
        }

        try {
            // Pecah string berdasarkan pemisah bagian
            val segments = serialized.split("\n---PART_SEPARATOR---\n")
            Log.d(TAG, "Deserializing ${segments.size} content segments")

            // Proses setiap segmen menjadi NotePart
            for (segment in segments) {
                when {
                    // Jika segmen dimulai dengan prefiks gambar
                    segment.startsWith(IMAGE_PREFIX) -> {
                        Log.d(TAG, "Found image segment: ${segment.take(30)}...")
                        parts.add(NotePart.ImagePart(segment))
                    }
                    // Jika segmen dimulai dengan prefiks teks
                    segment.startsWith(TEXT_PREFIX) -> {
                        val text = unwrapText(segment)
                        Log.d(TAG, "Found text segment: ${text.take(20)}...")
                        parts.add(NotePart.TextPart(text))
                    }
                    // Jika segmen dimulai dengan prefiks audio
                    segment.startsWith(AUDIO_PREFIX) -> {
                        val (audioPath, title) = unwrapAudio(segment)
                        Log.d(TAG, "Found audio segment with title: $title")
                        parts.add(NotePart.AudioPart(segment, title))
                    }
                    // Jika tidak memiliki prefiks, anggap sebagai teks
                    else -> {
                        Log.d(TAG, "Found untagged text: ${segment.take(20)}...")
                        parts.add(NotePart.TextPart(segment))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing note parts: ${e.message}")
            // Dalam kasus error, kembalikan setidaknya satu bagian teks dengan konten asli
            parts.add(NotePart.TextPart(serialized))
        }

        // Jika tidak ada bagian yang berhasil dideserialisasi, tambahkan bagian teks kosong
        if (parts.isEmpty()) {
            Log.w(TAG, "No parts could be deserialized, adding empty text part")
            parts.add(NotePart.TextPart(""))
        }

        return parts
    }
}