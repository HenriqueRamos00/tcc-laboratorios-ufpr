package br.ufpr.tcc.auth.authentication.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo do login de cliente.
 *
 * Nao existe minimo de senha aqui, e isso e contrato medido: o consumidor declara apenas o
 * teto de 72 caracteres no login. Cobrar o minimo de oito nesta rota responderia 400 onde
 * o contrato espera 401, o que entrega o tamanho da senha a quem sonda e ainda deixa a
 * tentativa curta sem gastar uma das cinco. O minimo e regra de cadastro.
 *
 * O campo de senha nao tem exemplo: a interface do Swagger preenche o formulario, e cada
 * clique gastaria uma das cinco tentativas da conta.
 */
record ClientLoginRequest(
        @NotBlank @Email @Size(max = 254)
        @Schema(example = "cliente@example.com", format = "email") String email,

        @NotBlank @Size(max = 72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Senha da conta, preservando espacos: o servidor nao remove espaco"
                        + " nas pontas, e a senha diferencia maiuscula de minuscula.")
        String password) {

    @Override
    public String toString() {
        return "ClientLoginRequest[email=" + email + ", password=[OMITIDA]]";
    }
}
