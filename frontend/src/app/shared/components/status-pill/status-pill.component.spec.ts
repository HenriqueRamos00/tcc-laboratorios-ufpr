import { TestBed } from '@angular/core/testing';

import {
  STATUS_PILL_CLASS,
  StatusPillButtonComponent,
  StatusPillComponent,
  type StatusPillTone,
} from './status-pill.component';

const PILL = STATUS_PILL_CLASS.split(' ');

function rotulo(tone: StatusPillTone, label = 'Normal'): HTMLElement {
  const fixture = TestBed.createComponent(StatusPillComponent);
  fixture.componentRef.setInput('tone', tone);
  fixture.componentRef.setInput('label', label);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('span') as HTMLElement;
}

function montarBotao(entradas: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(StatusPillButtonComponent);
  fixture.componentRef.setInput('label', 'Atualizar');
  fixture.componentRef.setInput('loadingLabel', 'Atualizando');
  for (const [nome, valor] of Object.entries(entradas)) {
    fixture.componentRef.setInput(nome, valor);
  }
  fixture.detectChanges();
  return fixture;
}

function botaoDe(fixture: ReturnType<typeof montarBotao>): HTMLButtonElement {
  return (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;
}

describe('StatusPillComponent', () => {
  it('é uma pílula preenchida, de altura e largura fixas, com o texto dentro', () => {
    const pilula = rotulo('normal');

    expect(pilula.textContent?.trim()).toBe('Normal');
    for (const classe of ['inline-flex', 'h-6', 'w-40', 'rounded-full', 'px-2', 'text-xs', 'font-semibold']) {
      expect(pilula.classList).toContain(classe);
    }
  });

  it('cada tom pinta o fundo com a cor do estado e escreve com a tinta que tem contraste', () => {
    expect(rotulo('normal').classList).toContain('bg-lactec-tarja-normal');
    expect(rotulo('normal').classList).toContain('text-lactec-on-tarja-normal');
    expect(rotulo('alerta').classList).toContain('bg-lactec-tarja-alerta');
    expect(rotulo('alerta').classList).toContain('text-lactec-on-tarja-alerta');
    expect(rotulo('nao-conforme').classList).toContain('bg-lactec-tarja-nao-conforme');
    expect(rotulo('nao-conforme').classList).toContain('text-lactec-on-tarja-nao-conforme');
    expect(rotulo('neutro').classList).toContain('bg-lactec-muted');
    expect(rotulo('neutro').classList).toContain('text-white');
  });

  it('todas as variantes levam as mesmas classes de forma e de tamanho', () => {
    const tons: StatusPillTone[] = ['normal', 'alerta', 'nao-conforme', 'neutro'];
    const parado = botaoDe(montarBotao());
    const rodando = botaoDe(montarBotao({ loading: true }));

    for (const classe of PILL) {
      for (const tom of tons) expect(rotulo(tom).classList).toContain(classe);
      expect(parado.classList).toContain(classe);
      expect(rodando.classList).toContain(classe);
    }
  });
});

describe('StatusPillButtonComponent', () => {
  it('é um botão em pílula neutra, com o ícone sync antes do texto, sem sublinhado', () => {
    const botao = botaoDe(montarBotao({ description: 'Atualizar a situação de TR-00001' }));
    const icone = botao.querySelector('mat-icon');

    expect(botao.type).toBe('button');
    expect(botao.classList).toContain('bg-lactec-muted');
    expect(botao.classList).toContain('text-white');
    expect(botao.className).not.toContain('underline');
    expect(botao.className).toContain('focus-visible:outline-2');
    expect(icone?.textContent?.trim()).toBe('sync');
    expect(icone?.getAttribute('aria-hidden')).toBe('true');
    expect(icone?.classList).not.toContain('animate-spin');
    expect(botao.firstElementChild).toBe(icone);
    expect(botao.textContent?.replace(/\s+/g, ' ').trim()).toBe('sync Atualizar');
    expect(botao.getAttribute('aria-label')).toBe('Atualizar a situação de TR-00001');
    expect(botao.disabled).toBeFalse();
  });

  it('avisa uma vez por clique', () => {
    const fixture = montarBotao();
    let cliques = 0;
    fixture.componentInstance.pressed.subscribe(() => cliques++);

    botaoDe(fixture).click();

    expect(cliques).toBe(1);
  });

  it('enquanto atualiza, gira o ícone, diz Atualizando e não aceita clique', () => {
    const fixture = montarBotao({ loading: true, description: 'Atualizar a situação de TR-00001' });
    const botao = botaoDe(fixture);
    let cliques = 0;
    fixture.componentInstance.pressed.subscribe(() => cliques++);

    botao.click();

    expect(botao.querySelector('mat-icon')?.classList).toContain('animate-spin');
    expect(botao.textContent?.replace(/\s+/g, ' ').trim()).toBe('sync Atualizando');
    expect(botao.disabled).toBeTrue();
    expect(botao.getAttribute('aria-busy')).toBe('true');
    expect(botao.getAttribute('aria-label')).toBeNull();
    expect(cliques).toBe(0);
  });
});
