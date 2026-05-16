package com.daeri.gpsspoofer

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk

class SpooferApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val appKey = ai.metaData?.getString("com.kakao.vectormap.APP_KEY")
            if (!appKey.isNullOrBlank() && appKey != "PUT_KAKAO_NATIVE_APP_KEY_HERE") {
                KakaoMapSdk.init(this, appKey)
            } else {
                Log.w("SpooferApp", "Kakao native app key not configured (local.properties)")
            }
        } catch (e: Exception) {
            Log.e("SpooferApp", "KakaoMapSdk init failed", e)
        }
    }
}
