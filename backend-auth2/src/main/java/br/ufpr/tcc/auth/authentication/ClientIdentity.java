package br.ufpr.tcc.auth.authentication;

import java.util.List;

/**
 * O que a resposta de login devolve sobre quem entrou.
 *
 * Os campos e os valores sao contrato medido no consumidor: ele exige o perfil exatamente
 * como CLIENTE, o codigo da aplicacao igual ao que ele tem configurado, e cada CNPJ so com
 * digito e letra maiuscula. Resposta fora disso vira falha de infraestrutura na tela, e
 * nao erro de credencial.
 *
 * O identificador e texto porque numero de 64 bits perde precisao no navegador.
 */
public record ClientIdentity(String id, String name, String email, String role,
        String serviceCode, List<String> companyCnpjs) {

    static final String CLIENT_ROLE = "CLIENTE";
}
