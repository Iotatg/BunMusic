package app.bunmusic.data

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.security.MessageDigest

/**
 * Google Sign-In.
 *
 * Google will not issue an ID token without an OAuth client that knows this app's
 * package name and signing certificate, so the client id is configuration, not code.
 * It lives in Settings → Google sign-in, and this object reports precisely what is
 * missing instead of pretending a login happened.
 */
object GoogleAuth {

    sealed class Failure(message: String) : Exception(message) {
        class NotConfigured : Failure("No Google client ID yet")
        class Cancelled : Failure("Sign-in cancelled")
        class NoAccounts : Failure("No Google account on this device")
        class Other(message: String) : Failure(message)
    }

    /** Builds the account picker intent, or null when there is no client id yet. */
    fun signInIntent(context: Context, clientId: String): Intent? {
        if (clientId.isBlank()) return null
        val activity = context.findActivity() ?: return null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(clientId.trim())
            .build()
        return runCatching { GoogleSignIn.getClient(activity, options).signInIntent }.getOrNull()
    }

    /** Pulls the verified ID token out of the picker's result. */
    fun idTokenFrom(data: Intent?): String {
        if (data == null) throw Failure.Cancelled()
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            throw when (e.statusCode) {
                12501 -> Failure.Cancelled()
                12500 -> Failure.Other("Google Play services is out of date")
                else -> Failure.Other("Google sign-in failed (${e.statusCode})")
            }
        } catch (e: Exception) {
            throw Failure.Other(e.message ?: "Google sign-in failed")
        }
        if (account == null) throw Failure.NoAccounts()
        return account.idToken ?: throw Failure.Other("Google returned no ID token")
    }

    /** The SHA-1 a Google OAuth client must be registered with. */
    fun signingSha1(context: Context): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val bytes = signatures?.firstOrNull()?.toByteArray() ?: return@runCatching ""
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(":") { "%02X".format(it) }
    }.getOrDefault("")

    private fun Context.findActivity(): android.app.Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return current as? android.app.Activity
    }
}
