package takagi.ru.monica.steam.data

import android.content.Context
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxSourceType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.MdbxVaultStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.scanner.data.readSteamStorageSource
import takagi.ru.monica.steam.scanner.data.saveSteamStorageSource

data class SteamAccountSourceState(
    val storageSource: SteamStorageSource = SteamStorageSource.Local,
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * One source-aware account state shared by Store, Library and future account consumers.
 * It keeps local Room accounts and MDBX maFile records behind the same selection API.
 */
class SteamAccountSourceRepository private constructor(
    private val appContext: Context,
    private val localRepository: SteamAccountRepository,
    private val mdbxAccountStore: SteamMdbxAccountStore,
    passwordDatabase: PasswordDatabase
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseDao = passwordDatabase.localMdbxDatabaseDao()
    private val _state = MutableStateFlow(
        SteamAccountSourceState(storageSource = readSteamStorageSource(appContext))
    )
    val state: StateFlow<SteamAccountSourceState> = _state.asStateFlow()

    private var localAccounts: List<SteamAccount> = emptyList()
    private var mdbxRecords: List<SteamMdbxAccountRecord> = emptyList()
    private var sourceLoadGeneration = 0L

    init {
        scope.launch {
            localRepository.observeAccounts().collect { accounts ->
                localAccounts = accounts
                if (_state.value.storageSource is SteamStorageSource.Local) {
                    publishLocalAccounts(accounts)
                }
            }
        }
        scope.launch {
            databaseDao.getAllDatabases().collect { databases ->
                val supported = databases.filter(LocalMdbxDatabase::supportsSteamAccounts)
                val currentSource = _state.value.storageSource
                _state.update { it.copy(mdbxDatabases = supported) }
                if (
                    currentSource is SteamStorageSource.Mdbx &&
                    supported.none { it.id == currentSource.databaseId }
                ) {
                    selectStorageSource(SteamStorageSource.Local)
                }
            }
        }
        when (val source = _state.value.storageSource) {
            SteamStorageSource.Local -> publishLocalAccounts(localAccounts)
            is SteamStorageSource.Mdbx -> loadMdbxAccounts(source)
        }
    }

    fun selectStorageSource(source: SteamStorageSource) {
        if (_state.value.storageSource == source) return
        sourceLoadGeneration++
        saveSteamStorageSource(appContext, source)
        when (source) {
            SteamStorageSource.Local -> {
                mdbxRecords = emptyList()
                _state.update {
                    it.copy(
                        storageSource = source,
                        loading = false,
                        errorMessage = null
                    )
                }
                publishLocalAccounts(localAccounts)
            }
            is SteamStorageSource.Mdbx -> {
                _state.update {
                    it.copy(
                        storageSource = source,
                        accounts = emptyList(),
                        selectedAccountId = null,
                        loading = true,
                        errorMessage = null
                    )
                }
                loadMdbxAccounts(source)
            }
        }
    }

    fun refreshCurrentSource() {
        when (val source = _state.value.storageSource) {
            SteamStorageSource.Local -> publishLocalAccounts(localAccounts)
            is SteamStorageSource.Mdbx -> loadMdbxAccounts(source)
        }
    }

    fun selectAccount(accountId: Long) {
        val current = _state.value
        if (current.accounts.none { it.id == accountId }) return
        if (current.selectedAccountId == accountId) return
        _state.update { it.copy(selectedAccountId = accountId) }
        if (current.storageSource is SteamStorageSource.Local) {
            scope.launch { localRepository.select(accountId) }
        }
    }

    suspend fun updateSessionTokens(
        id: Long,
        accessToken: String,
        refreshToken: String?,
        steamLoginSecure: String?
    ) {
        when (val source = _state.value.storageSource) {
            SteamStorageSource.Local -> localRepository.updateSessionTokens(
                id = id,
                accessToken = accessToken,
                refreshToken = refreshToken,
                steamLoginSecure = steamLoginSecure
            )
            is SteamStorageSource.Mdbx -> {
                val record = mdbxRecords.firstOrNull { it.account.id == id } ?: return
                val updatedAccount = record.account.copy(
                    accessToken = accessToken,
                    refreshToken = refreshToken ?: record.account.refreshToken,
                    steamLoginSecure = steamLoginSecure ?: record.account.steamLoginSecure,
                    updatedAt = System.currentTimeMillis()
                )
                val updatedRecord = mdbxAccountStore.upsertAccount(
                    databaseId = source.databaseId,
                    entryId = record.entryId,
                    account = updatedAccount
                )
                mdbxRecords = mdbxRecords.map { existing ->
                    if (existing.entryId == record.entryId) updatedRecord else existing
                }
                _state.update { current ->
                    current.copy(
                        accounts = current.accounts.map { account ->
                            if (account.id == id) updatedRecord.account else account
                        }
                    )
                }
            }
        }
    }

    private fun publishLocalAccounts(accounts: List<SteamAccount>) {
        val previousId = _state.value.selectedAccountId
        val selected = accounts.firstOrNull { it.id == previousId }
            ?: accounts.firstOrNull(SteamAccount::selected)
            ?: accounts.firstOrNull()
        _state.update { current ->
            if (current.storageSource !is SteamStorageSource.Local) current
            else current.copy(
                accounts = accounts,
                selectedAccountId = selected?.id,
                loading = false,
                errorMessage = null
            )
        }
    }

    private fun loadMdbxAccounts(source: SteamStorageSource.Mdbx) {
        val generation = ++sourceLoadGeneration
        _state.update { current ->
            if (current.storageSource != source) current
            else current.copy(loading = true, errorMessage = null)
        }
        scope.launch {
            runCatching { mdbxAccountStore.loadAccounts(source.databaseId) }
                .onSuccess { records ->
                    if (generation != sourceLoadGeneration || _state.value.storageSource != source) {
                        return@onSuccess
                    }
                    mdbxRecords = records
                    val previousId = _state.value.selectedAccountId
                    val selected = records.firstOrNull { it.account.id == previousId }?.account
                        ?: records.firstOrNull { it.account.selected }?.account
                        ?: records.firstOrNull()?.account
                    _state.update {
                        it.copy(
                            accounts = records.map(SteamMdbxAccountRecord::account),
                            selectedAccountId = selected?.id,
                            loading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != sourceLoadGeneration || _state.value.storageSource != source) {
                        return@onFailure
                    }
                    mdbxRecords = emptyList()
                    _state.update {
                        it.copy(
                            accounts = emptyList(),
                            selectedAccountId = null,
                            loading = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    override fun close() = Unit

    companion object {
        @Volatile
        private var instance: SteamAccountSourceRepository? = null

        fun get(context: Context): SteamAccountSourceRepository = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(appContext: Context): SteamAccountSourceRepository {
            val steamDatabase = SteamDatabase.getDatabase(appContext)
            val passwordDatabase = PasswordDatabase.getDatabase(appContext)
            val securityManager = SecurityManager(appContext)
            val mdbxRepository = MdbxVaultStore(
                context = appContext,
                databaseDao = passwordDatabase.localMdbxDatabaseDao(),
                securityManager = securityManager,
                remoteSourceDao = passwordDatabase.mdbxRemoteSourceDao(),
                passwordEntryDao = passwordDatabase.passwordEntryDao(),
                secureItemDao = passwordDatabase.secureItemDao(),
                customFieldDao = passwordDatabase.customFieldDao()
            )
            return SteamAccountSourceRepository(
                appContext = appContext,
                localRepository = SteamAccountRepository(
                    steamDatabase.steamAccountDao(),
                    securityManager
                ),
                mdbxAccountStore = SteamMdbxAccountStore(mdbxRepository),
                passwordDatabase = passwordDatabase
            )
        }
    }
}

private fun LocalMdbxDatabase.supportsSteamAccounts(): Boolean =
    sourceTypeEnum == MdbxSourceType.LOCAL_INTERNAL ||
        sourceTypeEnum == MdbxSourceType.LOCAL_EXTERNAL ||
        sourceTypeEnum == MdbxSourceType.REMOTE_WEBDAV

