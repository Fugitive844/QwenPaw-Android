package cn.qwenpaw.android;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Passwords are never persisted. Tokens are encrypted with a non-exportable device key. */
public final class TokenVault {
    private final Context context;
    public TokenVault(Context context) { this.context=context; }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("qwenpaw-token")) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("qwenpaw-token",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey("qwenpaw-token",null);
    }
    public void save(String token) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        context.getSharedPreferences("vault",0).edit()
            .putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP))
            .putString("token",Base64.encodeToString(c.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP)).apply();
    }
    public String read() {
        try {
            var p=context.getSharedPreferences("vault",0); String t=p.getString("token",""); if(t.isEmpty()) return "";
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(t,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        } catch(Exception e) { clear(); return ""; }
    }
    public void clear() { context.getSharedPreferences("vault",0).edit().clear().apply(); }
}
