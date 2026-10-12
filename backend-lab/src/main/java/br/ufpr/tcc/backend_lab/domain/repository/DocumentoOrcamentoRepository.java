package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoOrcamentoRepository
        extends JpaRepository<DocumentoOrcamento, Long> {}
