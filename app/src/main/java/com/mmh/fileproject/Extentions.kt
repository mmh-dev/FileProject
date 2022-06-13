package com.mmh.fileproject

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.fragment.app.Fragment


fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

inline fun <T> sdk29AndUp (onSdk29: () -> T): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onSdk29()
    } else null
}