import { TestBed } from '@angular/core/testing';

import { ClassificationBadgeComponent } from './classification-badge.component';

// A cor da marca chega por nome de classe utilitária, não por valor de estilo.
// O teste fixa as duas garantias que isso depende: o Angular mescla a classe
// estática do gabarito com a vinda do binding, e o mapa cobre as três
// classificações.
describe('ClassificationBadgeComponent', () => {
  function classesDaMarca(classificacao: 'normal' | 'alerta' | 'nao-conforme'): string[] {
    const fixture = TestBed.createComponent(ClassificationBadgeComponent);
    fixture.componentRef.setInput('classificacao', classificacao);
    fixture.detectChanges();
    const marca: HTMLElement = fixture.nativeElement.querySelector('span span');
    return Array.from(marca.classList);
  }

  it('mescla a classe de cor com as classes estáticas da marca', () => {
    const classes = classesDaMarca('normal');
    expect(classes).toContain('bg-lactec-success');
    expect(classes).toContain('rounded-full');
    expect(classes).toContain('inline-block');
  });

  it('dá uma classe de cor própria a cada classificação', () => {
    expect(classesDaMarca('alerta')).toContain('bg-lactec-warning');
    expect(classesDaMarca('nao-conforme')).toContain('bg-lactec-danger');
  });
});
