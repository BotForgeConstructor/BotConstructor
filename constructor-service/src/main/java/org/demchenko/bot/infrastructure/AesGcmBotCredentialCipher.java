package org.demchenko.bot.infrastructure;

import org.demchenko.bot.application.BotCredentialCipher;
import org.demchenko.bot.config.CredentialEncryptionProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public final class AesGcmBotCredentialCipher implements BotCredentialCipher {
    private final CredentialEncryptionProperties properties;
    private final SecureRandom random = new SecureRandom();
    public AesGcmBotCredentialCipher(CredentialEncryptionProperties properties) { this.properties=properties; properties.decodedKey(); }
    public String encrypt(String plaintext, String aad) {
        try { byte[] iv=new byte[12]; random.nextBytes(iv); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(properties.decodedKey(),"AES"),new GCMParameterSpec(128,iv)); c.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); byte[] out=c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)); return "v1:"+properties.keyId()+":"+b64(iv)+":"+b64(out); }
        catch(Exception e){ throw new IllegalStateException("credential encryption failed"); }
    }
    public String decrypt(String envelope, String aad) {
        try { String[] p=envelope.split(":",-1); if(p.length!=4||!p[0].equals("v1")||!p[1].equals(properties.keyId())) throw new IllegalStateException(); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(properties.decodedKey(),"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(p[2]))); c.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); return new String(c.doFinal(Base64.getDecoder().decode(p[3])),StandardCharsets.UTF_8); }
        catch(Exception e){ throw new IllegalStateException("credential decryption failed"); }
    }
    private static String b64(byte[] b){ return Base64.getEncoder().withoutPadding().encodeToString(b); }
}
