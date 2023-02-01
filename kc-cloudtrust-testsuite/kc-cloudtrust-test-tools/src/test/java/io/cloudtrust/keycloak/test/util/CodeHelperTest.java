package io.cloudtrust.keycloak.test.util;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CodeHelperTest {
    @Test
    void wrongCodeTest() {
        Stream.of("123456", "|abc ABC 123|", "0000", "9999").forEach(v -> Assertions.assertNotEquals(v, CodeHelper.wrongCode(v)));
    }
}
