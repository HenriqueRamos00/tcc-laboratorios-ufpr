import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PageHeaderSkeletonComponent } from '@shared/components/skeleton/page-header-skeleton.component';
import { SkeletonComponent } from '@shared/components/skeleton/skeleton.component';
import { skeletonIndexes } from '@shared/components/skeleton/table-skeleton.component';

export type HealthSkeletonLayout = 'fisico-quimico' | 'criterios' | 'metodos';

const RESULT_ROW_COUNT = 8;
const RESULT_COLUMN_COUNT = 5;
const LEGEND_ITEM_COUNT = 6;
const DIAGNOSTIC_FIGURE_COUNT = 9;
const CONCLUSION_COUNT = 4;

@Component({
  selector: 'app-health-indicators-skeleton',
  standalone: true,
  imports: [NgTemplateOutlet, PageHeaderSkeletonComponent, SkeletonComponent],
  host: { class: 'block' },
  template: `
    <app-page-header-skeleton [breadcrumbs]="true">
      <div class="flex flex-col items-end gap-1">
        <app-skeleton class="h-4 w-24" />
        <app-skeleton class="h-11 w-11" shape="block" />
      </div>
    </app-page-header-skeleton>

    <div class="mt-4 flex gap-8 border-b border-lactec-line px-4">
      <div class="flex h-11 items-center"><app-skeleton class="h-4 w-24" /></div>
      <div class="flex h-11 items-center"><app-skeleton class="h-4 w-32" /></div>
    </div>

    @if (layout() === 'fisico-quimico') {
      <div
        data-skeleton-panel="fisico-quimico"
        class="mt-6 rounded-lactec-panel border border-lactec-line bg-lactec-paper p-6"
      >
        <div class="grid grid-cols-1 items-center gap-6 lg:grid-cols-[minmax(0,20rem)_1fr]">
          <ng-container [ngTemplateOutlet]="gauge" />
          <ng-container [ngTemplateOutlet]="chart" />
        </div>
        <ng-container [ngTemplateOutlet]="results" />
      </div>
      <ng-container [ngTemplateOutlet]="report" />
    } @else {
      <div class="mt-6">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <app-skeleton class="h-11" shape="block" />
          <app-skeleton class="h-11" shape="block" />
        </div>

        @if (layout() === 'criterios') {
          <div
            data-skeleton-panel="criterios"
            class="mt-4 rounded-lactec-panel border border-lactec-line bg-lactec-paper p-6"
          >
            <ng-container [ngTemplateOutlet]="gauge" />
            <div class="mt-6"><ng-container [ngTemplateOutlet]="chart" /></div>
            <ng-container [ngTemplateOutlet]="results" />
          </div>
          <ng-container [ngTemplateOutlet]="report" />
        } @else {
          <div
            data-skeleton-panel="metodos"
            class="mt-4 rounded-lactec-panel border border-lactec-line bg-lactec-paper p-6"
          >
            <div class="flex h-7 items-center justify-center">
              <app-skeleton class="h-5 w-80 max-w-full" />
            </div>

            <div class="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              @for (figure of figures; track figure) {
                <div class="rounded-lg border border-lactec-line-soft p-4">
                  <app-skeleton class="mx-auto h-4 w-40" />
                  <app-skeleton class="mt-3 aspect-[4/3] w-full" shape="block" />
                </div>
              }
            </div>

            <div class="mt-6 overflow-hidden rounded-lg border border-lactec-line">
              <div class="flex h-11 items-center bg-lactec-line-soft px-4">
                <app-skeleton class="h-4 w-28" tone="strong" />
              </div>
              @for (conclusion of conclusions; track conclusion) {
                <div class="flex h-11 items-center border-t border-lactec-line-soft px-4">
                  <app-skeleton class="h-4 w-2/3" />
                </div>
              }
            </div>
          </div>
        }
      </div>
    }

    <ng-template #gauge>
      <div class="flex flex-col items-center gap-3">
        <app-skeleton class="my-1 h-5 w-48" />
        <div class="h-24 w-40 overflow-hidden">
          <app-skeleton class="h-40 w-40" shape="round" />
        </div>
        <app-skeleton class="my-0.5 h-6 w-32" />
        <div class="flex flex-wrap justify-center gap-x-5 gap-y-1">
          <app-skeleton class="h-9 w-20" />
          <app-skeleton class="h-9 w-20" />
          <app-skeleton class="h-9 w-20" />
        </div>
      </div>
    </ng-template>

    <ng-template #chart>
      <div>
        <app-skeleton class="mx-auto mt-0.5 mb-2.5 h-5 w-80 max-w-full" />
        <app-skeleton class="aspect-[720/320] w-full" shape="block" />
        <div class="mt-3 flex flex-wrap items-center justify-center gap-x-4 gap-y-1">
          @for (item of legendItems; track item) {
            <app-skeleton class="h-4 w-16" />
          }
        </div>
      </div>
    </ng-template>

    <ng-template #results>
      <div class="mt-6 overflow-hidden">
        <div class="flex h-20 items-center gap-8 bg-lactec-line-soft px-4">
          @for (column of resultColumns; track column) {
            <app-skeleton class="h-4 w-24" tone="strong" />
          }
        </div>
        @for (row of resultRows; track row) {
          <div class="flex h-11 items-center gap-8 border-t border-lactec-line-soft px-4">
            <app-skeleton class="h-4 w-40" />
            @for (column of resultColumns; track column) {
              @if (!$first) {
                <app-skeleton class="h-4 w-20" />
              }
            }
          </div>
        }
      </div>
    </ng-template>

    <ng-template #report>
      <div class="mt-4 rounded-lactec-panel border border-lactec-line bg-lactec-paper p-6">
        <app-skeleton class="my-0.5 h-5 w-72 max-w-full" />
        <app-skeleton class="mt-2.5 mb-0.5 h-4 w-full" />
        <app-skeleton class="mt-2.5 mb-0.5 h-4 w-5/6" />
      </div>
    </ng-template>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HealthIndicatorsSkeletonComponent {
  readonly layout = input.required<HealthSkeletonLayout>();

  protected readonly resultRows = skeletonIndexes(RESULT_ROW_COUNT);
  protected readonly resultColumns = skeletonIndexes(RESULT_COLUMN_COUNT);
  protected readonly legendItems = skeletonIndexes(LEGEND_ITEM_COUNT);
  protected readonly figures = skeletonIndexes(DIAGNOSTIC_FIGURE_COUNT);
  protected readonly conclusions = skeletonIndexes(CONCLUSION_COUNT);
}
