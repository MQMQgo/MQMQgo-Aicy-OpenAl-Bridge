package io.github.aicybridge;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** Credential encryption boundary; tests can exercise persistence without replacing app preferences. */
interface SecretCodec {
    String encrypt(String value) throws GeneralSecurityException, IOException;
    String decrypt(String value) throws GeneralSecurityException, IOException;
}
