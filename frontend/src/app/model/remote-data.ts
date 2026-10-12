// Estado de uma busca remota como união discriminada.
//
// Existe para a tela parar de adivinhar: com `dados | null` + `falhou`, o par
// (null, false) significava "carregando" E "vazio" ao mesmo tempo, e o
// template cobria isso com uma cascata de `?.` e `?? []`. Aqui cada estado é
// um caso e o `@switch` obriga a tratar todos.
//
// Incomputabilidade NÃO mora aqui: uma análise sem C₂H₂ é resposta bem-sucedida
// do servidor com um diagnóstico a menos, não falha de requisição. Esse caso é
// `TriangleReading` em `health.ts`, por análise.

export type RemoteData<T> =
  | { readonly kind: 'loading' }
  | { readonly kind: 'empty' }
  | { readonly kind: 'failure'; readonly reason: string }
  | { readonly kind: 'ok'; readonly data: T };

export const loading = { kind: 'loading' } as const;
export const empty = { kind: 'empty' } as const;

export const failure = (reason: string): RemoteData<never> => ({ kind: 'failure', reason });

export const ok = <T>(data: T): RemoteData<T> => ({ kind: 'ok', data });
