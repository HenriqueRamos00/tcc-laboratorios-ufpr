package br.ufpr.tcc.backend_lab.application.dto;

import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos.AmostraRef;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos.EmpresaRef;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos.Ensaio;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos.OrcamentoRef;

public record RelatorioSnapshot(EmpresaRef company, OrcamentoRef quote, AmostraRef sample,
        String type, String analysisType, Ensaio test) { }
