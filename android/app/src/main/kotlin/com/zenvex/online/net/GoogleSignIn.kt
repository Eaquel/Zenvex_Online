package com.zenvex.online.net

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.zenvex.online.BuildConfig
import com.zenvex.online.game.Account
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * "Google ile giriş": Android Credential Manager ile Google hesabı seçilir, ID jetonu alınır ve
 * Account.google ile sunucuya (zx-google) gönderilir. İstemci kimliği BuildConfig.GOOGLE_WEB_CLIENT_ID
 * (Google Cloud → OAuth istemcisi, tür "Web application"); boşsa düğme kurulum mesajı gösterir.
 */
object GoogleSignIn {
    private val scope = MainScope()

    val available: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotEmpty()

    /** done(null) başarı; done("") kullanıcı vazgeçti; diğer metinler hata. */
    fun start(activity: Activity, link: Boolean, done: (String?) -> Unit) {
        if (!available) { done("Google girişi henüz ayarlanmadı"); return }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
        scope.launch {
            try {
                val cred = CredentialManager.create(activity).getCredential(activity, req).credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    Account.google(GoogleIdTokenCredential.createFrom(cred.data).idToken, link, done)
                } else done("Google hesabı alınamadı")
            } catch (e: GetCredentialCancellationException) {
                done("")
            } catch (e: Exception) {
                done(e.message ?: "Google girişi başarısız")
            }
        }
    }
}
