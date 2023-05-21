package com.gmail.spittelermattijn.sipkip.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

fun ContentResolver.queryName(uri: Uri): String {
    val returnCursor = query(uri, null, null, null, null)!!
    val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    returnCursor.moveToFirst()
    val name = returnCursor.getString(nameIndex)
    returnCursor.close()
    return name
}