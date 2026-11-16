import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RESIZE_DEBOUNCE_MS } from '@shared/viewport-rows';

import { DataTableActionsDirective } from './data-table-actions.directive';
import { DataTableCellDirective } from './data-table-cell.directive';
import { DataTableComponent } from './data-table.component';
import { unmeasuredQuery, type DataTableColumn, type DataTableQuery } from './data-table.model';

interface Transformer {
  readonly id: string;
  readonly serial: string;
  readonly tag: string;
  readonly voltage: string | null;
  readonly reports: number;
}

const ROW_HEIGHT = 40;
const PADDING_BELOW = 16;

const TRANSFORMERS: readonly Transformer[] = [
  { id: 'a', serial: '571289', tag: 'TR-00001', voltage: null, reports: 4 },
  { id: 'b', serial: '884120', tag: 'TR-00002', voltage: '230 kV', reports: 1 },
];

@Component({
  standalone: true,
  imports: [DataTableComponent, DataTableCellDirective, DataTableActionsDirective],
  template: `
    <div
      data-area-rolavel
      style="overflow: auto; box-sizing: border-box; padding: 0"
      [style.height.px]="areaHeight()"
      [style.padding-bottom.px]="paddingBelow"
    >
      <div style="height: 50px"></div>
      <app-data-table
        caption="Transformadores da empresa"
        paginationLabel="Paginação dos transformadores"
        emptyTitle="Nenhum transformador encontrado"
        emptyDescription="Revise a busca."
        loadingMessage="Carregando transformadores…"
        [columns]="columns"
        [rows]="rows()"
        [total]="total()"
        [loading]="loading()"
        [rowHeight]="rowHeight"
        [minimumRows]="minimumRows()"
        [(query)]="query"
      >
        <ng-template appDataTableCell="tag" [appDataTableCellOf]="rows()" let-row>
          <strong>{{ row.tag.toLowerCase() }}</strong>
        </ng-template>
        @if (withActions()) {
          <ng-template appDataTableActions [appDataTableActionsOf]="rows()" let-row>
            <button type="button" (click)="opened.set(row.id)">Abrir {{ row.serial }}</button>
          </ng-template>
        }
      </app-data-table>
    </div>
  `,
})
class TableHostComponent {
  readonly columns: readonly DataTableColumn<Transformer>[] = [
    { key: 'serial', header: 'Número de Série' },
    { key: 'tag', header: 'TAG' },
    { key: 'voltage', header: 'Tensão', value: (row) => row.voltage },
    { key: 'reports', header: 'Laudos', value: (row) => row.reports, align: 'end' },
  ];
  readonly rowHeight = ROW_HEIGHT;
  readonly paddingBelow = PADDING_BELOW;
  readonly areaHeight = signal(700);
  readonly minimumRows = signal(2);
  readonly rows = signal<readonly Transformer[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly withActions = signal(true);
  readonly opened = signal('');
  readonly query = signal<DataTableQuery>(unmeasuredQuery());
}

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

async function resizeTheWindow(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  window.dispatchEvent(new Event('resize'));
  await new Promise((resolve) => setTimeout(resolve, RESIZE_DEBOUNCE_MS + 40));
  await settle(fixture);
}

function rowsThatFit(element: HTMLElement, areaHeight: number): number {
  const root = element.querySelector<HTMLElement>('app-data-table > div')!;
  const reserved =
    element.querySelector('thead')!.getBoundingClientRect().height +
    element.querySelector('app-pagination')!.parentElement!.getBoundingClientRect().height +
    (root.offsetHeight - root.clientHeight);
  const rowHeight =
    element.querySelector('tbody tr[data-row]')?.getBoundingClientRect().height ?? ROW_HEIGHT;
  return Math.floor((areaHeight - 50 - PADDING_BELOW - Math.ceil(reserved)) / rowHeight);
}

function cells(element: HTMLElement, column: number): string[] {
  return Array.from(element.querySelectorAll('tbody tr[data-row]')).map(
    (row) => row.querySelectorAll('td')[column].textContent?.trim() ?? '',
  );
}

function pageSizeSelect(element: HTMLElement): HTMLSelectElement {
  return element.querySelector('app-pagination select') as HTMLSelectElement;
}

function choosePageSize(element: HTMLElement, value: string): void {
  const select = pageSizeSelect(element);
  select.value = value;
  select.dispatchEvent(new Event('change'));
}

async function mount(configure?: (host: TableHostComponent) => void) {
  const fixture = TestBed.createComponent(TableHostComponent);
  configure?.(fixture.componentInstance);
  await settle(fixture);
  return { fixture, host: fixture.componentInstance, element: fixture.nativeElement as HTMLElement };
}

async function mountLoaded(total = TRANSFORMERS.length) {
  const mounted = await mount();
  mounted.host.rows.set(TRANSFORMERS);
  mounted.host.total.set(total);
  mounted.host.loading.set(false);
  await settle(mounted.fixture);
  return mounted;
}

describe('DataTableComponent', () => {
  describe('quantas linhas pede', () => {
    it('só entrega o tamanho de página depois de medir, e pede o que cabe na área', async () => {
      const fixture = TestBed.createComponent(TableHostComponent);

      expect(fixture.componentInstance.query()).toEqual({ page: 1, pageSize: 0 });

      await settle(fixture);
      const expected = rowsThatFit(fixture.nativeElement, 700);

      expect(expected).toBeGreaterThan(5);
      expect(fixture.componentInstance.query()).toEqual({ page: 1, pageSize: expected });
    });

    it('com as linhas que pediu, a tabela enche a área sem fazê-la rolar', async () => {
      const { fixture, host, element } = await mount();
      const pageSize = host.query().pageSize;

      host.rows.set(
        Array.from({ length: pageSize }, (unused, index) => ({
          id: String(index),
          serial: `5712${index}`,
          tag: `TR-${index}`,
          voltage: null,
          reports: index,
        })),
      );
      host.total.set(500);
      host.loading.set(false);
      await settle(fixture);

      const area = element.querySelector<HTMLElement>('[data-area-rolavel]')!;
      const table = element.querySelector<HTMLElement>('app-data-table > div')!;
      const spaceLeft =
        area.getBoundingClientRect().bottom - PADDING_BELOW - table.getBoundingClientRect().bottom;

      expect(element.querySelectorAll('tbody tr[data-row]').length).toBe(pageSize);
      expect(area.scrollHeight).toBeLessThanOrEqual(area.clientHeight);
      expect(spaceLeft).toBeGreaterThanOrEqual(0);
      expect(spaceLeft).toBeLessThan(ROW_HEIGHT + 1);
    });

    it('fica no mínimo quando a tabela começa abaixo do que a área mostra', async () => {
      const { host } = await mount((component) => component.areaHeight.set(120));

      expect(host.query().pageSize).toBe(2);
    });

    it('mostra o tamanho automático no seletor como "Auto (N)"', async () => {
      const { host, element } = await mountLoaded();

      const options = Array.from(pageSizeSelect(element).options).map((option) =>
        option.textContent?.trim(),
      );

      expect(options).toEqual([`Auto (${host.query().pageSize})`, '10', '25', '50', '100']);
      expect(pageSizeSelect(element).value).toBe('auto');
    });

    it('remede quando a janela muda e mantém à vista a primeira linha da página', async () => {
      const { fixture, host, element } = await mountLoaded(500);
      const before = host.query().pageSize;
      host.query.set({ page: 3, pageSize: before });
      await settle(fixture);

      host.areaHeight.set(1100);
      await resizeTheWindow(fixture);
      const after = rowsThatFit(element, 1100);

      expect(after).toBeGreaterThan(before);
      expect(host.query()).toEqual({
        page: Math.floor((2 * before) / after) + 1,
        pageSize: after,
      });
    });

    it('a escolha do usuário desliga o automático, e "Auto" volta a seguir a área', async () => {
      const { fixture, host, element } = await mountLoaded(500);
      const automatic = host.query().pageSize;

      choosePageSize(element, '25');
      await settle(fixture);

      expect(host.query()).toEqual({ page: 1, pageSize: 25, chosenPageSize: 25 });

      host.areaHeight.set(1100);
      await resizeTheWindow(fixture);

      expect(host.query().pageSize).toBe(25);
      expect(pageSizeSelect(element).options[0].textContent?.trim()).toBe(
        `Auto (${rowsThatFit(element, 1100)})`,
      );

      choosePageSize(element, 'auto');
      await settle(fixture);

      expect(host.query().pageSize).toBe(rowsThatFit(element, 1100));
      expect(host.query().pageSize).not.toBe(automatic);
    });

    it('a escolha guardada na consulta sobrevive quando a tela recria a tabela', async () => {
      const { host, element } = await mount((created) => {
        created.query.set({ page: 2, pageSize: 25, chosenPageSize: 25 });
      });

      expect(host.query()).toEqual({ page: 2, pageSize: 25, chosenPageSize: 25 });
      expect(pageSizeSelect(element).value).toBe('25');
    });

    it('não devolve consulta nova quando o tamanho medido é o que já estava', async () => {
      const { fixture, host } = await mountLoaded();
      const measured = host.query();

      await resizeTheWindow(fixture);

      expect(host.query()).toBe(measured);
    });
  });

  describe('paginação', () => {
    it('mostra a faixa da página e leva a próxima página à consulta, sem trocar o tamanho', async () => {
      const { fixture, host, element } = await mountLoaded(500);
      const pageSize = host.query().pageSize;

      expect(element.querySelector('app-pagination')?.textContent).toContain(`1-${pageSize} de 500`);

      const next = Array.from(element.querySelectorAll('app-pagination button')).find((button) =>
        button.textContent?.includes('Próximo'),
      ) as HTMLButtonElement;
      next.click();
      await settle(fixture);

      expect(host.query()).toEqual({ page: 2, pageSize });
    });

    it('trava a paginação enquanto carrega', async () => {
      const { fixture, host, element } = await mountLoaded(500);

      host.loading.set(true);
      await settle(fixture);

      const controls = Array.from(
        element.querySelectorAll<HTMLButtonElement | HTMLSelectElement>(
          'app-pagination button, app-pagination select',
        ),
      );

      expect(controls.every((control) => control.disabled)).toBeTrue();
      expect(element.querySelector('table')?.getAttribute('aria-busy')).toBe('true');
    });
  });

  describe('conteúdo', () => {
    it('monta cabeçalho e células pelas colunas declaradas, com a legenda para leitor de tela', async () => {
      const { element } = await mountLoaded();

      const headers = Array.from(element.querySelectorAll('thead th')).map((header) =>
        header.textContent?.trim(),
      );

      expect(element.querySelector('caption')?.textContent).toBe('Transformadores da empresa');
      expect(headers).toEqual(['Número de Série', 'TAG', 'Tensão', 'Laudos', 'Ações']);
      expect(cells(element, 0)).toEqual(['571289', '884120']);
      expect(cells(element, 3)).toEqual(['4', '1']);
    });

    it('valor nulo de coluna vira "Dados não disponíveis"', async () => {
      const { element } = await mountLoaded();

      expect(cells(element, 2)).toEqual(['Dados não disponíveis', '230 kV']);
    });

    it('usa o template de célula da coluna que o declara', async () => {
      const { element } = await mountLoaded();

      expect(cells(element, 1)).toEqual(['tr-00001', 'tr-00002']);
      expect(element.querySelectorAll('tbody td strong').length).toBe(2);
    });

    it('entrega a linha ao template de ações', async () => {
      const { fixture, host, element } = await mountLoaded();

      const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('tbody button'));
      buttons[1].click();
      await settle(fixture);

      expect(buttons.map((button) => button.textContent?.trim())).toEqual([
        'Abrir 571289',
        'Abrir 884120',
      ]);
      expect(host.opened()).toBe('b');
    });

    it('sem template de ações não há coluna de ações', async () => {
      const { element } = await mount((component) => component.withActions.set(false));

      expect(element.querySelectorAll('thead th').length).toBe(4);
    });
  });

  describe('carregando e vazio', () => {
    it('anuncia o carregamento enquanto não há linha nenhuma', async () => {
      const { element } = await mount();

      expect(element.querySelector('tbody [role="status"]')?.textContent?.trim()).toBe(
        'Carregando transformadores…',
      );
      expect(element.querySelector('tbody td')?.getAttribute('colspan')).toBe('5');
    });

    it('mantém as linhas à vista enquanto busca a página seguinte', async () => {
      const { fixture, host, element } = await mountLoaded();

      host.loading.set(true);
      await settle(fixture);

      expect(cells(element, 0)).toEqual(['571289', '884120']);
      expect(element.textContent).not.toContain('Carregando transformadores…');
    });

    it('anuncia a lista vazia com o título e a descrição da tela', async () => {
      const { fixture, host, element } = await mount();

      host.loading.set(false);
      await settle(fixture);

      const empty = element.querySelector('tbody [role="status"]');

      expect(empty?.textContent).toContain('Nenhum transformador encontrado');
      expect(empty?.textContent).toContain('Revise a busca.');
      expect(element.querySelector('app-pagination')?.textContent).toContain('0 de 0');
    });
  });
});
