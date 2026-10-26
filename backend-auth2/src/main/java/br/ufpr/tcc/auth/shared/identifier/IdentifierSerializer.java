package br.ufpr.tcc.auth.shared.identifier;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Escreve o identificador como string no JSON.
 *
 * Inteiro acima de dois elevado a 53 perde digito em JavaScript, e o TSID ja nasce cerca
 * de cem vezes acima desse limite. Medido: o consumidor .NET do Portal le este campo como
 * string e quebra se vier numero.
 */
public class IdentifierSerializer extends StdSerializer<Long> {

    public IdentifierSerializer() {
        super(Long.class);
    }

    @Override
    public void serialize(Long value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(Long.toString(value));
    }
}
