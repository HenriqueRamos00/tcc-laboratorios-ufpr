import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';

import { ClassificationBadgeComponent } from '@shared/components/classification-badge/classification-badge.component';
import { ConformityGaugeComponent } from '@shared/components/conformity-gauge/conformity-gauge.component';
import { DuvalPentagonComponent } from '@shared/components/duval-pentagon/duval-pentagon.component';
import { DuvalTriangleComponent } from '@shared/components/duval-triangle/duval-triangle.component';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import {
  LineChartComponent,
  type SerieDoGrafico,
} from '@shared/components/line-chart/line-chart.component';
import {
  PageHeaderComponent,
  type MigalhaDePao,
} from '@shared/components/page-header/page-header.component';
import { TarjaDeConformidadeComponent } from '@shared/components/tarja-de-conformidade/tarja-de-conformidade.component';

import type {
  GasDissolvido,
  IndicadoresDeSaude,
  ResultadoDeRogers,
  ValorMedido,
} from '@/app/model/health';
import {
  ABA_PADRAO,
  abaConhecida,
  type AbaDeIndicadores,
} from '@/app/model/health-navigation';
import { carregando, erro, ok, vazio, type RemoteData } from '@/app/model/remote-data';
import { EquipmentsService } from '@/app/services/equipments.service';
import { HealthService } from '@/app/services/health.service';

type SubAbaDeGases = 'criterios' | 'metodos';

// A data chega no formato do AutoLAB (2025-10-26) e a migalha de pão mostra a
// análise como o cliente a lê na tabela do histórico.
const DATA_BRASILEIRA = new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' });

function dataLegivel(iso: string): string {
  return DATA_BRASILEIRA.format(new Date(iso));
}

const NOME_DO_ENSAIO: Record<string, string> = {
  neutralizacao: 'Índice Neutralização',
  agua: 'Teor de Água',
  densidade: 'Densidade a 20ºC',
  fatorDePotencia: 'Fator de Potência',
  rigidez: 'Rigidez Dielétrica',
  tensaoInterfacial: 'Tensão Interfacial',
  cor: 'Cor',
};

const NOME_DO_GAS: Record<string, string> = {
  h2: 'H₂',
  o2: 'O₂',
  n2: 'N₂',
  ch4: 'CH₄',
  co: 'CO',
  co2: 'CO₂',
  c2h4: 'C₂H₄',
  c2h6: 'C₂H₆',
  c2h2: 'C₂H₂',
};

// Toda cor desta tela chega como classe utilitária inteira, nunca como tom: o
// Tailwind só emite a utilitária que encontra escrita por extenso no fonte, e
// nome montado em tempo de execução não chega ao CSS gerado. Quando a mesma
// cor pinta propriedades diferentes, cada propriedade ganha a sua constante.
const CLASSE_DE_FUNDO_DE_ROGERS: Record<ResultadoDeRogers, string> = {
  termico: 'bg-lactec-diagnostico-termico',
  descarga: 'bg-lactec-diagnostico-descarga',
  indeterminado: 'bg-transparent',
};

const ROTULO_DE_ROGERS: Record<ResultadoDeRogers, string> = {
  termico: 'Térmico',
  descarga: 'Descarga',
  indeterminado: 'Sem indicação',
};

// Legenda e marca leem a mesma constante: cor repetida no gabarito faz a
// legenda mentir no dia em que a paleta mudar.
const LEGENDA_DE_ROGERS = (['termico', 'descarga'] as const).map((chave) => ({
  chave,
  rotulo: ROTULO_DE_ROGERS[chave],
  classeDeFundo: CLASSE_DE_FUNDO_DE_ROGERS[chave],
}));

const CLASSE_DE_PREENCHIMENTO_DO_MEDIDO = 'fill-lactec-valor-medido';
const CLASSE_DE_FUNDO_DO_MEDIDO = 'bg-lactec-valor-medido';
const CLASSE_DE_TRACO_DO_LIMITE = 'stroke-lactec-limite-da-norma';
const CLASSE_DE_FUNDO_DO_LIMITE = 'bg-lactec-limite-da-norma';

// Faixas do IEEE C57.104: a severidade é a própria mensagem, então as três
// reusam o trio de feedback do tema em vez de inventar matiz nova.
const CLASSES_DA_FAIXA_IEEE = {
  alarme: { traco: 'stroke-lactec-danger', rotulo: 'fill-lactec-danger' },
  atencao: { traco: 'stroke-lactec-warning', rotulo: 'fill-lactec-warning' },
  tolerada: { traco: 'stroke-lactec-success', rotulo: 'fill-lactec-success' },
} as const;

// Coleta não realizada não é zero nem "não detectado": é lacuna. O laudo
// impresso marca a lacuna com um hífen, e a legenda da tabela explica a marca
// uma vez, em vez de repetir a explicação em cada célula vazia.
const MARCA_DE_COLETA_NAO_REALIZADA = '-';

function textoDoValorMedido(valor: ValorMedido): string {
  if (valor === null) return MARCA_DE_COLETA_NAO_REALIZADA;
  if (valor === 'ND') return 'ND';
  return valor.toLocaleString('pt-BR');
}

const LEGENDA_DA_NBR = [
  {
    chave: 'medido',
    rotulo: 'Medido',
    classeDeFundo: CLASSE_DE_FUNDO_DO_MEDIDO,
    formato: 'bloco' as const,
  },
  {
    chave: 'limite',
    rotulo: 'Limite',
    classeDeFundo: CLASSE_DE_FUNDO_DO_LIMITE,
    formato: 'linha' as const,
  },
];

@Component({
  selector: 'app-indicadores',
  standalone: true,
  imports: [
    MatIconModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ConformityGaugeComponent,
    ClassificationBadgeComponent,
    LineChartComponent,
    DuvalTriangleComponent,
    DuvalPentagonComponent,
    TarjaDeConformidadeComponent,
  ],
  templateUrl: './indicadores.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IndicadoresComponent {
  readonly id = input.required<string>();
  /** Segmento de rota: qual análise do histórico está na tela. */
  readonly relatorioId = input<string | undefined>();
  /** Parâmetro de consulta: qual aba abrir. A URL é a única fonte de verdade. */
  readonly aba = input<string | undefined>();

  private readonly health = inject(HealthService);
  private readonly equipments = inject(EquipmentsService);
  private readonly router = inject(Router);
  private readonly rotaAtual = inject(ActivatedRoute);

  protected readonly legendaDeRogers = LEGENDA_DE_ROGERS;
  protected readonly legendaDaNbr = LEGENDA_DA_NBR;
  protected readonly classeDePreenchimentoDoMedido = CLASSE_DE_PREENCHIMENTO_DO_MEDIDO;
  protected readonly classeDeTracoDoLimite = CLASSE_DE_TRACO_DO_LIMITE;

  // Parâmetro ausente chega como undefined, nunca como o valor anterior, então
  // o padrão vive no derivado e não num signal paralelo que precisasse de
  // sincronia.
  protected readonly abaAtiva = computed<AbaDeIndicadores>(() => {
    const pedida = this.aba();
    return abaConhecida(pedida) ? pedida : ABA_PADRAO;
  });

  protected readonly subAba = signal<SubAbaDeGases>('criterios');

  private readonly consulta = computed(() => ({
    id: this.id(),
    relatorioId: this.relatorioId(),
  }));

  // O router reaproveita a instância entre /equipamentos/1 e /2 e ngOnInit não
  // roda de novo; o switchMap externo cancela a busca anterior. startWith e
  // catchError ficam no pipe INTERNO: assim cada troca de rota volta para
  // 'carregando' e uma falha derruba só aquela tentativa, não o fluxo que
  // escuta a rota.
  protected readonly estado = toSignal(
    toObservable(this.consulta).pipe(
      switchMap(({ id, relatorioId }) =>
        this.equipments.buscarPorId(id).pipe(
          switchMap((equipamento) =>
            this.health.indicadoresDoEquipamento(id, equipamento.tag, relatorioId),
          ),
          map((dados) =>
            dados.fisicoQuimico.ensaios.length || dados.gasesDissolvidos.gases.length
              ? ok(dados)
              : vazio,
          ),
          catchError(() => of(erro('A consulta ao AutoLAB falhou. Tente novamente em instantes.'))),
          startWith(carregando),
        ),
      ),
    ),
    { initialValue: carregando as RemoteData<IndicadoresDeSaude> },
  );

  // O @switch estreita `estado().tipo`, mas não estreita `estado().dados` para
  // o template. Este computed faz a ponte sem espalhar casts na view.
  protected readonly dados = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'ok' ? estado.dados : null;
  });

  protected readonly migalhas = computed<readonly MigalhaDePao[]>(() => {
    const dados = this.dados();
    if (!dados) return [];
    const detalhe = `/cliente/equipamentos/${dados.equipamentoId}`;
    const trilha: MigalhaDePao[] = [
      { rotulo: 'Listagem de Equipamentos', rota: '/cliente/equipamentos' },
      { rotulo: dados.tag, rota: detalhe },
      { rotulo: 'Indicadores de Saúde', rota: `${detalhe}/indicadores` },
    ];
    const analise = dados.analiseSelecionada;
    if (!analise) return trilha;
    return [...trilha, { rotulo: `${analise.rotulo} ${dataLegivel(analise.data)}` }];
  });

  protected readonly motivoDoErro = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'erro' ? estado.motivo : '';
  });

  protected readonly datasFisicoQuimico = computed(
    () => this.dados()?.fisicoQuimico.coletas.map((coleta) => coleta.data) ?? [],
  );

  protected readonly seriesFisicoQuimico = computed<readonly SerieDoGrafico[]>(() => {
    const bloco = this.dados()?.fisicoQuimico;
    if (!bloco) return [];
    const chaves = Object.keys(NOME_DO_ENSAIO);
    return chaves.map((chave, indice) => ({
      chave,
      nome: NOME_DO_ENSAIO[chave],
      posicaoNaPaleta: indice,
      valores: bloco.coletas.map((coleta) => coleta.valores[chave] ?? null),
    }));
  });

  protected readonly colunasFisicoQuimico = computed(
    () => this.dados()?.fisicoQuimico.colunas ?? [],
  );

  /**
   * As colunas anteriores à coleta atual. Com uma coleta só esta lista fica
   * vazia e o grupo HISTÓRICO some da tabela inteiro: `colspan="0"` é HTML
   * inválido e um grupo sem coluna não é cabeçalho de coisa alguma.
   */
  protected readonly colunasDeHistorico = computed(() =>
    this.colunasFisicoQuimico().filter((coluna) => !coluna.ehResultadoAtual),
  );

  protected readonly colunaDeResultado = computed(
    () => this.colunasFisicoQuimico().find((coluna) => coluna.ehResultadoAtual) ?? null,
  );

  /**
   * Casa cada valor com a sua coluna pela posição, e é a posição que manda: a
   * linha percorre as colunas, não a própria lista. Uma linha mais curta do
   * que as colunas vira lacuna no fim, e não um deslocamento que faria a
   * tabela mostrar o valor de 2021 debaixo da data de 2025.
   */
  protected readonly linhasFisicoQuimico = computed(() => {
    const colunas = this.colunasFisicoQuimico();
    const indiceDoResultado = colunas.findIndex((coluna) => coluna.ehResultadoAtual);
    return (this.dados()?.fisicoQuimico.ensaios ?? []).map((ensaio) => ({
      chave: ensaio.chave,
      nome: ensaio.nome,
      metodo: ensaio.metodo,
      limite: ensaio.limite,
      classificacao: ensaio.classificacao,
      historico: colunas
        .map((coluna, indice) => ({ coluna, valor: ensaio.valoresPorColeta[indice] ?? null }))
        .filter((celula) => !celula.coluna.ehResultadoAtual)
        .map((celula) => textoDoValorMedido(celula.valor)),
      resultado: textoDoValorMedido(ensaio.valoresPorColeta[indiceDoResultado] ?? null),
    }));
  });

  protected readonly datasDeGases = computed(
    () => this.dados()?.gasesDissolvidos.coletas.map((coleta) => coleta.data) ?? [],
  );

  protected readonly seriesDeGases = computed<readonly SerieDoGrafico[]>(() => {
    const bloco = this.dados()?.gasesDissolvidos;
    if (!bloco) return [];
    return Object.keys(NOME_DO_GAS).map((chave, indice) => ({
      chave,
      nome: NOME_DO_GAS[chave],
      posicaoNaPaleta: indice,
      valores: bloco.coletas.map((coleta) => coleta.valores[chave] ?? null),
    }));
  });

  /** Os totais fecham a tabela e não entram no gráfico de evolução. */
  protected readonly gasesMedidos = computed(() => this.gasesFormatados((gas) => !!gas.formula));

  protected readonly gasesTotalizadores = computed(() =>
    this.gasesFormatados((gas) => !gas.formula),
  );

  private gasesFormatados(escolher: (gas: GasDissolvido) => boolean) {
    return (this.dados()?.gasesDissolvidos.gases ?? []).filter(escolher).map((gas) => ({
      chave: gas.chave,
      nome: gas.nome,
      formula: gas.formula,
      resultados: gas.resultados.map(textoDoValorMedido),
    }));
  }

  protected readonly linhasDeRogers = computed(() => {
    const celulas = this.dados()?.diagnostico.rogers ?? [];
    const linhas = [...new Set(celulas.map((celula) => celula.linha))];
    const colunas = [...new Set(celulas.map((celula) => celula.coluna))];
    return {
      colunas,
      linhas: linhas.map((linha) => ({
        rotulo: linha,
        celulas: colunas.map((coluna) => {
          const encontrada = celulas.find((celula) => celula.linha === linha && celula.coluna === coluna);
          const resultado = encontrada?.resultado ?? 'indeterminado';
          return {
            coluna,
            resultado,
            classeDeFundo: CLASSE_DE_FUNDO_DE_ROGERS[resultado],
            rotulo: ROTULO_DE_ROGERS[resultado],
          };
        }),
      })),
    };
  });

  protected readonly caminhoIeee = computed(() => {
    const ieee = this.dados()?.diagnostico.ieee;
    if (!ieee?.serie.length) return '';
    const maximo = Math.max(...ieee.serie, ieee.alarme) * 1.1;
    const passo = 260 / (ieee.serie.length - 1);
    return ieee.serie
      .map((valor, i) => `${i === 0 ? 'M' : 'L'} ${i * passo} ${110 - (valor / maximo) * 100}`)
      .join(' ');
  });

  protected readonly linhasDeReferenciaIeee = computed(() => {
    const ieee = this.dados()?.diagnostico.ieee;
    if (!ieee) return [];
    const maximo = Math.max(...ieee.serie, ieee.alarme) * 1.1;
    return [
      {
        rotulo: 'ALARM',
        y: 110 - (ieee.alarme / maximo) * 100,
        classes: CLASSES_DA_FAIXA_IEEE.alarme,
      },
      {
        rotulo: 'CAUTION',
        y: 110 - (ieee.atencao / maximo) * 100,
        classes: CLASSES_DA_FAIXA_IEEE.atencao,
      },
      { rotulo: 'IN-TOL.', y: 108, classes: CLASSES_DA_FAIXA_IEEE.tolerada },
    ];
  });

  protected readonly barrasNbr = computed(() => {
    const leituras = this.dados()?.diagnostico.nbr7274 ?? [];
    if (!leituras.length) return [];
    const maximo = Math.max(...leituras.map((leitura) => Math.max(leitura.valor, leitura.limite))) * 1.1;
    return leituras.map((leitura, i) => ({
      ...leitura,
      x: 24 + i * 46,
      altura: (leitura.valor / maximo) * 96,
      yLimite: 108 - (leitura.limite / maximo) * 96,
    }));
  });

  // Trocar de aba troca a URL, para o link da linha do histórico poder apontar
  // direto para a aba certa. `replaceUrl` evita que cada clique de aba empilhe
  // uma entrada no botão Voltar.
  protected trocarAba(aba: AbaDeIndicadores): void {
    void this.router.navigate([], {
      relativeTo: this.rotaAtual,
      queryParams: { aba },
      replaceUrl: true,
    });
  }

  protected trocarSubAba(sub: SubAbaDeGases): void {
    this.subAba.set(sub);
  }
}
