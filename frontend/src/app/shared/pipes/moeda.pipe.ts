import { Pipe, type PipeTransform } from '@angular/core';

const REAL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

@Pipe({ name: 'moeda', standalone: true })
export class MoedaPipe implements PipeTransform {
  transform(valor: number | null | undefined): string {
    return typeof valor === 'number' && Number.isFinite(valor) ? REAL.format(valor) : '';
  }
}
