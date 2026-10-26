package br.ufpr.tcc.auth.shared.identifier;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Le o identificador que chegou como string no JSON.
 *
 * getString() tambem devolve o texto de um token numerico, entao corpo com numero continua
 * sendo aceito. Quem garante que a saida tem aspas e o teste que afirma sobre o texto cru
 * do JSON, nao este leitor.
 */
public class IdentifierDeserializer extends StdDeserializer<Long> {

    public IdentifierDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        return Long.valueOf(parser.getString());
    }
}
