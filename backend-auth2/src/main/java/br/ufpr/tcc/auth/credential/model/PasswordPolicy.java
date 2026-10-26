package br.ufpr.tcc.auth.credential.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Politica de senha de cadastro: minimo de oito pontos de codigo, com letra maiuscula,
 * letra minuscula e digito presentes. E funcao pura, sem Spring e sem banco, para poder
 * ser exercitada em teste de unidade e reusada pelo cadastro e pela troca de senha.
 *
 * O minimo de oito e regra de cadastro, nao de login. O consumidor declara apenas o teto
 * de 72 caracteres no login, e cobrar minimo ali responderia 400 onde o contrato espera
 * 401, o que entrega o tamanho da senha a quem sonda e ainda deixa a tentativa curta sem
 * gastar uma das cinco.
 *
 * A senha nunca e normalizada. Nao existe trim, nao existe mudanca de caixa e nao existe
 * remocao de acento em ponto nenhum desta classe. Cortar espaco troca a credencial em
 * silencio e quem cadastrou com espaco na ponta deixa de entrar; mudar a caixa apagaria a
 * diferenca entre dois requisitos que a propria politica cobra; remover acento faria duas
 * senhas diferentes virarem a mesma e encolheria o espaco de busca de quem ataca.
 *
 * Maiuscula, minuscula e digito seguem a definicao do Unicode, entao letra acentuada conta
 * como letra. Aceitar apenas de A a Z recusaria senha que a pessoa digita no teclado dela
 * sem ganhar forca nenhuma.
 */
public final class PasswordPolicy {

    private static final int MINIMUM_LENGTH_IN_CODE_POINTS = 8;

    private PasswordPolicy() {
    }

    /**
     * Devolve os requisitos que a senha deixa de cumprir, na ordem em que o enum os
     * declara, ou um conjunto vazio quando ela cumpre todos. O conjunto e somente leitura.
     *
     * Senha nula conta como senha que nao cumpre requisito nenhum, e nao como erro de
     * programacao: a politica fica fechada por padrao, e quem esquecer a validacao de
     * obrigatoriedade na borda recebe recusa com motivo em vez de excecao no meio do
     * cadastro.
     */
    public static Set<PasswordRequirement> violationsOf(String password) {
        if (password == null) {
            return Collections.unmodifiableSet(EnumSet.allOf(PasswordRequirement.class));
        }
        Set<PasswordRequirement> violations = EnumSet.noneOf(PasswordRequirement.class);
        if (password.codePointCount(0, password.length()) < MINIMUM_LENGTH_IN_CODE_POINTS) {
            violations.add(PasswordRequirement.MINIMUM_LENGTH);
        }
        if (password.codePoints().noneMatch(Character::isUpperCase)) {
            violations.add(PasswordRequirement.UPPERCASE_LETTER);
        }
        if (password.codePoints().noneMatch(Character::isLowerCase)) {
            violations.add(PasswordRequirement.LOWERCASE_LETTER);
        }
        if (password.codePoints().noneMatch(Character::isDigit)) {
            violations.add(PasswordRequirement.DIGIT);
        }
        return Collections.unmodifiableSet(violations);
    }
}
