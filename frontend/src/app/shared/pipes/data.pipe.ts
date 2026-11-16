import { Pipe, type PipeTransform } from '@angular/core';

const SO_A_DATA = /^\d{4}-\d{2}-\d{2}$/;
const EM_UTC = new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' });
const NO_FUSO_DO_LEITOR = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

@Pipe({ name: 'data', standalone: true })
export class DataPipe implements PipeTransform {
  transform(valor: string | Date | null | undefined): string {
    if (valor === null || valor === undefined || valor === '') return '';
    const momento = valor instanceof Date ? valor : new Date(valor);
    if (Number.isNaN(momento.getTime())) return String(valor);
    return typeof valor === 'string' && SO_A_DATA.test(valor)
      ? EM_UTC.format(momento)
      : NO_FUSO_DO_LEITOR.format(momento);
  }
}
