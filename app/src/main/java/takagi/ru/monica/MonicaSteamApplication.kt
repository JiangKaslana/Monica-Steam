package takagi.ru.monica

import android.app.Application
import takagi.ru.monica.steam.diagnostics.SteamCrashDiagnostics
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

class MonicaSteamApplication : Application() {
    override fun onCreate() {
        SteamCrashDiagnostics.install(this)
        SteamDiagLogger.initialize(this)
        super.onCreate()
    }
}

