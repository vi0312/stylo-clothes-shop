package com.stylo.shop

import android.app.Application
import com.stylo.shop.core.storage.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StyloApp : Application() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        sessionManager.bootstrap()
    }
}
