import { Pipe, type PipeTransform } from '@angular/core';

@Pipe({ name: 'numero', standalone: true })
export class NumeroPipe implements PipeTransform {
  transform(valor: number | null | undefined, casasDecimais?: number): string {
    if (typeof valor !== 'number' || !Number.isFinite(valor)) return '';
    return casasDecimais === undefined
      ? valor.toLocaleString('pt-BR')
      : valor.toLocaleString('pt-BR', { maximumFractionDigits: casasDecimais });
  }
}
