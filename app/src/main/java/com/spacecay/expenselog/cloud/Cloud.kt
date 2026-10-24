package com.spacecay.expenselog.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.spacecay.expenselog.backup.Backup
import com.spacecay.expenselog.data.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Account + cloud copy of all data in Firestore under users/{uid}.
 * The whole book set is stored as a snapshot (split into ~800 KB chunks), uploaded when
 * something changed, and restored on a new phone after signing in.
 */
object Cloud {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val lock = Mutex()
    private const val CHUNK = 800_000

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user

    data class CloudInfo(val updatedAt: Long, val device: String, val transactions: Int)

    @Volatile private var inited = false

    fun init() {
        if (inited) return
        inited = true
        try {
            _user.value = auth.currentUser
            auth.addAuthStateListener { _user.value = it.currentUser }
        } catch (_: Exception) {
        }
    }

    val signedIn get() = _user.value != null

    /** The OAuth web client id that google-services.json provides once Google sign-in is enabled. */
    private fun webClientId(ctx: Context): String? {
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else null
    }

    fun googleAvailable(ctx: Context) = webClientId(ctx) != null

    suspend fun signInWithGoogle(activity: Activity) {
        val clientId = webClientId(activity) ?: error("Google sign-in isn't set up in Firebase yet")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        } else error("Unexpected sign-in result")
    }

    suspend fun signInEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        auth.signOut()
        Graph.prefs.lastCloudSync = 0L
    }

    private fun userDoc() = db.collection("users").document(auth.currentUser?.uid ?: error("Not signed in"))

    suspend fun cloudInfo(): CloudInfo? {
        val meta = userDoc().collection("data").document("meta").get().await()
        if (!meta.exists()) return null
        return CloudInfo(meta.getLong("updatedAt") ?: 0L, meta.getString("device").orEmpty(), (meta.getLong("transactions") ?: 0L).toInt())
    }

    /** Upload everything now. */
    suspend fun upload(): Unit = lock.withLock {
        val json = Backup.toJson()
        val chunks = json.chunked(CHUNK)
        val data = userDoc().collection("data")
        val oldCount = (data.document("meta").get().await().getLong("chunks") ?: 0L).toInt()
        val now = System.currentTimeMillis()
        val batch = db.batch()
        chunks.forEachIndexed { i, c -> batch.set(data.document("chunk_$i"), mapOf("d" to c)) }
        for (i in chunks.size until oldCount) batch.delete(data.document("chunk_$i"))
        batch.set(
            data.document("meta"),
            mapOf(
                "chunks" to chunks.size, "updatedAt" to now, "device" to android.os.Build.MODEL,
                "transactions" to Graph.dao.allTxns().size, "app" to "ExpenseLog", "version" to 1,
            ),
            SetOptions.merge(),
        )
        batch.commit().await()
        Graph.prefs.lastCloudSync = now
    }

    /** Replace local data with the cloud copy. */
    suspend fun download(): Boolean = lock.withLock {
        val data = userDoc().collection("data")
        val meta = data.document("meta").get().await()
        if (!meta.exists()) return@withLock false
        val n = (meta.getLong("chunks") ?: 0L).toInt()
        val sb = StringBuilder()
        for (i in 0 until n) sb.append(data.document("chunk_$i").get().await().getString("d").orEmpty())
        Backup.restore(sb.toString())
        Graph.prefs.lastCloudSync = System.currentTimeMillis()
        Graph.prefs.lastLocalChange = Graph.prefs.lastCloudSync - 1
        true
    }

    /** Upload if anything changed since the last sync. Safe to call often. */
    suspend fun syncIfNeeded() {
        if (!signedIn) return
        val p = Graph.prefs
        if (p.lastLocalChange > p.lastCloudSync) {
            try { upload() } catch (_: Exception) {}
        }
    }
}
