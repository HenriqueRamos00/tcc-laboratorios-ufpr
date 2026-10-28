package br.ufpr.tcc.auth.shared.web;

/**
 * Corpo que passou do teto durante a leitura, e nao antes dela.
 *
 * Ela existe porque requisicao em pedacos nao declara tamanho: o teto so pode ser cobrado
 * enquanto os bytes chegam, e o lugar onde isso acontece e dentro da conversao do corpo, ja
 * fora do filtro. Como excecao nao verificada, ela atravessa o leitor de JSON, que embrulha
 * apenas falha de entrada e saida, e chega ao tratador que responde o mesmo 413 do caminho
 * com tamanho declarado.
 */
public class BodyAboveCeilingException extends RuntimeException {

    BodyAboveCeilingException(long maximumBodyBytes) {
        super("Corpo da requisicao passou de " + maximumBodyBytes + " bytes durante a leitura");
    }
}
