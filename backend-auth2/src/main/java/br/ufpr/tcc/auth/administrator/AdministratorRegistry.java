package br.ufpr.tcc.auth.administrator;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Leitura e gravacao de administradores. */
@Component
class AdministratorRegistry {

    private final AdministratorRepository administrators;

    AdministratorRegistry(AdministratorRepository administrators) {
        this.administrators = administrators;
    }

    boolean noneRegistered() {
        return administrators.count() == 0L;
    }

    /**
     * Grava o administrador e devolve o identificador que o gerador atribuiu.
     *
     * A gravacao e descarregada na hora, e nao no fim da transacao, para que a recusa da
     * restricao unica do endereco aconteca aqui: adiada para o commit, ela apareceria depois
     * de a trilha de auditoria ja ter registrado uma criacao que nao aconteceu.
     */
    long insert(String name, String email, String passwordHash) {
        return administrators.saveAndFlush(Administrator.registered(name,
                withoutSurroundingSpace(email), passwordHash)).id();
    }

    record StoredAdministrator(long id, String name, String email, String passwordHash,
            boolean active) {
    }

    Optional<StoredAdministrator> findByEmail(String email) {
        return administrators.findByEmail(withoutSurroundingSpace(email))
                .map(administrator -> new StoredAdministrator(administrator.id(),
                        administrator.name(), administrator.email(),
                        administrator.passwordHash(), administrator.active()));
    }

    record AdministratorView(String id, String name, String email, boolean active) {
    }

    private static AdministratorView toView(Administrator administrator) {
        return new AdministratorView(Long.toString(administrator.id()), administrator.name(),
                administrator.email(), administrator.active());
    }

    List<AdministratorView> list() {
        return administrators.findByOrderByCreatedAtDesc().stream()
                .map(AdministratorRegistry::toView).toList();
    }

    Optional<AdministratorView> findById(long id) {
        return administrators.findById(id).map(AdministratorRegistry::toView);
    }

    boolean emailBelongsToAnother(String email, long id) {
        return findByEmail(email).filter(administrator -> administrator.id() != id).isPresent();
    }

    /**
     * A alteracao mexe na entidade carregada, e nao num comando proprio, porque aqui nao
     * existe coluna de escopo para manter no WHERE: a chave primaria sozinha ja identifica a
     * linha. Pelo caminho da entidade o transformador de coluna entra, e a forma minuscula do
     * endereco continua saindo do banco.
     *
     * A gravacao e descarregada na hora para que a recusa da restricao unica aconteca aqui, e
     * nao no commit, depois de a sessao ja ter sido revogada e a trilha ja ter registrado.
     */
    boolean update(long id, String name, String email, boolean active) {
        Optional<Administrator> stored = administrators.findById(id);
        stored.ifPresent(administrator -> {
            administrator.change(name, withoutSurroundingSpace(email), active);
            administrators.flush();
        });
        return stored.isPresent();
    }

    void updatePasswordHash(long id, String passwordHash) {
        administrators.findById(id)
                .ifPresent(administrator -> administrator.passwordHash(passwordHash));
    }

    boolean delete(long id) {
        return administrators.deleteByIdentifier(id) == 1;
    }

    long activeCountOtherThan(long id) {
        return administrators.countByActiveTrueAndIdNot(id);
    }

    /** A mesma contagem, depois de serializar as operacoes que mexem nela. */
    long activeCountOtherThanUnderLock(long id) {
        administrators.lockTheWholeTable();
        return activeCountOtherThan(id);
    }

    static Optional<Long> identifierOf(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
        }
    }

    /**
     * Apenas o espaco das pontas. Quem passa para minuscula e o banco, pela funcao LOWER no
     * parametro da consulta e pelo transformador de coluna da entidade, e isso nao e
     * preferencia de estilo.
     *
     * A coluna normalizada e conferida por uma restricao que compara com LOWER(EMAIL), e foi
     * medido aqui: o I maiusculo com ponto, U+0130, desce em Java para duas unidades de
     * codigo e em Oracle para outra coisa, entao calcular a minuscula nos dois lugares fazia
     * a restricao reprovar a gravacao e a rota responder erro de servidor onde o contrato
     * promete cadastro ou recusa. Com a minuscula saindo so do banco, os dois lados nao tem
     * como divergir.
     *
     * O corte de espaco alcanca apenas o que esta ate U+0020, e isto tambem foi medido neste
     * repositorio: o espaco nao quebravel U+00A0 e a largura zero U+200B sobrevivem tanto a
     * trim quanto a strip. Entao esta funcao deixa o endereco gravavel, e nao limpo.
     *
     * Nada disto alcanca senha. Senha nao passa por normalizacao nenhuma, porque cortar
     * espaco trocaria a credencial em silencio.
     */
    static String withoutSurroundingSpace(String email) {
        return email == null ? "" : email.trim();
    }
}
