package com.love.interviewforge.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 本地密钥的静态加密。
 *
 * <p>定位：防止 Key 被误提交、误打印、被随手打开数据库翻到。
 * <b>不是</b>强安全边界——密钥由本机信息派生，能拿到本机的人仍然可以解密。</p>
 *
 * <p>格式：{@code v1:<base64(iv | ciphertext)>}，AES-256-GCM，12 字节 IV，128 位 tag。</p>
 */
public final class Crypto {

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Crypto() {
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("密钥加密失败", e);
        }
    }

    /**
     * 解密失败返回 null（例如换了机器），由调用方视为「未配置」并提示重新录入。
     */
    public static String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 脱敏展示：sk-abc...wxyz -> sk-****wxyz */
    public static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 7) {
            return "****";
        }
        return key.substring(0, 2) + "****" + key.substring(key.length() - 4);
    }

    private static SecretKeySpec key() {
        return new SecretKeySpec(machineKeyBytes(), "AES");
    }

    /**
     * 由本机信息派生 32 字节主密钥。换机器后旧密文解不开，属于预期行为。
     */
    private static byte[] machineKeyBytes() {
        String seed = String.join("|",
                System.getProperty("os.name", "unknown"),
                System.getenv().getOrDefault("COMPUTERNAME", "unknown-host"),
                System.getenv().getOrDefault("USERNAME", "unknown-user"));
        try {
            var factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            var spec = new javax.crypto.spec.PBEKeySpec(
                    seed.toCharArray(),
                    "InterviewForge::local::v1".getBytes(StandardCharsets.UTF_8),
                    120_000,
                    256);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法派生本机密钥", e);
        }
    }
}
