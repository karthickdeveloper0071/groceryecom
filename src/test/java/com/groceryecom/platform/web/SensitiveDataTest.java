package com.groceryecom.platform.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataTest {

    @Test
    void masksPasswordsButKeepsTheRestReadable() {
        String masked = SensitiveData.mask(
                "{\"username\":\"alice\",\"password\":\"hunter2\",\"email\":\"a@example.com\"}");

        assertThat(masked).doesNotContain("hunter2");
        assertThat(masked).contains("\"username\":\"alice\"", "\"email\":\"a@example.com\"", "\"password\":\"***\"");
    }

    @Test
    void masksEveryKnownSecretField() {
        String masked = SensitiveData.mask("""
                {"oldPassword":"a","newPassword":"b","confirmPassword":"c","refreshToken":"d",
                 "accessToken":"e","cardNumber":"4111111111111111","cvv":123,"otp":"999999"}""");

        assertThat(masked).doesNotContain("4111111111111111", "999999", "123");
        assertThat(masked).contains("\"cvv\":\"***\"", "\"cardNumber\":\"***\"");
    }

    @Test
    void isNotFooledByWhitespaceOrLetterCase() {
        assertThat(SensitiveData.mask("{\"Password\"  :   \"hunter2\"}")).doesNotContain("hunter2");
    }

    @Test
    void masksValuesContainingEscapedQuotes() {
        assertThat(SensitiveData.mask("{\"password\":\"he said \\\"hi\\\"\",\"username\":\"bob\"}"))
                .isEqualTo("{\"password\":\"***\",\"username\":\"bob\"}");
    }

    @Test
    void handlesTextThatIsNotJson() {
        assertThat(SensitiveData.mask("not json at all")).isEqualTo("not json at all");
        assertThat(SensitiveData.mask(null)).isEmpty();
        assertThat(SensitiveData.mask("")).isEmpty();
    }

    @Test
    void truncatesLongBodiesAndCollapsesWhitespace() {
        String body = "{\"note\":\"" + "x".repeat(500) + "\"}";

        String masked = SensitiveData.maskAndTruncate(body, 50);

        assertThat(masked).hasSize(50 + "...[truncated]".length());
        assertThat(masked).endsWith("...[truncated]");
    }
}
