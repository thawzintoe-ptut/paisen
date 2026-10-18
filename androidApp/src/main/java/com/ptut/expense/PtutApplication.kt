package com.ptut.expense

import android.app.Application
import com.ptut.expense.di.AppBuild
import com.ptut.expense.di.initKoin
import com.ptut.expense.sync.push.startPushAfterSignIn

/**
 * Starts the graph once per process. An Activity would rebuild it on every recreation and leave
 * the previous database open with its collectors still running.
 *
 * The Application is the `PtutDatabaseContext`: on Android that type is a `Context`, and the
 * builder resolves `applicationContext` from it, so nothing here can leak an Activity.
 */
class PtutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(this, AppBuild(debug = BuildConfig.DEBUG))
        startPushAfterSignIn(this)
    }
}
