package tn.loukious.facebookappadsremover.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast

internal object ImageActions {
    fun show(activity: Activity, bitmap: Bitmap) {
        AlertDialog.Builder(activity).setItems(arrayOf("Save image", "Copy image")) { _, which ->
            // Own a snapshot: closing the viewer must not recycle the worker's pixels.
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@setItems
            Thread {
                val resolver = activity.contentResolver
                var uri: Uri? = null
                val result = runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "Facebook-${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, if(which==0) "Pictures/Facebook" else "Pictures/Facebook/Clipboard")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert")
                    resolver.openOutputStream(uri!!)?.use { check(copy.compress(Bitmap.CompressFormat.PNG, 100, it)) } ?: error("write")
                    resolver.update(uri!!, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    uri!!
                }
                copy.recycle()
                if(result.isFailure) uri?.let { runCatching {resolver.delete(it,null,null)} }
                Handler(Looper.getMainLooper()).post {
                    result.onSuccess { saved ->
                        if(which==1) {
                            (activity.getSystemService(ClipboardManager::class.java)).setPrimaryClip(ClipData.newUri(resolver,"Image",saved))
                        }
                        Toast.makeText(activity,if(which==0) "Saved to Pictures/Facebook" else "Image copied",Toast.LENGTH_SHORT).show()
                    }.onFailure { Toast.makeText(activity,"Couldn't save image",Toast.LENGTH_SHORT).show() }
                }
            }.apply {name="FBAR-ImageAction"}.start()
        }.show()
    }
}
