import { ApplicationRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { type Classificacao } from '@/app/model/health';

import { ConformityGaugeComponent } from './conformity-gauge.component';

// As asserções olham o DOM e nunca o estilo calculado: a folha do Tailwind não
// é servida ao navegador do Karma, então computar cor ou matriz de giro aqui
// mediria o ambiente de teste em vez do componente.
function doisQuadrosDeAnimacao(): Promise<void> {
  return new Promise((resolver) =>
    requestAnimationFrame(() => requestAnimationFrame(() => resolver())),
  );
}

describe('ConformityGaugeComponent', () => {
  let fixture: ComponentFixture<ConformityGaugeComponent>;

  function montar(classificacao: Classificacao, animarNaEntrada: boolean): HTMLElement {
    fixture = TestBed.createComponent(ConformityGaugeComponent);
    fixture.componentRef.setInput('classificacao', classificacao);
    fixture.componentRef.setInput('veredito', 'APROVADO');
    fixture.componentRef.setInput('animarNaEntrada', animarNaEntrada);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function grupoDoPonteiro(raiz: HTMLElement): SVGGElement {
    return raiz.querySelector('svg g') as SVGGElement;
  }

  function medidor(raiz: HTMLElement): HTMLElement {
    return raiz.querySelector('[role="meter"]') as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ConformityGaugeComponent] }).compileComponents();
  });

  it('expõe o valor pelo papel meter, com o veredito por extenso', () => {
    const alvo = medidor(montar('alerta', false));

    expect(alvo.getAttribute('aria-valuemin')).toBe('1');
    expect(alvo.getAttribute('aria-valuemax')).toBe('3');
    expect(alvo.getAttribute('aria-valuenow')).toBe('2');
    expect(alvo.getAttribute('aria-valuetext')).toBe('APROVADO, faixa Alerta');
    expect(alvo.getAttribute('aria-label')).toBe('Conformidade');
  });

  it('numera a faixa pela ordem do modelo', () => {
    expect(medidor(montar('normal', false)).getAttribute('aria-valuenow')).toBe('1');
    expect(medidor(montar('nao-conforme', false)).getAttribute('aria-valuenow')).toBe('3');
  });

  it('esconde o desenho do leitor de tela, porque quem fala é o meter', () => {
    const desenho = montar('normal', false).querySelector('svg') as SVGElement;

    expect(desenho.getAttribute('aria-hidden')).toBe('true');
    expect(desenho.getAttribute('role')).toBeNull();
    expect(desenho.getAttribute('aria-label')).toBeNull();
  });

  it('pinta tudo por classe do tema, sem hexadecimal', () => {
    const raiz = montar('normal', false);
    const arcos = Array.from(raiz.querySelectorAll('svg path')).map((arco) => arco.getAttribute('class'));

    expect(arcos).toEqual(['fill-lactec-success', 'fill-lactec-warning', 'fill-lactec-danger']);
    expect((raiz.querySelector('svg line') as SVGElement).getAttribute('class')).toBe('stroke-lactec-ink');
    expect((raiz.querySelector('svg circle') as SVGElement).getAttribute('class')).toBe('fill-lactec-ink');
    expect(raiz.innerHTML).not.toMatch(/#[0-9a-fA-F]{6}/);
  });

  it('deriva o giro do ponteiro da posição da faixa', () => {
    expect(grupoDoPonteiro(montar('normal', false)).getAttribute('style')).toContain(
      '--giro-ate-a-faixa: 30deg',
    );
    expect(grupoDoPonteiro(montar('alerta', false)).getAttribute('style')).toContain(
      '--giro-ate-a-faixa: 90deg',
    );
    expect(grupoDoPonteiro(montar('nao-conforme', false)).getAttribute('style')).toContain(
      '--giro-ate-a-faixa: 150deg',
    );
  });

  it('compõe o giro somando o destino ao recuo da entrada', () => {
    expect(grupoDoPonteiro(montar('normal', false)).getAttribute('style')).toContain(
      'transform: rotate(calc(var(--giro-ate-a-faixa) + var(--recuo-da-entrada, 0deg)))',
    );
  });

  it('só promete movimento a quem não pediu redução', () => {
    const classes = grupoDoPonteiro(montar('normal', true)).getAttribute('class') ?? '';

    expect(classes).toContain('motion-safe:transition-transform');
    expect(classes).toContain(
      'motion-safe:data-[entrando]:[--recuo-da-entrada:calc(var(--giro-ate-a-faixa)*-1)]',
    );
    expect(classes.split(/\s+/).every((nome) => nome === '' || nome.startsWith('motion-safe:'))).toBeTrue();
  });

  it('com a entrada desligada, o ponteiro já nasce na faixa', () => {
    expect(grupoDoPonteiro(montar('normal', false)).hasAttribute('data-entrando')).toBeFalse();
  });

  it('com a entrada ligada, o ponteiro larga segurado no início do arco', () => {
    expect(grupoDoPonteiro(montar('nao-conforme', true)).hasAttribute('data-entrando')).toBeTrue();
  });

  it('segura o ponteiro enquanto a largada ainda nao foi para a tela', () => {
    const raiz = montar('nao-conforme', true);
    const grupo = grupoDoPonteiro(raiz);

    TestBed.inject(ApplicationRef).tick();
    fixture.detectChanges();

    expect(grupo.hasAttribute('data-entrando')).toBeTrue();
  });

  it('solta o ponteiro para a faixa depois que a largada foi pintada', async () => {
    const raiz = montar('nao-conforme', true);
    const grupo = grupoDoPonteiro(raiz);

    TestBed.inject(ApplicationRef).tick();
    await doisQuadrosDeAnimacao();
    fixture.detectChanges();

    expect(grupo.hasAttribute('data-entrando')).toBeFalse();
  });

  it('não deixa nenhum atributo aria acompanhar a animação', async () => {
    const raiz = montar('nao-conforme', true);
    const antes = medidor(raiz).outerHTML.slice(0, medidor(raiz).outerHTML.indexOf('>'));

    TestBed.inject(ApplicationRef).tick();
    await doisQuadrosDeAnimacao();
    fixture.detectChanges();

    const depois = medidor(raiz).outerHTML.slice(0, medidor(raiz).outerHTML.indexOf('>'));
    expect(depois).toBe(antes);
    expect(medidor(raiz).getAttribute('aria-valuenow')).toBe('3');
  });
});
