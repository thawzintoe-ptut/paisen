package com.ptut.expense.sync

import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.FirebaseAuthApi
import com.ptut.expense.sync.auth.FirebaseAuthRepository
import com.ptut.expense.sync.auth.GoogleOAuth
import com.ptut.expense.sync.auth.tokenStore
import com.ptut.expense.sync.push.PushRegistrar
import com.ptut.expense.sync.push.PushSender
import com.ptut.expense.sync.push.PushTokenHolder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

/**
 * #187–#190 in the graph. The only place an HTTP client is built, so `di/` names none (#191).
 * Everything here is bound whether or not `firebase.properties` exists: without it,
 * [AuthRepository.available] is false and nothing ever calls out.
 */
fun syncModule(databaseContext: PtutDatabaseContext): Module = module {
    // #193: no call waits for ever; a hung request would hold the sync lock behind it.
    single {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds
                requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
                socketTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
            }
        }
    }
    single<AuthRepository> {
        FirebaseAuthRepository(
            api = FirebaseAuthApi(get(), FirebaseConfig.API_KEY),
            store = tokenStore(databaseContext),
            available = FirebaseConfig.enabled,
        )
    }
    single { GoogleOAuth(get(), FirebaseConfig.GOOGLE_IOS_CLIENT_ID) }
    // The platform's push service hands its token here; registering waits for a signed-in account.
    single { PushTokenHolder() }
    single {
        val auth = get<AuthRepository>()
        val firestore = FirestoreClient(get(), FirebaseConfig.PROJECT_ID) { auth.idToken() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val push = FirebaseConfig.pushEnabled
        val registrar = PushRegistrar(auth, get(), firestore, enabled = push).also { it.start(scope) }
        SharedSync(
            auth = auth,
            firestore = firestore,
            repository = get(),
            dao = get<PtutDatabase>().sharedDao(),
            pushes = if (push) PushSender(get(), { auth.idToken() }, FirebaseConfig.PUSH_URL, scope) else null,
            registrar = registrar.takeIf { push },
        ).also { it.uploadSoonAfterSaves(scope) }
    }
}

/** The privacy notice's "where" (#191): the region from `firebase.properties`. */
val sharedDataRegion: String get() = FirebaseConfig.REGION.ifEmpty { "Google Cloud" }

/** The Google web client id Credential Manager asks for on Android (#187). */
internal val googleWebClientId: String get() = FirebaseConfig.GOOGLE_WEB_CLIENT_ID

private val CONNECT_TIMEOUT = 10.seconds
private val REQUEST_TIMEOUT = 15.seconds
