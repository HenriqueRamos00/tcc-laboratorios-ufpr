import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  NgZone,
  signal,
} from '@angular/core';

import { type Classificacao, FAIXAS_DE_CONFORMIDADE, ROTULO_DA_CLASSIFICACAO } from '@/app/model/health';

// É um resumo do laudo e não uma medição contínua: por isso o ponteiro aponta
// para o meio da faixa, e o veredito aparece escrito abaixo, porque cor sozinha
// nunca carrega a informação.
const RAIO = 62;
const CENTRO_X = 80;
const CENTRO_Y = 74;
const PROPORCAO_DO_RAIO_INTERNO = 0.52;
const PROPORCAO_DO_COMPRIMENTO_DO_PONTEIRO = 0.82;

// O arco nasce na ponta esquerda e morre na ponta direita. As faixas de
// FAIXAS_DE_CONFORMIDADE repartem esse intervalo em fatias iguais, de modo que
// acrescentar uma faixa ao modelo reparte o arco de novo sem tocar aqui.
const ANGULO_INICIAL = 180;
const ANGULO_FINAL = 0;

const CLASSE_DE_PREENCHIMENTO_DA_FAIXA: Record<Classificacao, string> = {
  normal: 'fill-lactec-success',
  alerta: 'fill-lactec-warning',
  'nao-conforme': 'fill-lactec-danger',
};

const CLASSE_DE_FUNDO_DA_FAIXA: Record<Classificacao, string> = {
  normal: 'bg-lactec-success',
  alerta: 'bg-lactec-warning',
  'nao-conforme': 'bg-lactec-danger',
};

const CLASSE_DE_TEXTO_DA_FAIXA: Record<Classificacao, string> = {
  normal: 'text-lactec-success',
  alerta: 'text-lactec-warning',
  'nao-conforme': 'text-lactec-danger',
};

interface Arco {
  readonly chave: Classificacao;
  readonly caminho: string;
  readonly classeDePreenchimento: string;
}

// 180° é a ponta esquerda do arco e 0° a direita, com o y invertido porque no
// SVG ele cresce para baixo.
function pontoNoArco(anguloEmGraus: number, raio: number): [number, number] {
  const radianos = (Math.PI * anguloEmGraus) / 180;
  return [CENTRO_X + raio * Math.cos(radianos), CENTRO_Y - raio * Math.sin(radianos)];
}

function larguraDeCadaFaixaEmGraus(): number {
  return (ANGULO_INICIAL - ANGULO_FINAL) / FAIXAS_DE_CONFORMIDADE.length;
}

function anguloOndeAFaixaComeca(indiceDaFaixa: number): number {
  return ANGULO_INICIAL - indiceDaFaixa * larguraDeCadaFaixaEmGraus();
}

function anguloOndeAFaixaTermina(indiceDaFaixa: number): number {
  return anguloOndeAFaixaComeca(indiceDaFaixa) - larguraDeCadaFaixaEmGraus();
}

function anguloDoMeioDaFaixa(indiceDaFaixa: number): number {
  return anguloOndeAFaixaComeca(indiceDaFaixa) - larguraDeCadaFaixaEmGraus() / 2;
}

// Quanto o ponteiro precisa girar, em graus no sentido horário, para sair da
// ponta esquerda do arco e parar no meio da faixa. O arco é medido no sentido
// anti-horário e o giro do SVG corre no horário, por isso a subtração inverte.
function giroDoPonteiroAteOMeioDaFaixa(indiceDaFaixa: number): number {
  return ANGULO_INICIAL - anguloDoMeioDaFaixa(indiceDaFaixa);
}

function caminhoDoArcoDaFaixa(indiceDaFaixa: number): string {
  const raioInterno = RAIO * PROPORCAO_DO_RAIO_INTERNO;
  const de = anguloOndeAFaixaComeca(indiceDaFaixa);
  const ate = anguloOndeAFaixaTermina(indiceDaFaixa);
  const [x1, y1] = pontoNoArco(de, RAIO);
  const [x2, y2] = pontoNoArco(ate, RAIO);
  const [x3, y3] = pontoNoArco(ate, raioInterno);
  const [x4, y4] = pontoNoArco(de, raioInterno);
  return (
    `M ${x1} ${y1} A ${RAIO} ${RAIO} 0 0 1 ${x2} ${y2} ` +
    `L ${x3} ${y3} A ${raioInterno} ${raioInterno} 0 0 0 ${x4} ${y4} Z`
  );
}

/**
 * O ponteiro é desenhado parado na ponta esquerda do arco e só gira por CSS.
 * `--giro-ate-a-faixa` é o destino, calculado aqui; `--recuo-da-entrada` é o
 * desvio que a folha de estilo aplica para segurar o ponteiro na largada, e
 * existe apenas enquanto o medidor está entrando e apenas para quem não pediu
 * movimento reduzido. Quem pediu redução nunca recebe o recuo, então vê o
 * ponteiro no destino já no primeiro quadro e nada é desfeito depois.
 */
function estiloDoGiroDoPonteiro(giroEmGraus: number): string {
  return [
    'transform-box: view-box',
    `transform-origin: ${CENTRO_X}px ${CENTRO_Y}px`,
    `--giro-ate-a-faixa: ${giroEmGraus}deg`,
    'transform: rotate(calc(var(--giro-ate-a-faixa) + var(--recuo-da-entrada, 0deg)))',
  ].join('; ');
}

// Folga suficiente para o primeiro quadro ir para a tela num navegador ativo,
// e curta o bastante para nao segurar o ponteiro de forma perceptivel.
const ESPERA_MAXIMA_PELA_LARGADA_EM_MS = 150;

@Component({
  selector: 'app-conformity-gauge',
  standalone: true,
  host: { class: 'block' },
  template: `
    <figure class="m-0 flex flex-col items-center gap-3">
      <figcaption class="text-lg text-lactec-ink-soft">{{ titulo() }}</figcaption>

      <div
        role="meter"
        [attr.aria-label]="titulo()"
        [attr.aria-valuemin]="primeiraPosicao"
        [attr.aria-valuemax]="ultimaPosicao"
        [attr.aria-valuenow]="posicaoDaFaixa()"
        [attr.aria-valuetext]="leituraPorExtenso()"
      >
        <svg viewBox="0 0 160 96" class="w-40 h-24" aria-hidden="true">
          @for (arco of arcos(); track arco.chave) {
            <path [attr.d]="arco.caminho" [class]="arco.classeDePreenchimento" />
          }
          <g
            class="motion-safe:transition-transform motion-safe:duration-700 motion-safe:ease-out motion-safe:data-[entrando]:[--recuo-da-entrada:calc(var(--giro-ate-a-faixa)*-1)]"
            [attr.data-entrando]="estaEntrando() ? '' : null"
            [attr.style]="estiloDoGiro()"
          >
            <line
              [attr.x1]="CENTRO_X"
              [attr.y1]="CENTRO_Y"
              [attr.x2]="pontaDoPonteiro.x"
              [attr.y2]="pontaDoPonteiro.y"
              class="stroke-lactec-ink"
              stroke-width="5"
              stroke-linecap="round"
            />
          </g>
          <circle [attr.cx]="CENTRO_X" [attr.cy]="CENTRO_Y" r="7" class="fill-lactec-ink" />
        </svg>
      </div>

      <p class="display m-0 text-xl font-semibold tracking-wide text-lactec-ink">
        {{ veredito() }}
      </p>

      <ul class="m-0 flex list-none flex-wrap justify-center gap-x-5 gap-y-1 p-0">
        @for (faixa of faixas; track faixa.chave) {
          <li class="flex flex-col items-center gap-1">
            <span
              class="flex items-center gap-2 text-sm font-medium"
              [class]="classeDeTextoDaFaixa(faixa.chave)"
            >
              <span
                class="inline-block h-3 w-3 rounded-full"
                [class]="classeDeFundoDaFaixa(faixa.chave)"
                aria-hidden="true"
              ></span>
              <span>{{ faixa.rotulo }}</span>
            </span>
            <span class="text-xs text-lactec-muted">{{ faixa.criterio }}</span>
          </li>
        }
      </ul>
    </figure>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConformityGaugeComponent {
  readonly classificacao = input.required<Classificacao>();
  readonly veredito = input.required<string>();
  readonly titulo = input('Conformidade');
  /** Desligar a entrada deixa o ponteiro no destino desde o primeiro quadro. */
  readonly animarNaEntrada = input(true);

  protected readonly faixas = FAIXAS_DE_CONFORMIDADE;
  protected readonly CENTRO_X = CENTRO_X;
  protected readonly CENTRO_Y = CENTRO_Y;
  protected readonly primeiraPosicao = 1;
  protected readonly ultimaPosicao = FAIXAS_DE_CONFORMIDADE.length;

  // O ponteiro nasce deitado na ponta esquerda do arco e todo o resto do
  // caminho é giro, por isso a ponta não depende da faixa.
  protected readonly pontaDoPonteiro = (() => {
    const [x, y] = pontoNoArco(ANGULO_INICIAL, RAIO * PROPORCAO_DO_COMPRIMENTO_DO_PONTEIRO);
    return { x, y };
  })();

  private readonly zonaDoAngular = inject(NgZone);

  private readonly chegouAoDestino = signal(false);

  protected readonly arcos = computed<readonly Arco[]>(() =>
    FAIXAS_DE_CONFORMIDADE.map((faixa, indice) => ({
      chave: faixa.chave,
      classeDePreenchimento: CLASSE_DE_PREENCHIMENTO_DA_FAIXA[faixa.chave],
      caminho: caminhoDoArcoDaFaixa(indice),
    })),
  );

  protected readonly posicaoDaFaixa = computed(() => this.indiceDaFaixa() + this.primeiraPosicao);

  protected readonly leituraPorExtenso = computed(
    () => `${this.veredito()}, faixa ${ROTULO_DA_CLASSIFICACAO[this.classificacao()]}`,
  );

  protected readonly estiloDoGiro = computed(() =>
    estiloDoGiroDoPonteiro(giroDoPonteiroAteOMeioDaFaixa(this.indiceDaFaixa())),
  );

  protected readonly estaEntrando = computed(() => this.animarNaEntrada() && !this.chegouAoDestino());

  private readonly indiceDaFaixa = computed(() =>
    Math.max(
      FAIXAS_DE_CONFORMIDADE.findIndex((faixa) => faixa.chave === this.classificacao()),
      0,
    ),
  );

  // afterNextRender roda no navegador depois que o Angular renderiza, mas ainda
  // ANTES de o navegador pintar. Soltar o ponteiro ali deixa os dois estados no
  // mesmo quadro, e sem um quadro pintado na largada a transição não tem de
  // onde partir: o ponteiro aparece pronto. Por isso a soltura espera dois
  // quadros de animação, que é o ponto em que a posição inicial já está na tela.
  //
  // A volta precisa da zona porque afterNextRender roda fora dela: o sinal
  // escrito num quadro posterior sujaria a view sem agendar ciclo nenhum, e o
  // ponteiro ficaria preso na largada para sempre.
  //
  // O temporizador existe porque aba oculta congela o requestAnimationFrame em
  // zero: sem ele o ponteiro ficaria na largada até alguém olhar para a aba, e
  // uma impressão ou captura feita antes disso mostraria o veredito errado.
  // Soltar duas vezes não faz mal, porque escrever o mesmo valor no sinal nao
  // dispara ciclo novo.
  constructor() {
    afterNextRender({
      write: () => {
        if (!this.animarNaEntrada()) {
          this.chegouAoDestino.set(true);
          return;
        }
        const soltar = () => this.zonaDoAngular.run(() => this.chegouAoDestino.set(true));
        requestAnimationFrame(() => requestAnimationFrame(soltar));
        setTimeout(soltar, ESPERA_MAXIMA_PELA_LARGADA_EM_MS);
      },
    });
  }

  protected classeDeFundoDaFaixa(chave: Classificacao): string {
    return CLASSE_DE_FUNDO_DA_FAIXA[chave];
  }

  protected classeDeTextoDaFaixa(chave: Classificacao): string {
    return CLASSE_DE_TEXTO_DA_FAIXA[chave];
  }
}
