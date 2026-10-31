package com.sonnik.pin.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Вход в Google Диск через Authorization API: только область `drive.file`.
 * OAuth-клиент Android в Google Cloud опознаёт Pin по пакету и SHA-1 ключа подписи — секретов в приложении нет.
 */
object DriveAuth {
    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE))).build()

    /** Есть ли на телефоне сервисы Google Play — без них вход не работает. */
    fun playServicesAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Запросить доступ из экрана: результат может потребовать показать окно выбора аккаунта ([AuthorizationResult.hasResolution]). */
    fun authorize(activity: Activity): Task<AuthorizationResult> = Identity.getAuthorizationClient(activity).authorize(request)

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Токен в фоне (не на главном потоке). Если нужен экран входа — [NeedsSignInException]. */
    fun tokenBlocking(context: Context): String {
        val result = try {
            Tasks.await(Identity.getAuthorizationClient(context).authorize(request), 30, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw NeedsSignInException(describe(e.cause ?: e))
        }
        if (result.hasResolution()) throw NeedsSignInException()
        return result.accessToken ?: throw NeedsSignInException()
    }

    /** Понятное описание ошибки входа. */
    fun describe(e: Throwable): String = when ((e as? ApiException)?.statusCode) {
        10 -> "Google не узнал приложение: проверьте OAuth-клиент Android в Google Cloud (пакет com.sonnik.pin и SHA-1)"
        7 -> "Нет связи с Google — проверьте интернет или попробуйте через VPN"
        16 -> "Вход отменён"
        else -> e.message ?: e.javaClass.simpleName
    }
}
