import { TestBed } from '@angular/core/testing';

import { STATUS_PILL_CLASS } from '@shared/components/status-pill/status-pill.component';

import type { EquipmentOverallStatus, EquipmentStatus } from '@/app/model/equipment';

import { EquipmentStatusComponent } from './equipment-status.component';

const RESERVED = ['h-6', 'w-40', 'shrink-0'];

function situacao(overall: EquipmentOverallStatus): EquipmentStatus {
  return {
    overall,
    chromatography: null,
    physicalChemical: null,
    lastReportDate: overall === 'sem-laudo' ? null : '2026-09-24',
    updatedAt: '2026-11-10T09:00:00Z',
  };
}

function montar(entradas: Record<string, unknown>) {
  const fixture = TestBed.createComponent(EquipmentStatusComponent);
  fixture.componentRef.setInput('subject', 'TR-00001');
  for (const [nome, valor] of Object.entries(entradas)) {
    fixture.componentRef.setInput(nome, valor);
  }
  fixture.detectChanges();
  return fixture;
}

function textoDe(elemento: Element | null | undefined): string {
  return elemento?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

describe('EquipmentStatusComponent', () => {
  it('tem dado: mostra a pílula da classificação, preenchida, e nenhum botão', () => {
    const raiz: HTMLElement = montar({ status: situacao('alerta') }).nativeElement;
    const pilula = raiz.querySelector('app-status-pill > span');

    expect(textoDe(pilula)).toBe('Alerta');
    expect(pilula?.classList).toContain('bg-lactec-tarja-alerta');
    expect(raiz.querySelector('button')).toBeNull();
  });

  it('não tem dado: mostra o botão Atualizar em pílula no lugar do rótulo', () => {
    const raiz: HTMLElement = montar({ status: null }).nativeElement;
    const botao = raiz.querySelector('button');

    expect(textoDe(botao)).toBe('sync Atualizar');
    expect(botao?.getAttribute('aria-label')).toBe('Atualizar a situação de TR-00001');
    expect(raiz.querySelector('app-status-pill')).toBeNull();
  });

  it('o botão leva exatamente as classes da pílula do rótulo', () => {
    const comDado: HTMLElement = montar({ status: situacao('normal') }).nativeElement;
    const semDado: HTMLElement = montar({ status: null }).nativeElement;
    const rotulo = comDado.querySelector('app-status-pill > span');
    const botao = semDado.querySelector('button');

    for (const classe of STATUS_PILL_CLASS.split(' ')) {
      expect(rotulo?.classList).toContain(classe);
      expect(botao?.classList).toContain(classe);
    }
  });

  it('reserva a mesma largura e a mesma altura em todas as variantes', () => {
    const variantes: Record<string, unknown>[] = [
      { status: situacao('normal') },
      { status: situacao('alerta') },
      { status: situacao('nao-conforme') },
      { status: situacao('sem-laudo') },
      { status: situacao('sem-classificacao') },
      { status: null },
      { status: null, refreshing: true },
      { status: null, failed: true },
      { status: null, refreshable: false },
    ];

    for (const entradas of variantes) {
      const raiz: HTMLElement = montar(entradas).nativeElement;
      const pilula = raiz.querySelector('app-status-pill > span, button');

      for (const classe of RESERVED) {
        expect(raiz.classList).withContext(JSON.stringify(entradas)).toContain(classe);
        if (pilula) expect(pilula.classList).withContext(JSON.stringify(entradas)).toContain(classe);
      }
      expect(raiz.children.length).withContext(JSON.stringify(entradas)).toBeGreaterThan(0);
      expect(raiz.querySelector('[role="alert"]:not(.sr-only)')).toBeNull();
    }
  });

  it('avisa o pedido de atualização uma vez por clique', () => {
    const fixture = montar({ status: null });
    let pedidos = 0;
    fixture.componentInstance.refresh.subscribe(() => pedidos++);

    (fixture.nativeElement as HTMLElement).querySelector('button')?.click();

    expect(pedidos).toBe(1);
  });

  it('enquanto atualiza, o botão gira o ícone, diz que está atualizando e não aceita outro clique', () => {
    const fixture = montar({ status: null, refreshing: true });
    const botao = (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;
    let pedidos = 0;
    fixture.componentInstance.refresh.subscribe(() => pedidos++);

    botao.click();

    expect(textoDe(botao)).toBe('sync Atualizando');
    expect(botao.querySelector('mat-icon')?.classList).toContain('animate-spin');
    expect(botao.disabled).toBeTrue();
    expect(botao.getAttribute('aria-busy')).toBe('true');
    expect(pedidos).toBe(0);
  });

  it('ao chegar o dado, troca o botão pelo rótulo', () => {
    const fixture = montar({ status: null, refreshing: true });
    const raiz: HTMLElement = fixture.nativeElement;

    fixture.componentRef.setInput('status', situacao('nao-conforme'));
    fixture.componentRef.setInput('refreshing', false);
    fixture.detectChanges();

    expect(raiz.querySelector('button')).toBeNull();
    expect(textoDe(raiz.querySelector('app-status-pill'))).toBe('Não conforme');
    expect(raiz.querySelector('.bg-lactec-tarja-nao-conforme')).not.toBeNull();
  });

  it('equipamento sem laudo e laudo sem classificação saem por extenso, em pílula neutra', () => {
    const semLaudo: HTMLElement = montar({ status: situacao('sem-laudo') }).nativeElement;
    const semCor: HTMLElement = montar({ status: situacao('sem-classificacao') }).nativeElement;

    expect(textoDe(semLaudo.querySelector('app-status-pill'))).toBe('Sem laudo');
    expect(textoDe(semCor.querySelector('app-status-pill'))).toBe('Sem classificação');
    expect(semLaudo.querySelector('.bg-lactec-muted')).not.toBeNull();
    expect(semLaudo.querySelector('button')).toBeNull();
    expect(semLaudo.querySelector('[class*="bg-lactec-tarja"]')).toBeNull();
  });

  it('diz que a atualização falhou sem ocupar outra linha, e o botão continua disponível', () => {
    const raiz: HTMLElement = montar({ status: null, failed: true }).nativeElement;
    const aviso = raiz.querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Não foi possível atualizar a situação de TR-00001');
    expect(aviso?.classList).toContain('sr-only');
    expect(textoDe(raiz.querySelector('button'))).toBe('sync Tentar de novo');
    expect(raiz.querySelector('button')?.disabled).toBeFalse();
  });

  it('onde o ambiente não oferece a atualização, não mostra botão', () => {
    const raiz: HTMLElement = montar({ status: null, refreshable: false }).nativeElement;

    expect(raiz.querySelector('button')).toBeNull();
    expect(raiz.querySelector('app-data-unavailable')?.textContent).toContain('Dados não disponíveis');
  });
});
