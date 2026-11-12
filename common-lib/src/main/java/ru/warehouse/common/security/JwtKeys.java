package ru.warehouse.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * P2-11: работа с RSA-ключами подписи JWT (RS256). Только JDK, без зависимостей от Spring.
 * Формат файлов: PEM, приватный ключ в PKCS#8 ("BEGIN PRIVATE KEY"), публичный в X.509 ("BEGIN PUBLIC KEY").
 * Приватные ключи хранятся вне репозитория, путь задаётся переменной окружения.
 */
public final class JwtKeys {
    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);
    private static final int KEY_BITS = 2048;

    private JwtKeys() {}

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось создать RSA-ключ", e);
        }
    }

    /**
     * Читает приватный ключ из файла. Если файла нет и generateIfMissing=true (только профиль dev),
     * создаёт новую пару и сохраняет приватный ключ в этот файл. Иначе остановка с понятным сообщением:
     * молча подменять ключ рабочего сервиса нельзя, все выданные токены перестали бы приниматься.
     */
    public static RSAPrivateCrtKey loadOrCreatePrivateKey(String file, boolean generateIfMissing, String purpose) {
        Path path = Path.of(file);
        if (Files.exists(path)) {
            return readPrivateKey(path);
        }
        if (!generateIfMissing) {
            throw new IllegalStateException("Файл ключа (" + purpose + ") не найден: " + path + ". Создайте его "
                    + "(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out <файл>) "
                    + "или запустите сервис с профилем dev, он создаст ключ сам.");
        }
        KeyPair pair = generate();
        writePrivateKey(path, pair.getPrivate());
        log.warn("Создан новый ключ ({}): {}. Автосоздание допустимо только для локальной разработки.", purpose, path);
        return (RSAPrivateCrtKey) pair.getPrivate();
    }

    public static RSAPrivateCrtKey readPrivateKey(Path file) {
        String pem = read(file);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("Ключ " + file + " в формате PKCS#1. Преобразуйте: "
                    + "openssl pkcs8 -topk8 -nocrypt -in <старый> -out <новый>");
        }
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateCrtKey crt)) {
                throw new IllegalStateException("Ключ " + file + " не RSA с китайской теоремой об остатках (CRT)");
            }
            if (crt.getModulus().bitLength() < KEY_BITS) {
                throw new IllegalStateException("Ключ " + file + " короче " + KEY_BITS + " бит");
            }
            return crt;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Не удалось прочитать приватный ключ " + file + " (нужен PKCS#8 PEM)", e);
        }
    }

    public static RSAPublicKey readPublicKey(Path file) {
        String pem = read(file);
        try {
            byte[] der = decodePem(pem, "PUBLIC KEY");
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof RSAPublicKey rsa)) {
                throw new IllegalStateException("Ключ " + file + " не RSA");
            }
            if (rsa.getModulus().bitLength() < KEY_BITS) {
                throw new IllegalStateException("Ключ " + file + " короче " + KEY_BITS + " бит");
            }
            return rsa;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Не удалось прочитать публичный ключ " + file + " (нужен X.509 PEM)", e);
        }
    }

    public static RSAPublicKey publicKeyOf(RSAPrivateCrtKey key) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось получить публичный ключ", e);
        }
    }

    public static void writePrivateKey(Path file, PrivateKey key) {
        write(file, toPem("PRIVATE KEY", key.getEncoded()), true);
    }

    public static void writePublicKey(Path file, PublicKey key) {
        write(file, toPem("PUBLIC KEY", key.getEncoded()), false);
    }

    /** Идентификатор ключа (kid): SHA-256 от X.509-представления публичного ключа, base64url. */
    public static String keyId(RSAPublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл ключа " + file, e);
        }
    }

    private static void write(Path file, String pem, boolean secret) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, pem, StandardCharsets.US_ASCII);
            if (secret && file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось записать файл ключа " + file, e);
        }
    }

    private static String toPem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private static byte[] decodePem(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int from = pem.indexOf(begin);
        int to = pem.indexOf(end);
        if (from < 0 || to < from) {
            throw new IllegalArgumentException("Нет блока " + begin);
        }
        String body = pem.substring(from + begin.length(), to).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
