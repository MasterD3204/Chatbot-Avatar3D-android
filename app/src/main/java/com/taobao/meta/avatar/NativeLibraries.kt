package com.taobao.meta.avatar

import android.util.Log

object NativeLibraries {

    private const val TAG = "NativeLibraries"
    private const val TAOAVATAR_LIB = "taoavatar"
    private const val MNN_A2BS_LIB = "mnn_a2bs"

    @Volatile
    private var taoavatarLoaded = false

    @Volatile
    private var mnnA2bsLoaded = false

    @Volatile
    private var taoavatarError: Throwable? = null

    @Volatile
    private var mnnA2bsError: Throwable? = null

    @Synchronized
    fun loadTaoavatar(): Throwable? {
        if (taoavatarLoaded) return null
        taoavatarError?.let { return it }
        return try {
            System.loadLibrary(TAOAVATAR_LIB)
            taoavatarLoaded = true
            null
        } catch (t: Throwable) {
            taoavatarError = t
            Log.e(TAG, "Failed to load $TAOAVATAR_LIB", t)
            t
        }
    }

    @Synchronized
    fun loadMnnA2bs(): Throwable? {
        if (mnnA2bsLoaded) return null
        mnnA2bsError?.let { return it }
        return try {
            System.loadLibrary(MNN_A2BS_LIB)
            mnnA2bsLoaded = true
            null
        } catch (t: Throwable) {
            mnnA2bsError = t
            Log.e(TAG, "Failed to load $MNN_A2BS_LIB", t)
            t
        }
    }

    fun buildLoadErrorMessage(error: Throwable): String {
        val rawMessage = error.message ?: error.toString()
        return if (rawMessage.contains("_ZN3MNN7Session9ModeGroup7setHint")) {
            "Khong the nap bo thu vien MNN hien tai.\n\n" +
                "libMNN_Express.so dang khong tuong thich voi libMNN.so. " +
                "Hay thay toan bo bo .so MNN bang cac file cung mot release."
        } else {
            "Khong the nap thu vien native:\n$rawMessage"
        }
    }
}
