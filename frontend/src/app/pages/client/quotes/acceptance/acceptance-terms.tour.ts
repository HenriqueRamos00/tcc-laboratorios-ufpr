import type { TourDefinition } from '@/app/model/tour';

export function acceptanceTermsTour(): TourDefinition {
  return {
    id: 'termo-de-aceite',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'cobranca',
        target: 'aceite-cobranca',
        side: 'top',
        title: 'Endereço para cobrança',
        text: 'Informe os dados da empresa que vai receber a cobrança: razão social, CNPJ, endereço e um contato.',
      },
      {
        id: 'emails-nota',
        target: 'aceite-emails-nota',
        side: 'top',
        title: 'E-mails da nota fiscal',
        text: 'A nota fiscal de serviço é enviada para todos os endereços informados aqui. Preencha pelo menos dois, diferentes entre si.',
      },
      {
        id: 'aprovacao',
        target: 'aceite-aprovacao',
        side: 'top',
        title: 'Aprovação',
        text: 'Identifique quem solicita e quem responde pela aprovação, e diga se os resultados podem ser enviados por e-mail.',
      },
      {
        id: 'envio-amostra',
        target: 'aceite-envio-amostra',
        side: 'top',
        title: 'Envio da amostra',
        text: 'Depois do aceite, a amostra segue para o laboratório. Aqui estão o endereço e como identificar a caixa.',
      },
      {
        id: 'rascunho',
        target: 'aceite-rascunho',
        side: 'top',
        title: 'Rascunho',
        text: 'O que você preenche fica guardado como rascunho neste navegador, então é possível sair e continuar depois. Este aviso mostra quando o rascunho foi guardado.',
      },
      {
        id: 'enviar',
        target: 'aceite-enviar',
        side: 'top',
        title: 'Enviar Aceite',
        text: 'Com tudo preenchido, envie o aceite. O portal confere os campos e pede uma confirmação antes de registrar a aprovação do orçamento.',
      },
    ],
  };
}
