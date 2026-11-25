export type TourRegion = 'menu' | 'page' | 'dialog';

export type TourSide = 'top' | 'right' | 'bottom' | 'left';

export interface TourScene {
  readonly available?: () => boolean;
  readonly enter: () => void | Promise<void>;
  readonly leave: () => void;
}

export interface TourStep {
  readonly id: string;
  readonly target: string;
  readonly title: string;
  readonly text: string;
  readonly region?: TourRegion;
  readonly side?: TourSide;
  readonly scene?: TourScene;
}

export interface TourDefinition {
  readonly id: string;
  readonly label: string;
  readonly steps: readonly TourStep[];
}

export type TourFactory = () => TourDefinition;

export interface TourHost {
  readonly isHandset: () => boolean;
  readonly openMenu: () => Promise<void>;
  readonly closeMenu: () => Promise<void>;
}

export const TOUR_ATTRIBUTE = 'data-tour';

export function tourSelector(target: string): string {
  return `[${TOUR_ATTRIBUTE}="${target}"]`;
}

export function regionOf(step: TourStep): TourRegion {
  return step.region ?? 'page';
}
