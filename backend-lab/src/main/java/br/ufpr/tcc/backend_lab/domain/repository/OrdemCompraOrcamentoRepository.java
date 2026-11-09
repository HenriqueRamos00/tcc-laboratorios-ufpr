package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.OrdemCompraOrcamento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdemCompraOrcamentoRepository extends JpaRepository<OrdemCompraOrcamento, Long> {}
