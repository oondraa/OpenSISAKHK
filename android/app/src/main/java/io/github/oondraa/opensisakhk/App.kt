package io.github.oondraa.opensisakhk

import android.app.Application
import androidx.work.WorkManager
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.Prefs
import io.github.oondraa.opensisakhk.ui.theme.Look
import io.github.oondraa.opensisakhk.watch.CheckWorker
import io.github.oondraa.opensisakhk.watch.Notifications
import io.github.oondraa.opensisakhk.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Cache.init(this)
        Prefs.init(this)
        Look.load()
        Api.init(this)
        Notifications.createChannels(this)
        if (Api.signedIn.value) CheckWorker.schedule(this)
        // Signing in starts the background check, signing out stops it and empties the widgets.
        scope.launch {
            Api.signedIn.drop(1).collect { signedIn ->
                if (signedIn) CheckWorker.schedule(this@App)
                else WorkManager.getInstance(this@App).cancelUniqueWork("check")
                Widgets.update(this@App)
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set

        /** Work that outlives a screen: widget redraws after the app loaded something. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
