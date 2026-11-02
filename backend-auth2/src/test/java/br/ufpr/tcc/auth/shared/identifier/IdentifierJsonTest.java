package br.ufpr.tcc.auth.shared.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IdentifierJsonTest {

    // O menor inteiro que o JavaScript nao representa: ele arredonda para o
    // anterior e o ultimo digito se perde.
    private static final long FIRST_UNSAFE_IN_JAVASCRIPT = 9_007_199_254_740_993L;

    private final JsonMapper mapper = JsonMapper.builder().build();

    record AccountResponse(@IdentifierAsString Long id, String name) {}

    @Test
    @DisplayName("o identificador sai do JSON entre aspas")
    void identifierIsWrittenAsJsonString() {
        String json = mapper.writeValueAsString(new AccountResponse(902317136557404180L, "Alfa"));

        assertThat(json).contains("\"id\":\"902317136557404180\"");
    }

    @Test
    @DisplayName("o identificador volta do JSON com o mesmo valor")
    void identifierSurvivesRoundTrip() {
        AccountResponse original = new AccountResponse(902317136557404180L, "Alfa");

        AccountResponse readBack =
                mapper.readValue(mapper.writeValueAsString(original), AccountResponse.class);

        assertThat(readBack).isEqualTo(original);
    }

    @Test
    @DisplayName("valor acima do inteiro seguro do JavaScript mantem todos os digitos")
    void identifierAboveJavaScriptSafeIntegerKeepsEveryDigit() {
        String json = mapper.writeValueAsString(
                new AccountResponse(FIRST_UNSAFE_IN_JAVASCRIPT, "Alfa"));

        assertThat(json).contains("\"9007199254740993\"");
        assertThat(mapper.readValue(json, AccountResponse.class).id())
                .isEqualTo(FIRST_UNSAFE_IN_JAVASCRIPT);
    }
}
