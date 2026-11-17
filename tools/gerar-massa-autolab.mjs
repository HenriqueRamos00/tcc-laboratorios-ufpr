import { createHash } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  ANALISES,
  CLIENTES,
  EQUIPAMENTOS,
  RELATORIOS,
  SITUA_PROCESS,
  TEXTOS_POR_TIPO,
} from './massa-autolab.mjs';

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SAIDA_JSON = resolve(RAIZ, 'backend-portal-web/PortalHexagonalApi/Portal.Tests/AutoLab/autolab-sample.json');

const GCRECORD_APAGADO = 1873465;
const DIAS_PROGRAMADOS_PARA_O_CLIENTE = 15;

function guid(chave) {
  const hex = createHash('sha1').update(`autolab-local:${chave}`).digest('hex').toUpperCase();

  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

function somarDias(isoLocal, dias) {
  const data = new Date(`${isoLocal}Z`);
  data.setUTCDate(data.getUTCDate() + dias);

  return data.toISOString().slice(0, 19);
}

function diasEntre(inicio, fim) {
  return Math.round((new Date(`${fim}Z`) - new Date(`${inicio}Z`)) / 86_400_000);
}

function pdfDeUmaPagina(linhas) {
  const escapar = (texto) => texto.replace(/[\\()]/g, (caractere) => `\\${caractere}`);
  const conteudo = [
    'BT /F1 14 Tf 72 770 Td',
    ...linhas.map((linha, indice) => `${indice === 0 ? '' : '0 -24 Td '}(${escapar(linha)}) Tj`),
    'ET',
  ].join('\n');

  const objetos = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
    `<< /Length ${Buffer.byteLength(conteudo, 'latin1')} >>\nstream\n${conteudo}\nendstream`,
  ];

  let documento = '%PDF-1.4\n';
  const posicoes = [];

  objetos.forEach((objeto, indice) => {
    posicoes.push(Buffer.byteLength(documento, 'latin1'));
    documento += `${indice + 1} 0 obj\n${objeto}\nendobj\n`;
  });

  const inicioDaTabela = Buffer.byteLength(documento, 'latin1');
  documento += `xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n`;
  documento += posicoes.map((posicao) => `${String(posicao).padStart(10, '0')} 00000 n \n`).join('');
  documento += `trailer\n<< /Size ${objetos.length + 1} /Root 1 0 R >>\nstartxref\n${inicioDaTabela}\n%%EOF\n`;

  return Buffer.from(documento, 'latin1');
}

function linhasDeDadosEnsaios() {
  return ANALISES.flatMap((analise) =>
    analise.resultados.map(([nome, resultado]) => ({
      Identificacao: analise.identificacao,
      ProtocoloLactec: analise.protocoloLactec,
      NumSerie: analise.numSerie,
      Name: nome,
      Resultado: resultado,
      EmpresaTrafo: analise.empresaTrafo,
      DataEmissao: analise.dataEmissao,
      DataFinalAnalise: analise.dataFinalAnalise,
    })),
  );
}

function analiseDoRelatorio(relatorio) {
  const analise = ANALISES.find((candidata) => candidata.identificacao === relatorio.identificacao);

  if (!analise) throw new Error(`Relatorio sem laudo em vwDadosEnsaios: ${relatorio.identificacao}`);

  return analise;
}

function emissoes() {
  const linhas = [];

  for (const relatorio of RELATORIOS) {
    const analise = analiseDoRelatorio(relatorio);
    const textos = TEXTOS_POR_TIPO[relatorio.tipo];

    for (const emissao of relatorio.emissoes) {
      const chave = `${relatorio.identificacao}:${emissao.revisao}`;
      const dataEmissao = somarDias(analise.dataEmissao, emissao.emitidoEmDias ?? 0);
      const dataProgramada = somarDias(analise.dataFinalAnalise, DIAS_PROGRAMADOS_PARA_O_CLIENTE);
      const arquivo = emissao.pdf
        ? {
            Oid: guid(`FileData:${chave}`),
            size: 0,
            FileName: `${relatorio.identificacao.replaceAll('/', '-')}_rev${emissao.revisao}.pdf`,
            Content: pdfDeUmaPagina([
              `Relatorio de ensaio ${relatorio.identificacao}`,
              `Revisao ${emissao.revisao}`,
              `Protocolo ${analise.protocoloLactec}`,
              'Documento sintetico do banco local de desenvolvimento',
            ]),
            OptimisticLockField: 0,
            GCRecord: null,
          }
        : null;

      if (arquivo) arquivo.size = arquivo.Content.length;

      linhas.push({
        relatorio,
        analise,
        emissao,
        arquivo,
        registro: {
          Oid: guid(`RelatorioEmissao:${chave}`),
          Identificacao: relatorio.identificacao,
          PdfAssinado: arquivo?.Oid ?? null,
          PdfLiberado: null,
          GCRecord: emissao.apagada ? GCRECORD_APAGADO : null,
          Revisao: emissao.revisao,
          RelatorioEnsaioSubstituto:
            emissao.substituidaPelaRevisao === undefined
              ? null
              : guid(`RelatorioEmissao:${relatorio.identificacao}:${emissao.substituidaPelaRevisao}`),
          DataEmissao: dataEmissao,
          DataPublicacao:
            emissao.publicadoEmDias === null ? null : somarDias(dataEmissao, emissao.publicadoEmDias),
          TituloRelatorio: relatorio.semTitulo ? null : textos.titulo,
          ObjetivoRelatorio: relatorio.semTitulo ? null : textos.objetivo,
          Conclusao: relatorio.semConclusao ? null : textos.conclusao,
          Observacoes: null,
          Solicitacao: guid(`Solicitacao:${relatorio.numeroSolicitacao}`),
          OptimisticLockField: 0,
        },
        solicitacao: {
          Oid: guid(`RelatorioEmissao:${chave}`),
          NumeroSolicitacao: relatorio.numeroSolicitacao,
          OrcamentoCRM: relatorio.orcamentoCRM,
          IDAmostra: `AM-${analise.protocoloLactec}`,
          Nome: 'Óleos',
          ClienteSolicitante: analise.empresaTrafo,
          DataFinalProgramadaCliente: dataProgramada,
          SituacaoRelatorio: emissao.situacao,
          DiasIntervalo: diasEntre(dataEmissao, dataProgramada),
        },
      });
    }
  }

  return linhas;
}

function linhaDaView(emissao) {
  return {
    NumeroSolicitacao: emissao.solicitacao.NumeroSolicitacao,
    Oid: emissao.registro.Oid,
    OrcamentoCRM: emissao.solicitacao.OrcamentoCRM,
    IDAmostra: emissao.solicitacao.IDAmostra,
    Nome: emissao.solicitacao.Nome,
    PdfAssinado: emissao.registro.PdfAssinado,
    ClienteSolicitante: emissao.solicitacao.ClienteSolicitante,
    Identificacao: emissao.registro.Identificacao,
    DataFinalProgramadaCliente: emissao.solicitacao.DataFinalProgramadaCliente,
    SituacaoRelatorio: emissao.solicitacao.SituacaoRelatorio,
    DataEmissao: emissao.registro.DataEmissao,
    DataPublicacao: emissao.registro.DataPublicacao,
    DiasIntervalo: emissao.solicitacao.DiasIntervalo,
  };
}

const COLUNAS_DATA = new Set(['DataEmissao', 'DataFinalAnalise', 'DataPublicacao', 'DataFinalProgramadaCliente']);

function valorJson(coluna, valor) {
  if (valor === null || valor === undefined) return null;
  if (Buffer.isBuffer(valor)) return valor.toString('base64');
  if (COLUNAS_DATA.has(coluna)) return valor.replace('T', ' ');

  return valor;
}

function linhasJson(linhas) {
  return linhas.map((linha) =>
    Object.fromEntries(Object.entries(linha).map(([coluna, valor]) => [coluna, valorJson(coluna, valor)])),
  );
}

const dadosEnsaios = linhasDeDadosEnsaios();
const todasAsEmissoes = emissoes();
const registros = todasAsEmissoes.map((emissao) => emissao.registro);
const arquivos = todasAsEmissoes.map((emissao) => emissao.arquivo).filter(Boolean);
const viewDeRelatorios = todasAsEmissoes.filter((emissao) => emissao.registro.GCRecord === null).map(linhaDaView);
const clientes = CLIENTES.map((cliente) => ({ Oid: guid(`Cliente:${cliente.Sigla}`), ...cliente }));

const json = {
  'dbo.vwDadosEnsaios': linhasJson(dadosEnsaios),
  'dbo.Equipamentos': linhasJson(EQUIPAMENTOS),
  'dbo.SituaProcess': linhasJson(SITUA_PROCESS),
  'dbo.FileData': linhasJson(arquivos),
  'dbo.RelatorioEmissao': linhasJson(registros),
  'dbo.vwRelatoriosEnsaios': linhasJson(viewDeRelatorios),
  'dbo.dCliente': linhasJson(clientes),
};

writeFileSync(SAIDA_JSON, `${JSON.stringify(json, null, 2)}\n`, 'utf8');

console.log(`${SAIDA_JSON}: ${dadosEnsaios.length} linhas de ensaio, ${registros.length} emissoes, ${arquivos.length} PDFs`);
