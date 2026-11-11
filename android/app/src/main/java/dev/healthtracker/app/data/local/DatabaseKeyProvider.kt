package dev.healthtracker.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supplies the SQLCipher passphrase. A random 32-byte passphrase is generated once, wrapped
 * with an AES-GCM key that never leaves Android Keystore, and only the wrapped form is stored.
 */
class DatabaseKeyProvider(private val context: Context) {

    fun getOrCreatePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_WRAPPED, null)
        if (stored != null) {
            val parts = stored.split(':')
            return decrypt(Base64.decode(parts[0], Base64.NO_WRAP), Base64.decode(parts[1], Base64.NO_WRAP))
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val wrapped = cipher.doFinal(passphrase)
        prefs.edit {
            putString(
                KEY_WRAPPED,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(wrapped, Base64.NO_WRAP),
            )
        }
        return passphrase
    }

    private fun decrypt(iv: ByteArray, wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(wrapped)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "health_db_key"
        const val PREFS = "db_key"
        const val KEY_WRAPPED = "wrapped_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
