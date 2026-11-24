import { CanDeactivateFn, Routes } from '@angular/router';

import { authGuard, guestGuard } from '@core/guards/auth.guard';

import type { EditorComponent } from './pages/admin/directory/editor.component';
import { sections } from './pages/admin/directory/sections';

const leaveEditor: CanDeactivateFn<EditorComponent | null> = (editor) => editor?.canLeave() ?? true;

const managementRoutes: Routes = sections.flatMap((section) => {
  const editor = () =>
    import('./pages/admin/directory/editor.component').then((module) => module.EditorComponent);
  return [
    {
      path: section.path,
      pathMatch: 'full' as const,
      data: { section },
      title: section.label + ' - Administração de acessos Lactec',
      loadComponent: () =>
        import('./pages/admin/directory/directory.component').then(
          (module) => module.DirectoryComponent,
        ),
    },
    {
      path: section.path + '/novo',
      data: { section },
      title: section.create + ' - Administração de acessos Lactec',
      canDeactivate: [leaveEditor],
      loadComponent: editor,
    },
    {
      path: section.path + '/:id',
      data: { section },
      title: section.edit + ' - Administração de acessos Lactec',
      canDeactivate: [leaveEditor],
      loadComponent: editor,
    },
  ];
});

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./layouts/wrapper-auth/wrapper-auth.component').then(
        (module) => module.WrapperAuthComponent,
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/login/login.component').then((module) => module.LoginComponent),
        title: 'Acessar - Administração de acessos Lactec',
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () =>
      import('./layouts/shell/shell.component').then((module) => module.ShellComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/admin/overview.component').then((module) => module.OverviewComponent),
        title: 'Visão geral - Administração de acessos Lactec',
      },
      ...managementRoutes,
    ],
  },
  { path: '**', redirectTo: 'login' },
];
