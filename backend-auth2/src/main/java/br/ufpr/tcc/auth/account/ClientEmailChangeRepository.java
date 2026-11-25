package br.ufpr.tcc.auth.account;

import org.springframework.data.jpa.repository.JpaRepository;

interface ClientEmailChangeRepository extends JpaRepository<ClientEmailChange, Long> {
}
