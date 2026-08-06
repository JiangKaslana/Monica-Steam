package takagi.ru.monica.steam.profile.viewer.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.profile.viewer.data.SteamProfileViewerCache
import takagi.ru.monica.steam.profile.viewer.data.SteamProfileViewerPreferencesCache
import takagi.ru.monica.steam.profile.viewer.data.SteamProfileViewerService
import takagi.ru.monica.steam.profile.viewer.domain.SteamAchievementComparison
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerFailureReason
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerResult
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

internal data class SteamProfileViewerUiState(
    val target: SteamProfileViewerTarget? = null,
    val snapshot: SteamProfileViewerSnapshot? = null,
    val snapshotFromCache: Boolean = false,
    val loading: Boolean = false,
    val failure: SteamProfileViewerFailureReason? = null,
    val selectedGame: SteamGame? = null,
    val achievementComparison: SteamAchievementComparison? = null,
    val achievementComparisonFromCache: Boolean = false,
    val loadingAchievementComparison: Boolean = false,
    val achievementFailure: SteamProfileViewerFailureReason? = null
)

internal class SteamProfileViewerViewModel(
    private val service: SteamProfileViewerService,
    private val cache: SteamProfileViewerCache,
    private val sessionResolver: SteamAccountSessionResolver? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamProfileViewerUiState())
    val uiState: StateFlow<SteamProfileViewerUiState> = _uiState.asStateFlow()

    private var viewerAccount: SteamAccount? = null
    private var profileGeneration = 0L
    private var achievementGeneration = 0L

    fun load(
        viewer: SteamAccount,
        target: SteamProfileViewerTarget,
        force: Boolean = false
    ) {
        val targetChanged = _uiState.value.target?.steamId != target.steamId ||
            viewerAccount?.steamId != viewer.steamId
        viewerAccount = viewer
        if (targetChanged) {
            profileGeneration++
            achievementGeneration++
            _uiState.value = SteamProfileViewerUiState(target = target)
        } else {
            _uiState.value = _uiState.value.copy(target = target)
        }
        val cached = cache.loadProfile(viewer.steamId, target.steamId)
        if (cached != null) {
            _uiState.value = _uiState.value.copy(
                snapshot = cached,
                snapshotFromCache = true,
                failure = null
            )
            if (!force && System.currentTimeMillis() - cached.fetchedAt < PROFILE_CACHE_TTL_MILLIS) {
                return
            }
        }
        if (_uiState.value.loading) return
        val generation = ++profileGeneration
        _uiState.value = _uiState.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            val result = runProfileViewerCatching {
                withContext(Dispatchers.IO) { fetchProfileWithSessionRetry(viewer, target) }
            }.getOrElse {
                SteamProfileViewerResult.Failure(SteamProfileViewerFailureReason.NETWORK)
            }
            if (generation != profileGeneration || _uiState.value.target?.steamId != target.steamId) {
                return@launch
            }
            when (result) {
                is SteamProfileViewerResult.Success -> {
                    withContext(Dispatchers.IO) { cache.saveProfile(result.value) }
                    _uiState.value = _uiState.value.copy(
                        snapshot = result.value,
                        snapshotFromCache = false,
                        loading = false,
                        failure = null
                    )
                }
                is SteamProfileViewerResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        failure = result.reason
                    )
                }
            }
        }
    }

    fun refresh() {
        val viewer = viewerAccount ?: return
        val target = _uiState.value.target ?: return
        load(viewer, target, force = true)
    }

    fun openGame(game: SteamGame) {
        val viewer = viewerAccount ?: return
        val target = _uiState.value.target ?: return
        val generation = ++achievementGeneration
        val cached = cache.loadAchievements(viewer.steamId, target.steamId, game.appId)
        _uiState.value = _uiState.value.copy(
            selectedGame = game,
            achievementComparison = cached,
            achievementComparisonFromCache = cached != null,
            loadingAchievementComparison = true,
            achievementFailure = null
        )
        viewModelScope.launch {
            val result = runProfileViewerCatching {
                withContext(Dispatchers.IO) {
                    fetchAchievementsWithSessionRetry(viewer, target.steamId, game)
                }
            }.getOrElse {
                SteamProfileViewerResult.Failure(SteamProfileViewerFailureReason.NETWORK)
            }
            if (
                generation != achievementGeneration ||
                _uiState.value.selectedGame?.appId != game.appId
            ) {
                return@launch
            }
            when (result) {
                is SteamProfileViewerResult.Success -> {
                    withContext(Dispatchers.IO) { cache.saveAchievements(result.value) }
                    _uiState.value = _uiState.value.copy(
                        achievementComparison = result.value,
                        achievementComparisonFromCache = false,
                        loadingAchievementComparison = false,
                        achievementFailure = null
                    )
                }
                is SteamProfileViewerResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        loadingAchievementComparison = false,
                        achievementFailure = result.reason
                    )
                }
            }
        }
    }

    fun closeGame() {
        achievementGeneration++
        _uiState.value = _uiState.value.copy(
            selectedGame = null,
            achievementComparison = null,
            achievementComparisonFromCache = false,
            loadingAchievementComparison = false,
            achievementFailure = null
        )
    }

    private suspend fun fetchProfileWithSessionRetry(
        viewer: SteamAccount,
        target: SteamProfileViewerTarget
    ): SteamProfileViewerResult<SteamProfileViewerSnapshot> {
        val prepared = sessionResolver.resolveOrKeep(viewer, forceRefresh = false)
        val first = service.fetchProfile(prepared, target, PROFILE_LANGUAGE)
        if (
            first !is SteamProfileViewerResult.Failure ||
            first.reason != SteamProfileViewerFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = sessionResolver.resolveOrKeep(prepared, forceRefresh = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            service.fetchProfile(refreshed, target, PROFILE_LANGUAGE)
        } else {
            first
        }
    }

    private suspend fun fetchAchievementsWithSessionRetry(
        viewer: SteamAccount,
        targetSteamId: String,
        game: SteamGame
    ): SteamProfileViewerResult<SteamAchievementComparison> {
        val prepared = sessionResolver.resolveOrKeep(viewer, forceRefresh = false)
        val first = service.fetchAchievementComparison(
            prepared,
            targetSteamId,
            game,
            PROFILE_LANGUAGE
        )
        if (
            first !is SteamProfileViewerResult.Failure ||
            first.reason != SteamProfileViewerFailureReason.SESSION_REQUIRED
        ) {
            return first
        }
        val refreshed = sessionResolver.resolveOrKeep(prepared, forceRefresh = true)
        return if (refreshed.accessToken != prepared.accessToken) {
            service.fetchAchievementComparison(
                refreshed,
                targetSteamId,
                game,
                PROFILE_LANGUAGE
            )
        } else {
            first
        }
    }

    companion object {
        private const val PROFILE_LANGUAGE = "schinese"
        private const val PROFILE_CACHE_TTL_MILLIS = 15L * 60L * 1_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val accountSourceRepository = SteamAccountSourceRepository.get(appContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamProfileViewerViewModel(
                        service = SteamProfileViewerService(),
                        cache = SteamProfileViewerPreferencesCache(appContext),
                        sessionResolver = accountSourceRepository.sessionResolver()
                    ) as T
            }
        }
    }
}

private suspend fun <T> runProfileViewerCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    SteamDiagLogger.append(
        "profile_viewer request_failed type=${error::class.java.simpleName}"
    )
    Result.failure(error)
}
