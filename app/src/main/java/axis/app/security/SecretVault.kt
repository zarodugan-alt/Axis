package axis.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * API-key vault: AES-256-GCM with the key held in the Android Keystore
 * (hardware-backed where available). Ciphertext is stored as
 * `v1:<iv-b64>:<ct-b64>`; the key material never leaves the Keystore, so a
 * copied DataStore file is useless on another device.
 *
 * Degradation: if the Keystore is unavailable (rare, some emulators), the
 * vault transparently falls back to `plain:` + base64 so the app still
 * works — [hardwareBacked] and [degraded] report that honestly in the UI
 * rather than pretending the key is protected.
 */
@Singleton
class SecretVault @Inject constructor() {

    private val alias = "axis_key_vault_v1"
    private val transformation = "AES/GCM/NoPadding"
    private val ivLength = 12
    private val tagBits = 128

    var degraded: Boolean = false
        private set

    val hardwareBacked: Boolean
        get() = runCatching { secretKey() != null }.getOrDefault(false)

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, requireKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            "v1:" + b64(iv) + ":" + b64(ct)
        } catch (t: Throwable) {
            degraded = true
            Timber.w(t, "Keystore unavailable — storing with base64 fallback")
            "plain:" + b64(plain.toByteArray(Charsets.UTF_8))
        }
    }

    fun decrypt(blob: String?): String? {
        if (blob.isNullOrEmpty()) return null
        return try {
            when {
                blob.startsWith("v1:") -> {
                    val parts = blob.split(':')
                    if (parts.size != 3) return null
                    val iv = unb64(parts[1])
                    val ct = unb64(parts[2])
                    val cipher = Cipher.getInstance(transformation)
                    cipher.init(Cipher.DECRYPT_MODE, requireKey(), GCMParameterSpec(tagBits, iv))
                    cipher.doFinal(ct).toString(Charsets.UTF_8)
                }
                blob.startsWith("plain:") -> {
                    degraded = true
                    unb64(blob.removePrefix("plain:")).toString(Charsets.UTF_8)
                }
                else -> null
            }
        } catch (t: Throwable) {
            // Tampered blob, or the Keystore entry was invalidated (e.g. after
            // a device restore). Treat as "no key" so the user re-pastes.
            Timber.w(t, "Vault decrypt failed — dropping stored secret")
            null
        }
    }

    private fun requireKey(): SecretKey = secretKey() ?: throw IllegalStateException("no keystore")

    private fun secretKey(): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
