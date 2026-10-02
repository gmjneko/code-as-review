package org.koaks.codereview.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("unit-test-passphrase");

    @Test
    void roundTripsAndUsesFreshIv() {
        String a = cipher.encrypt("sk-secret-value");
        String b = cipher.encrypt("sk-secret-value");
        assertThat(a).startsWith("v1:").isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("sk-secret-value");
    }

    @Test
    void rejectsTamperedOrForeignCiphertext() {
        String stored = cipher.encrypt("value");
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A") ? "BB" : "AA");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher("other").decrypt(stored)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("plain")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masksSecrets() {
        assertThat(SecretCipher.mask("sk-1234567890abcd")).isEqualTo("sk-1****abcd");
        assertThat(SecretCipher.mask("short")).isEqualTo("****");
    }
}
