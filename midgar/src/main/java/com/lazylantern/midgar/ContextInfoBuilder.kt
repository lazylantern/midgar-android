package com.lazylantern.midgar

import android.app.Application
import android.os.Build
import com.lazylantern.midgar.models.ContextInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.util.*


class ContextInfoBuilder{
    fun buildContextInfo(app: Application): ContextInfo{

        val packageManager = app.packageManager
        val packageName = app.packageName

        val packageInfo = packageManager.getPackageInfo(packageName, 0)

        return ContextInfo(
            getApplicationName(app, packageManager),
            getVersionName(packageInfo),
            getVersionCode(packageInfo),
            getDeviceCountry(app),
            getOsVersion(),
            getModel(),
            getManufacturer(),
            isEmulator()
        )
    }

    private fun getApplicationName(app: Application, packageManager: PackageManager):String {
        return app.applicationInfo.loadLabel(packageManager).toString()
    }

    private fun getVersionName(packageInfo: PackageInfo):String{
        return packageInfo.versionName
    }

    private fun getVersionCode(packageInfo: PackageInfo):Int{
        return packageInfo.versionCode
    }

    private fun getDeviceCountry(app: Application): String {
        val currentLocale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            app.resources.configuration.locales.get(0)
        } else {
            app.resources.configuration.locale
        }

        return currentLocale.country
    }

    private fun getOsVersion(): String{
        return Build.VERSION.RELEASE
    }

    private fun getModel(): String {
        return Build.BOARD
    }

    private fun getManufacturer(): String{
        return Build.MANUFACTURER
    }

    private fun isEmulator(): Boolean{
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }
}