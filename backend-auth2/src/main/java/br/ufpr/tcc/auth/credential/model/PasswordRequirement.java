package br.ufpr.tcc.auth.credential.model;

/**
 * Requisito de senha que o Lactec declarou nos itens 1 e 2 das regras de negocio. A
 * politica devolve os requisitos violados, e nao um booleano, para que a mensagem de erro
 * cite o que faltou sem o chamador avaliar a senha de novo.
 */
public enum PasswordRequirement {

    /**
     * Minimo de oito pontos de codigo. A contagem e em ponto de codigo e nao em char,
     * porque senha com emoji ocupa dois char por simbolo e passaria a valer metade do que
     * a pessoa digitou.
     */
    MINIMUM_LENGTH,

    /** Pelo menos uma letra maiuscula, na definicao do Unicode. */
    UPPERCASE_LETTER,

    /** Pelo menos uma letra minuscula, na definicao do Unicode. */
    LOWERCASE_LETTER,

    /** Pelo menos um digito, na definicao do Unicode. */
    DIGIT
}
