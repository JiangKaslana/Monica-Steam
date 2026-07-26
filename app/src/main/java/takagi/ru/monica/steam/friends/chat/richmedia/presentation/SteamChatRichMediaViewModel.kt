package takagi.ru.monica.steam.friends.chat.richmedia.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatAttachmentUploader
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatCatalogService
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCatalogGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatPendingAttachment
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

data class SteamChatRichMediaUiState(
    val emoticons: List<SteamChatEmoticon> = emptyList(),
    val stickers: List<SteamChatSticker> = emptyList(),
    val effects: List<SteamChatEffect> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogFailure: Boolean = false,
    val pendingAttachment: SteamChatPendingAttachment? = null,
    val attachmentSpoiler: Boolean = false,
    val attachmentPreparing: Boolean = false,
    val attachmentUploading: Boolean = false,
    val attachmentProgress: Float = 0f,
    val attachmentFailure: String? = null,
    val uploadCompletedAt: Long = 0L
)

class SteamChatRichMediaViewModel(
    private val catalogGateway: SteamChatCatalogGateway,
    private val attachmentGateway: SteamChatAttachmentGateway,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamChatRichMediaUiState())
    val uiState: StateFlow<SteamChatRichMediaUiState> = _uiState.asStateFlow()

    private var account: SteamAccount? = null
    private var partnerSteamId: String? = null
    private var catalogGeneration = 0L
    private var attachmentGeneration = 0L
    private var attachmentPreparationJob: Job? = null
    private var attachmentUploadJob: Job? = null

    fun selectAccount(account: SteamAccount?) {
        if (sameRichMediaAccount(this.account, account)) {
            this.account = account
            return
        }
        invalidateAttachmentRequests()
        this.account = account
        catalogGeneration++
        _uiState.value = SteamChatRichMediaUiState(catalogLoading = account != null)
        if (account != null) loadCatalogs(account, catalogGeneration)
    }

    fun selectPartner(steamId: String?) {
        if (partnerSteamId == steamId) return
        partnerSteamId = steamId
        clearAttachment()
    }

    fun refreshCatalogs() {
        val current = account ?: return
        catalogGeneration++
        _uiState.value = _uiState.value.copy(catalogLoading = true, catalogFailure = false)
        loadCatalogs(current, catalogGeneration)
    }

    fun selectAttachment(rawUri: String) {
        if (rawUri.isBlank() || _uiState.value.attachmentUploading) return
        attachmentPreparationJob?.cancel()
        attachmentGeneration++
        val generation = attachmentGeneration
        _uiState.update {
            it.copy(
                pendingAttachment = null,
                attachmentSpoiler = false,
                attachmentPreparing = true,
                attachmentFailure = null,
                attachmentProgress = 0f
            )
        }
        attachmentPreparationJob = viewModelScope.launch {
            val result = suspendResult {
                withContext(ioDispatcher) { attachmentGateway.inspect(rawUri) }
            }
            if (generation != attachmentGeneration) return@launch
            result.fold(
                onSuccess = { attachment ->
                    _uiState.update {
                        it.copy(
                            pendingAttachment = attachment,
                            attachmentPreparing = false,
                            attachmentFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            pendingAttachment = null,
                            attachmentPreparing = false,
                            attachmentFailure = error.userFacingMessage()
                        )
                    }
                }
            )
        }
    }

    fun setAttachmentSpoiler(spoiler: Boolean) {
        if (_uiState.value.attachmentUploading) return
        _uiState.value = _uiState.value.copy(attachmentSpoiler = spoiler)
    }

    fun uploadAttachment() {
        val currentAccount = account ?: return
        val currentPartner = partnerSteamId ?: return
        val attachment = _uiState.value.pendingAttachment ?: return
        if (_uiState.value.attachmentUploading) return
        val spoiler = _uiState.value.attachmentSpoiler
        val generation = attachmentGeneration
        _uiState.update {
            it.copy(
                attachmentUploading = true,
                attachmentProgress = 0f,
                attachmentFailure = null
            )
        }
        attachmentUploadJob?.cancel()
        attachmentUploadJob = viewModelScope.launch {
            val result = suspendResult {
                withContext(ioDispatcher) {
                    attachmentGateway.upload(
                        account = sessionResolver.resolveOrKeep(currentAccount),
                        partnerSteamId = currentPartner,
                        attachment = attachment,
                        spoiler = spoiler,
                        onProgress = { progress ->
                            if (isCurrentAttachmentRequest(generation, currentAccount, currentPartner)) {
                                _uiState.update {
                                    it.copy(attachmentProgress = progress.coerceIn(0f, 1f))
                                }
                            }
                        }
                    )
                }
            }
            if (!isCurrentAttachmentRequest(generation, currentAccount, currentPartner)) {
                return@launch
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            pendingAttachment = null,
                            attachmentSpoiler = false,
                            attachmentUploading = false,
                            attachmentProgress = 0f,
                            attachmentFailure = null,
                            uploadCompletedAt = nowMillis()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            attachmentUploading = false,
                            attachmentProgress = 0f,
                            attachmentFailure = error.userFacingMessage()
                        )
                    }
                }
            )
        }
    }

    fun clearAttachment() {
        invalidateAttachmentRequests()
        _uiState.update {
            it.copy(
                pendingAttachment = null,
                attachmentSpoiler = false,
                attachmentPreparing = false,
                attachmentUploading = false,
                attachmentProgress = 0f,
                attachmentFailure = null
            )
        }
    }

    fun clearAttachmentFailure() {
        _uiState.value = _uiState.value.copy(attachmentFailure = null)
    }

    private fun loadCatalogs(account: SteamAccount, generation: Long) {
        viewModelScope.launch {
            val catalogResult = async(ioDispatcher) {
                suspendResult {
                    catalogGateway.loadCatalog(sessionResolver.resolveOrKeep(account))
                }
            }.await()
            if (generation != catalogGeneration ||
                !sameRichMediaAccount(this@SteamChatRichMediaViewModel.account, account)
            ) {
                return@launch
            }
            val catalog = catalogResult.getOrNull()
            _uiState.value = _uiState.value.copy(
                emoticons = catalog?.emoticons.orEmpty(),
                stickers = catalog?.stickers.orEmpty(),
                effects = catalog?.effects.orEmpty(),
                catalogLoading = false,
                catalogFailure = catalogResult.isFailure
            )
        }
    }

    private fun invalidateAttachmentRequests() {
        attachmentGeneration++
        attachmentPreparationJob?.cancel()
        attachmentPreparationJob = null
        attachmentUploadJob?.cancel()
        attachmentUploadJob = null
    }

    private fun isCurrentAttachmentRequest(
        generation: Long,
        expectedAccount: SteamAccount,
        expectedPartner: String
    ): Boolean = generation == attachmentGeneration &&
        sameRichMediaAccount(account, expectedAccount) &&
        partnerSteamId == expectedPartner

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamChatRichMediaViewModel(
                        catalogGateway = SteamChatCatalogService(),
                        attachmentGateway = SteamChatAttachmentUploader(appContext),
                        sessionResolver = SteamAccountSourceRepository
                            .get(appContext)
                            .sessionResolver()
                    ) as T
            }
        }
    }
}

private suspend fun <T> suspendResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

private fun sameRichMediaAccount(left: SteamAccount?, right: SteamAccount?): Boolean =
    left?.id == right?.id &&
        left?.steamId == right?.steamId &&
        left?.accessToken == right?.accessToken &&
        left?.steamLoginSecure == right?.steamLoginSecure

private fun Throwable.userFacingMessage(): String = message
    ?.takeIf(String::isNotBlank)
    ?.take(240)
    ?: "Steam attachment operation failed"
