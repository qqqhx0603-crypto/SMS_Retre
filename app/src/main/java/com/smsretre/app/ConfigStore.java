package com.smsretre.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ConfigStore {
    private static final String PREFS = "config";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SENDER = "sender_email";
    private static final String KEY_RECIPIENT = "recipient_email";
    private static final String KEY_AUTH_BLOB = "auth_blob";
    private static final String KEY_ALIAS = "sms_retre_smtp_auth";
    private static final String KEYSTORE = "AndroidKeyStore";

    private final SharedPreferences prefs;

    ConfigStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    MailConfig load() {
        return new MailConfig(
                prefs.getBoolean(KEY_ENABLED, false),
                prefs.getString(KEY_SENDER, ""),
                getAuthCode(),
                prefs.getString(KEY_RECIPIENT, "")
        );
    }

    boolean hasAuthCode() {
        String blob = prefs.getString(KEY_AUTH_BLOB, "");
        return blob != null && !blob.isEmpty();
    }

    void save(boolean enabled, String senderEmail, String recipientEmail, String authCodeIfChanged) throws Exception {
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_SENDER, senderEmail == null ? "" : senderEmail.trim())
                .putString(KEY_RECIPIENT, recipientEmail == null ? "" : recipientEmail.trim());
        if (authCodeIfChanged != null && !authCodeIfChanged.trim().isEmpty()) {
            editor.putString(KEY_AUTH_BLOB, encrypt(authCodeIfChanged.trim()));
        }
        editor.apply();
    }

    private String getAuthCode() {
        String blob = prefs.getString(KEY_AUTH_BLOB, "");
        if (blob == null || blob.isEmpty()) {
            return "";
        }
        try {
            return decrypt(blob);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String encrypt(String plainText) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decrypt(String blob) throws Exception {
        String[] parts = blob.split(":", 2);
        if (parts.length != 2) {
            return "";
        }
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(encrypted);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
