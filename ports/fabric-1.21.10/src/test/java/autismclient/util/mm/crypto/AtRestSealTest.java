package autismclient.util.mm.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Tests encryption-result policy without changing native crypto, providers, or filesystem permissions. */
public final class AtRestSealTest {
    public static void main(String[] args) {
        int checks = 0;
        byte[] dpapi = new byte[]{11, 22, 33};
        byte[] aes = new byte[]{44, 55, 66};
        byte[] windowsRecord = AtRestSeal.selectEncryptedResult(dpapi, null);
        check(Arrays.equals(windowsRecord, new byte[]{1, 11, 22, 33}), "DPAPI ciphertext uses existing marker 1"); checks++;
        byte[] aesRecord = AtRestSeal.selectEncryptedResult(null, aes);
        check(Arrays.equals(aesRecord, new byte[]{2, 44, 55, 66}), "AES ciphertext uses existing marker 2"); checks++;
        check(Arrays.equals(AtRestSeal.selectEncryptedResult(dpapi, aes), windowsRecord), "DPAPI takes precedence"); checks++;
        dpapi[0] = 99;
        aes[0] = 99;
        check(windowsRecord[1] == 11 && aesRecord[1] == 44, "Persisted encrypted records own their bytes"); checks++;
        try {
            AtRestSeal.selectEncryptedResult(null, null);
            throw new AssertionError("Both encryption failures must reject the new write");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("Could not encrypt account credentials. No plaintext record was created."),
                "Encryption failure uses a fixed safe error"); checks++;
            check(expected.getCause() == null, "Encryption failure does not retain unsafe native exception text"); checks++;
        }
        byte[] synthetic = "legacy-synthetic-credential".getBytes(StandardCharsets.UTF_8);
        byte[] legacy = new byte[synthetic.length + 1];
        System.arraycopy(synthetic, 0, legacy, 1, synthetic.length);
        check(Arrays.equals(AtRestSeal.unseal(legacy), synthetic), "Existing marker 0 records remain readable"); checks++;
        check(AtRestSeal.unseal(new byte[]{99, 1}) == null, "Unknown storage marker rejected"); checks++;
        check(AtRestSeal.unseal(new byte[0]) == null && AtRestSeal.unseal(null) == null, "Missing record rejected"); checks++;
        System.out.println("AtRestSealTest: " + checks + " checks passed (synthetic encryption policy; legacy reads retained).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
