package com.example.orblocalizer.util

import android.util.Log

object Logger {
    private const val APP_TAG = "ORBLocalizer"
    private var debugEnabled = true

    fun d(tag: String, message: String) {
        if (debugEnabled) Log.d("$APP_TAG/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$APP_TAG/$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w("$APP_TAG/$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$APP_TAG/$tag", message, throwable)
    }

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }
}
