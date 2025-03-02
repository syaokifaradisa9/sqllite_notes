package com.example.sqllite_notes.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.sqllite_notes.R

/**
 * SwipeToDeleteCallback adalah Class abstrak yang menyediakan fungsionalitas
 * swipe-to-delete untuk item dalam RecyclerView.
 *
 * Class ini mewarisi ItemTouchHelper.SimpleCallback yang merupakan implementasi
 * dasar untuk menangani gestur swipe dan drag dalam RecyclerView. Dengan mewarisi
 * Class ini dan mengimplementasikan metode onSwiped(), komponen UI dapat menambahkan
 * fungsionalitas hapus dengan gesture swipe secara konsisten.
 *
 * Fitur utama dari implementasi ini meliputi:
 * 1. Visualisasi latar belakang merah saat item di-swipe
 * 2. Tampilan ikon hapus pada bagian kanan item saat di-swipe
 * 3. Animasi dan efek visual yang halus selama gesture swipe
 * 4. Dukungan untuk pembatalan gesture swipe (cancel)
 */
abstract class SwipeToDeleteCallback(context: Context) : ItemTouchHelper.SimpleCallback(
    0, ItemTouchHelper.LEFT  // Parameter 1: drag directions (0 = tidak ada), Parameter 2: swipe directions (hanya LEFT)
) {
    // Ikon hapus yang akan ditampilkan saat swipe
    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.baseline_auto_delete_24)

    // Background untuk area yang terlihat saat swipe
    private val background = ColorDrawable()

    // Warna merah untuk background swipe
    private val backgroundColor = Color.parseColor("#F44336")  // Material Design Red 500

    // Paint khusus untuk membersihkan canvas saat gesture dibatalkan
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    /**
     * Dipanggil saat item hendak di-drag.
     *
     * Karena implementasi ini hanya mendukung swipe (bukan drag),
     * metode ini selalu mengembalikan false.
     *
     * @return false untuk menunjukkan drag tidak didukung
     */
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false  // Tidak mendukung operasi drag-and-drop
    }

    /**
     * Dipanggil saat gesture swipe terjadi untuk menggambar efek visual selama swipe.
     *
     * Metode ini menangani:
     * 1. Menggambar latar belakang merah saat item di-swipe
     * 2. Menempatkan dan menggambar ikon hapus
     * 3. Menangani gesture swipe yang dibatalkan
     *
     * @param c Canvas untuk menggambar
     * @param recyclerView RecyclerView yang berisi item
     * @param viewHolder ViewHolder dari item yang di-swipe
     * @param dX Jarak horizontal yang di-swipe
     * @param dY Jarak vertikal yang di-swipe (biasanya 0 untuk swipe horizontal)
     * @param actionState Jenis aksi (swipe atau drag)
     * @param isCurrentlyActive True jika pengguna masih melakukan gesture, false jika animasi sedang berlangsung
     */
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top

        // Periksa apakah gesture dibatalkan (dX = 0 dan tidak aktif)
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            // Bersihkan canvas pada area item untuk menghapus efek visual swipe
            clearCanvas(
                c,
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )

            // Lanjutkan dengan perilaku default
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        // Gambar latar belakang merah
        background.color = backgroundColor
        background.setBounds(
            itemView.right + dX.toInt(),  // Latar belakang dimulai dari kanan item dan bergerak ke kiri sesuai jarak swipe
            itemView.top,
            itemView.right,
            itemView.bottom
        )
        background.draw(c)

        // Hitung posisi ikon hapus
        val iconMargin = (itemHeight - deleteIcon!!.intrinsicHeight) / 2
        val iconTop = itemView.top + (itemHeight - deleteIcon.intrinsicHeight) / 2
        val iconBottom = iconTop + deleteIcon.intrinsicHeight
        val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
        val iconRight = itemView.right - iconMargin

        // Gambar ikon hapus
        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
        deleteIcon.draw(c)

        // Lanjutkan dengan perilaku default
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    /**
     * Membersihkan area tertentu pada canvas.
     *
     * Digunakan untuk menghapus efek visual saat gesture dibatalkan.
     *
     * @param c Canvas yang akan dibersihkan
     * @param left Koordinat kiri area yang dibersihkan
     * @param top Koordinat atas area yang dibersihkan
     * @param right Koordinat kanan area yang dibersihkan
     * @param bottom Koordinat bawah area yang dibersihkan
     */
    private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
        c?.drawRect(left, top, right, bottom, clearPaint)
    }
}