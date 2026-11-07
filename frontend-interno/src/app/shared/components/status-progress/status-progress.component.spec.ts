import { BreakpointObserver } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { StatusProgressComponent, StatusProgressStep } from './status-progress.component';

const ETAPAS: readonly StatusProgressStep[] = [
  { key: 'execucao', label: 'Em execução', icon: 'science' },
  { key: 'completo', label: 'Completo', icon: 'task_alt' },
  { key: 'validado', label: 'Validado', icon: 'autorenew' },
  { key: 'finalizado', label: 'Finalizado', icon: 'task_alt' },
  { key: 'rascunho', label: 'Elaborando relatório', icon: 'description' },
  { key: 'publicado', label: 'Relatório publicado', icon: 'cloud_done' },
];

function montarStepper(indiceAtual: number | null, celular: boolean) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [StatusProgressComponent],
    providers: [
      { provide: BreakpointObserver, useValue: { observe: () => of({ matches: celular }) } },
    ],
  });

  const fixture = TestBed.createComponent(StatusProgressComponent);
  fixture.componentRef.setInput('steps', ETAPAS);
  fixture.componentRef.setInput('currentIndex', indiceAtual);
  fixture.detectChanges();

  return fixture;
}

describe('Progresso de etapas compartilhado', () => {
  it('mostra cinco etapas em torno do status atual no desktop', () => {
    const fixture = montarStepper(2, false);

    expect(fixture.componentInstance.visibleSteps().map((etapa) => etapa.key)).toEqual([
      'execucao', 'completo', 'validado', 'finalizado', 'rascunho',
    ]);
    expect(fixture.nativeElement.querySelectorAll('[data-state="active"]').length).toBe(1);
  });

  it('mostra três etapas em torno do status atual em telas estreitas', () => {
    const fixture = montarStepper(3, true);

    expect(fixture.componentInstance.visibleSteps().map((etapa) => etapa.key)).toEqual([
      'validado', 'finalizado', 'rascunho',
    ]);
    expect(fixture.nativeElement.querySelectorAll('[data-state="active"]').length).toBe(1);
  });

  it('mantém todas as etapas neutras quando não há uma etapa atual', () => {
    const fixture = montarStepper(null, false);

    expect(fixture.nativeElement.querySelectorAll('[data-state="active"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('[data-state="completed"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('[data-state="pending"]').length).toBe(5);
  });

  it('usa marcações de concluído sem alterar o ícone específico da etapa atual', () => {
    const fixture = montarStepper(2, false);
    const etapas = Array.from(
      fixture.nativeElement.querySelectorAll('[role="listitem"]'),
    ) as HTMLElement[];

    expect(etapas[0].querySelector('mat-icon')?.textContent?.trim()).toBe('check');
    expect(etapas[1].querySelector('mat-icon')?.textContent?.trim()).toBe('check');
    expect(etapas[2].querySelector('mat-icon')?.textContent?.trim()).toBe('autorenew');
    expect(etapas[3].querySelector('mat-icon')?.textContent?.trim()).toBe('task_alt');
  });
});
