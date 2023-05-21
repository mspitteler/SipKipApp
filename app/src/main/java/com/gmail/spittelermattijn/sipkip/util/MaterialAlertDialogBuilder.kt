package com.gmail.spittelermattijn.sipkip.util

import android.widget.EditText
import com.gmail.spittelermattijn.sipkip.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun MaterialAlertDialogBuilder.showFirstDirectoryPicker(default: String? = null, cb: (String) -> Unit) {
    val items = arrayOf(
        R.string.first_directory_star_clip to "star_clip", R.string.first_directory_triangle_clip to "triangle_clip",
        R.string.first_directory_square_clip to "square_clip", R.string.first_directory_heart_clip to "heart_clip",
        R.string.first_directory_beak_switch to "beak_switch"
    )
    var checkedItem = items.map { it.second }.indexOf(default)
    checkedItem = if (checkedItem == -1) 0 else checkedItem
    setTitle(R.string.dialog_pick_first_directory)
        .setSingleChoiceItems(items.map { context.getString(it.first) }.toTypedArray(), checkedItem) { dialog, which -> checkedItem = which }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { dialog, which -> cb(items[checkedItem].second) }
        .show()
}

fun MaterialAlertDialogBuilder.showRenameEditText(default: String? = null, cb: (String) -> Unit) {
    val editText = EditText(context).apply { setText(default) }
    setTitle(R.string.dialog_enter_path)
        .setView(editText)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { dialog, which ->
            val text = if (editText.text.isNullOrEmpty()) "default" else editText.text
            cb(text.replace("""\s""".toRegex(), "_"))
        }
        .show()
}