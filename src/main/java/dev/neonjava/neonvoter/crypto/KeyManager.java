package dev.neonjava.neonvoter.crypto;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages the RSA key pair used for Votifier v1 protocol.
 *
 * Keys are stored in the plugin data folder under /rsa/public.key and /rsa/private.key.
 * On first start, a fresh 2048-bit RSA key pair is generated automatically.
 *
 * Server admins copy the contents of public.key to their voting site's configuration.
 */
public final class KeyManager {

    private KeyManager() {}

    /**
     * Generate a 2048-bit RSA key pair.
     */
    public static KeyPair generate() throws Exception {
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        return keygen.generateKeyPair();
    }

    /**
     * Save a key pair to the given directory as public.key / private.key (Base64).
     */
    public static void save(File directory, KeyPair keyPair) throws Exception {
        directory.mkdirs();
        byte[] pubBytes = Base64.getEncoder().encode(
                new X509EncodedKeySpec(keyPair.getPublic().getEncoded()).getEncoded());
        byte[] privBytes = Base64.getEncoder().encode(
                new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded()).getEncoded());

        try (FileOutputStream pubOut = new FileOutputStream(new File(directory, "public.key"));
             FileOutputStream privOut = new FileOutputStream(new File(directory, "private.key"))) {
            pubOut.write(pubBytes);
            privOut.write(privBytes);
        }
    }

    /**
     * Load a key pair from the given directory.
     */
    public static KeyPair load(File directory) throws Exception {
        byte[] pubEncoded = readBase64(new File(directory, "public.key"));
        byte[] privEncoded = readBase64(new File(directory, "private.key"));

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubEncoded));
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privEncoded));
        return new KeyPair(pub, priv);
    }

    /**
     * Read a Base64-encoded file and return the decoded bytes.
     */
    private static byte[] readBase64(File file) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).trim();
        return Base64.getDecoder().decode(content);
    }

    /**
     * Return the Base64-encoded public key string (what server admins paste into voting sites).
     */
    public static String getPublicKeyBase64(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
