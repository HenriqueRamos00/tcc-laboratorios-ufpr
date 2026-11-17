package br.ufpr.tcc.auth.shared.security;

/**
 * Quem esta chamando quando a credencial e administrativa.
 *
 * Tipo proprio, e nao uma bandeira dentro do chamador de aplicacao, porque administrador
 * atravessa aplicacoes: dar a ele o mesmo tipo faria o escopo de aplicacao parecer
 * aplicavel, e escopo que nao se aplica costuma virar nulo circulando pelo codigo.
 */
public record AdministratorCaller(long administratorId, String name) {
}
