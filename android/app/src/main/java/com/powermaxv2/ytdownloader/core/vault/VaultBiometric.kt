package com.powermaxv2.ytdownloader.core.vault

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Fingerprint unlock of the Coffre-fort (Android 11+, strong biometrics only): a second copy of the master key,
 * wrapped by a key of the phone's Keystore that only opens after a fingerprint (or face, when the phone counts it as
 * strong). That Keystore key never leaves the phone's secure hardware and dies when a new fingerprint is enrolled,
 * so the code is needed again then. The code always works too.
 */
object VaultBiometric {
    private const val ALIAS = "gulp-vault-fingerprint"
    private const val FILE = "fingerprint.bin"

    /** The phone can do it: Android 11+ and a strong biometric enrolled. */
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enabled(vaultDir: File): Boolean = File(vaultDir, FILE).isFile

    /** Turns it off: the wrapped copy and the Keystore key are deleted. */
    fun disable(vaultDir: File) {
        File(vaultDir, FILE).delete()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    /**
     * Asks for a fingerprint, then stores [master] wrapped by a new Keystore key. [done] gets true when it is on.
     * Called on the main thread.
     */
    fun enable(context: Context, vaultDir: File, master: ByteArray, done: (Boolean, String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return done(false, "Il faut Android 11 ou plus.")
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, newKey()) }
        } catch (e: Exception) {
            return done(false, "Le téléphone refuse de créer la clé d'empreinte.")
        }
        prompt(context, "Activer l'empreinte", "Pose ton doigt pour ouvrir le coffre sans code.", cipher, onFail = { done(false, it) }) { ready ->
            try {
                val sealed = ready.doFinal(master)
                File(vaultDir, FILE).writeBytes(ready.iv + sealed)
                done(true, null)
            } catch (e: Exception) {
                done(false, "L'empreinte n'a pas pu être enregistrée.")
            } finally {
                master.fill(0)
            }
        }
    }

    /**
     * Asks for a fingerprint and gives back the raw master key. [onFail] gets a message to show, or null when the
     * user just closed the prompt. When a fingerprint was added to the phone since, the copy is dropped.
     */
    fun unlock(context: Context, vaultDir: File, onFail: (String?) -> Unit, onKey: (ByteArray) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return onFail(null)
        val bytes = runCatching { File(vaultDir, FILE).readBytes() }.getOrNull()
        if (bytes == null || bytes.size <= 12) return onFail("L'empreinte n'est pas activée.")
        val cipher = try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(vaultDir)
            return onFail("Les empreintes du téléphone ont changé : tape ton code, puis réactive l'empreinte.")
        } catch (e: Exception) {
            return onFail("L'empreinte ne marche pas sur ce téléphone, tape ton code.")
        }
        prompt(context, "Ouvrir le coffre", "Pose ton doigt, ou tape ton code.", cipher, onFail = onFail) { ready ->
            try {
                onKey(ready.doFinal(bytes, 12, bytes.size - 12))
            } catch (e: Exception) {
                onFail("L'empreinte n'a pas ouvert le coffre, tape ton code.")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun prompt(
        context: Context,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onFail: (String?) -> Unit,
        onOk: (Cipher) -> Unit,
    ) {
        val executor = context.mainExecutor
        val dialog = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton("Code", executor) { _, _ -> onFail(null) }
            .build()
        dialog.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val ready = result.cryptoObject?.cipher
                    if (ready == null) onFail("L'empreinte n'a pas ouvert le coffre, tape ton code.") else onOk(ready)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val closed = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON
                    onFail(if (closed) null else errString.toString())
                }
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun newKey(): SecretKey {
        runCatching { keyStore().deleteEntry(ALIAS) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
