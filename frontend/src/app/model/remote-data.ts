// Estado de uma busca remota como união discriminada.
//
// Existe para a tela parar de adivinhar: com `dados | null` + `falhou`, o par
// (null, false) significava "carregando" E "vazio" ao mesmo tempo, e o
// template cobria isso com uma cascata de `?.` e `?? []`. Aqui cada estado é
// um caso e o `@switch` obriga a tratar todos.
//
// Incomputabilidade NÃO mora aqui: uma coleta sem C₂H₂ é resposta bem-sucedida
// do servidor com um diagnóstico a menos, não falha de requisição. Esse caso é
// `LeituraDoTriangulo` em `health.ts`, por coleta.

export type RemoteData<T> =
  | { readonly tipo: 'carregando' }
  | { readonly tipo: 'vazio' }
  | { readonly tipo: 'erro'; readonly motivo: string }
  | { readonly tipo: 'ok'; readonly dados: T };

export const carregando = { tipo: 'carregando' } as const;
export const vazio = { tipo: 'vazio' } as const;

export const erro = (motivo: string): RemoteData<never> => ({ tipo: 'erro', motivo });

export const ok = <T>(dados: T): RemoteData<T> => ({ tipo: 'ok', dados });
