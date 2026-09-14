package com.example.dsh.adapter

import android.content.Context
import android.util.Log
import com.example.dsh.BuildConfig
import com.example.dsh.DshThemeChrome
import com.example.dsh.KRApplication
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter

object KRUncaughtExceptionHandlerAdapter : IKRUncaughtExceptionHandlerAdapter {

    private const val TAG = "KRExceptionHandler"

    override fun uncaughtException(throwable: Throwable) {
        val stack = throwable.stackTraceToString()
        runCatching {
            KRApplication.application
                .getSharedPreferences(DshThemeChrome.SP_FILE, Context.MODE_PRIVATE)
                .edit()
                .putString("dsh_last_crash", stack.take(4000))
                .commit()
        }
        Log.e(TAG, "KR error: $stack")
        if (BuildConfig.DEBUG) {
            throw throwable
        }
    }
}
