import type { InternalRole } from '@/app/model/internal-user';
import type { TourDefinition, TourStep } from '@/app/model/tour';

export const MENU_TOUR_TARGET = {
  dashboard: 'menu-dashboard',
  quotes: 'menu-orcamentos',
  technicians: 'menu-tecnicos',
  companies: 'menu-empresas',
  clients: 'menu-clientes',
  tests: 'menu-ensaios',
  reports: 'menu-relatorios',
  equipment: 'menu-equipamentos',
  help: 'menu-ajuda',
  profile: 'menu-perfil',
  logout: 'menu-sair',
  collapse: 'menu-recolher',
} as const;

interface MenuEntry {
  readonly label: string;
  readonly target: string;
  readonly text: string;
}

const ADMIN_MENU: readonly MenuEntry[] = [
  {
    label: 'Dashboard',
    target: MENU_TOUR_TARGET.dashboard,
    text: 'Abra o painel para ver os indicadores gerais e os atalhos da administração.',
  },
  {
    label: 'Orçamentos',
    target: MENU_TOUR_TARGET.quotes,
    text: 'Consulte os orçamentos, filtre por situação e acesse seus detalhes ou cadastre um novo.',
  },
  {
    label: 'Técnicos',
    target: MENU_TOUR_TARGET.technicians,
    text: 'Consulte os técnicos cadastrados e gerencie seus dados e situação de acesso.',
  },
  {
    label: 'Empresas',
    target: MENU_TOUR_TARGET.companies,
    text: 'Consulte e mantenha os dados das empresas atendidas pelo laboratório.',
  },
  {
    label: 'Clientes',
    target: MENU_TOUR_TARGET.clients,
    text: 'Consulte os usuários clientes e as empresas associadas a cada acesso do portal.',
  },
];

const TECHNICIAN_MENU: readonly MenuEntry[] = [
  {
    label: 'Dashboard',
    target: MENU_TOUR_TARGET.dashboard,
    text: 'Veja os indicadores do trabalho técnico, os atalhos e os laudos que aguardam validação.',
  },
  {
    label: 'Orçamentos',
    target: MENU_TOUR_TARGET.quotes,
    text: 'Pesquise orçamentos e abra os detalhes para consultar os serviços solicitados.',
  },
  {
    label: 'Manter Ensaio',
    target: MENU_TOUR_TARGET.tests,
    text: 'Consulte os ensaios, cadastre execuções e atualize seus dados e status.',
  },
  {
    label: 'Pesquisar Relatório',
    target: MENU_TOUR_TARGET.reports,
    text: 'Localize relatórios técnicos, revise os documentos e acompanhe sua validação e publicação.',
  },
  {
    label: 'Equipamentos',
    target: MENU_TOUR_TARGET.equipment,
    text: 'Consulte e mantenha os equipamentos cadastrados no laboratório.',
  },
];

export function menuTour(role: InternalRole): TourDefinition {
  const navigation = role === 'ADMIN' ? ADMIN_MENU : TECHNICIAN_MENU;
  const steps: TourStep[] = navigation.map((item) => ({
    id: item.target,
    target: item.target,
    region: 'menu',
    side: 'right',
    title: item.label,
    text: item.text,
  }));

  steps.push(
    {
      id: 'menu-ajuda',
      target: MENU_TOUR_TARGET.help,
      region: 'menu',
      side: 'right',
      title: 'Ajuda',
      text: 'Abra a Ajuda para rever esta apresentação ou consultar as orientações da tela atual.',
    },
    {
      id: 'menu-perfil',
      target: MENU_TOUR_TARGET.profile,
      region: 'menu',
      side: 'right',
      title: 'Perfil',
      text: 'Este atalho está reservado às informações da conta. A edição do perfil não está disponível no Portal Interno.',
    },
    {
      id: 'menu-recolher',
      target: MENU_TOUR_TARGET.collapse,
      region: 'menu',
      side: 'right',
      title: 'Recolher menu',
      text: 'Recolha a barra lateral para ampliar a área de trabalho. Os ícones permanecem visíveis.',
    },
    {
      id: 'menu-sair',
      target: MENU_TOUR_TARGET.logout,
      region: 'menu',
      side: 'bottom',
      title: 'Sair',
      text: 'Encerra sua sessão no Portal Interno. O botão fica inativo enquanto esta apresentação está aberta.',
    },
  );

  return { id: `menu-${role.toLowerCase()}`, label: 'Sobre o menu', steps };
}

function step(id: string, target: string, title: string, text: string, side?: TourStep['side']): TourStep {
  return { id, target, title, text, side };
}

function listTour(
  id: string,
  heading: string,
  introduction: string,
  filters: string,
  results: string,
): TourDefinition {
  return {
    id,
    label: 'Sobre esta página',
    steps: [
      step('cabecalho', 'pagina-cabecalho', heading, introduction, 'bottom'),
      step('filtros', 'pagina-filtros', 'Busca e filtros', filters, 'bottom'),
      step('resultados', 'pagina-resultados', 'Resultados', results),
      step(
        'paginacao',
        'pagina-paginacao',
        'Andar entre as páginas',
        'Veja quantos registros foram encontrados. Quando houver mais resultados do que cabem em uma página, use Próximo e Anterior para navegar entre eles.',
        'top',
      ),
    ],
  };
}

function dashboardTour(role: InternalRole): TourDefinition {
  const administrator = role === 'ADMIN';
  return {
    id: `dashboard-${role.toLowerCase()}`,
    label: 'Sobre esta página',
    steps: [
      step(
        'indicadores',
        'pagina-indicadores',
        'Indicadores do painel',
        administrator
          ? 'Os cartões resumem os dados gerais disponíveis para a administração.'
          : 'Os cartões mostram o andamento dos ensaios e das análises documentais do laboratório.',
      ),
      step(
        'atalhos',
        'pagina-atalhos',
        'Acessos rápidos',
        administrator
          ? 'Use estes atalhos para abrir os principais cadastros e consultar orçamentos.'
          : 'Acesse por aqui ensaios, relatórios e equipamentos sem voltar ao menu lateral.',
      ),
      ...(!administrator
        ? [
            step(
              'laudos-pendentes',
              'pagina-pendencias',
              'Laudos pendentes de validação',
              'Esta lista reúne os documentos que aguardam revisão. Use Ver todos para abrir a pesquisa de relatórios.',
            ),
          ]
        : []),
    ],
  };
}

function testFormTour(): TourDefinition {
  return {
    id: 'formulario-ensaio',
    label: 'Sobre esta página',
    steps: [
      step(
        'orcamento',
        'ensaio-form-orcamento',
        'Orçamento',
        'Selecione o orçamento desta execução. As amostras disponíveis na etapa seguinte pertencem a ele.',
      ),
      step(
        'amostra',
        'ensaio-form-amostra',
        'Identificação da amostra',
        'Selecione uma amostra do orçamento ou use Nova amostra. A nova identificação é salva junto com o ensaio.',
      ),
      step(
        'tipo-analise',
        'ensaio-form-tipo',
        'Tipo de análise',
        'Descreva o tipo de análise realizado. O campo ajuda a identificar a execução.',
      ),
      step(
        'formato-resultados',
        'ensaio-form-formato',
        'Formato dos resultados',
        'Escolha Físico-químico, Cromatografia ou Outro tipo. Essa opção define o modelo das medições. Em Outro tipo, você pode montar as linhas.',
      ),
      step(
        'metodo-norma',
        'ensaio-form-metodo-norma',
        'Método e norma',
        'Informe o método e a norma gerais do ensaio. Na tabela, cada medição também pode ter método e referência próprios.',
      ),
      step(
        'agendamento',
        'ensaio-form-agendamento',
        'Data de agendamento',
        'Informe a data prevista para a realização do ensaio.',
      ),
      step(
        'responsavel',
        'ensaio-form-responsavel',
        'Responsável técnico',
        'Selecione o técnico responsável pela execução. Este campo é necessário para salvar o ensaio.',
      ),
      step(
        'instrumentos',
        'ensaio-form-instrumentos',
        'Instrumentos laboratoriais',
        'Selecione os instrumentos usados na análise, se houver. Se algum não estiver na lista, use Cadastrar instrumento. Eles são diferentes do equipamento analisado associado à amostra.',
      ),
      step(
        'classificacao',
        'ensaio-form-classificacao',
        'Classificação da análise',
        'Escolha uma classificação disponível ou mantenha “Não classificada”. O preenchimento é opcional.',
      ),
      step(
        'medicoes',
        'ensaio-form-medicoes',
        'Medições e resultados',
        'Cada linha reúne medição, método, referência e resultado. Escolha o tipo para distinguir Quantidade, Não detectado (ND), Não realizado e Texto. Em Outro tipo, adicione ou remova linhas e informe nome e unidade.',
      ),
      step(
        'texto-rascunho',
        'ensaio-form-rascunho',
        'Texto técnico do laudo',
        'Este texto fica como rascunho. Salvar o ensaio não gera nem publica um PDF para o cliente.',
      ),
      step(
        'acoes',
        'ensaio-form-acoes',
        'Salvar ensaio',
        'Use Salvar para registrar os dados ou Cancelar para voltar à lista de ensaios. Ao editar, você também pode atualizar o status por aqui.',
      ),
    ],
  };
}

function testStatusTour(): TourDefinition {
  return {
    id: 'status-ensaio',
    label: 'Sobre esta página',
    steps: [
      step('resumo', 'ensaio-status-resumo', 'Dados do ensaio', 'Confira a identificação e o contexto da execução.'),
      step('fluxo', 'ensaio-status-fluxo', 'Fluxo de status', 'Acompanhe as etapas registradas para este ensaio.'),
      step('documento', 'ensaio-status-documento', 'Relatório técnico', 'Valide e publique o documento quando as ações estiverem disponíveis.'),
      step('historico', 'ensaio-status-historico', 'Histórico de status', 'Consulte as alterações anteriores e suas justificativas.'),
    ],
  };
}

export function pageTourFor(url: string): TourDefinition | null {
  const path = url.split(/[?#]/, 1)[0].replace(/\/$/, '') || '/';

  if (path === '/admin') return dashboardTour('ADMIN');
  if (path === '/tecnico') return dashboardTour('TECNICO');

  if (path === '/admin/orcamentos') {
    return listTour(
      'orcamentos-administrador',
      'Orçamentos',
      'Acesse os orçamentos dos laboratórios e use Novo orçamento para registrar uma proposta.',
      'Pesquise por proposta, cliente, nome ou descrição e filtre os registros pela situação.',
      'Confira os dados principais e use as ações da linha para abrir ou editar um orçamento.',
    );
  }
  if (path === '/admin/tecnicos') {
    return listTour(
      'tecnicos-administrador',
      'Pesquisar técnicos',
      'Consulte os técnicos cadastrados ou use Novo técnico para incluir um acesso.',
      'Busque por nome, e-mail ou unidade e filtre pelo status do cadastro.',
      'Confira os dados do técnico e use as ações disponíveis para editar ou alterar seu status.',
    );
  }
  if (path === '/admin/empresas') {
    return listTour(
      'empresas-administrador',
      'Pesquisar empresas',
      'Consulte as empresas clientes cadastradas ou use Nova empresa para incluir uma.',
      'Busque por razão social, CNPJ, cidade, contato ou e-mail e filtre pelo status.',
      'Confira os dados cadastrais e de contato das empresas listadas.',
    );
  }
  if (path === '/admin/clientes') {
    return listTour(
      'clientes-administrador',
      'Pesquisar clientes',
      'Consulte os usuários clientes ou use Novo cliente para cadastrar um acesso.',
      'Busque por nome, e-mail ou empresa e filtre pelo status do cadastro.',
      'Confira os dados do usuário e as empresas associadas ao acesso do portal.',
    );
  }
  if (path === '/tecnico/orcamentos') {
    return listTour(
      'orcamentos-tecnico',
      'Pesquisar orçamentos',
      'Consulte os orçamentos disponíveis para o trabalho técnico.',
      'Combine a busca por proposta, nome ou empresa com período e status técnico.',
      'Abra um orçamento para conferir seus serviços, dados e andamento técnico.',
    );
  }
  if (path === '/tecnico/ensaios') {
    return listTour(
      'ensaios-tecnico',
      'Manter ensaios',
      'Consulte as execuções do laboratório ou use Novo ensaio para registrar uma análise.',
      'Pesquise por código, amostra, empresa ou tipo de análise.',
      'A lista mostra o contexto de cada execução e as ações disponíveis para consultar ou atualizar.',
    );
  }
  if (path === '/tecnico/equipamentos') {
    return listTour(
      'equipamentos-tecnico',
      'Pesquisar equipamentos',
      'Consulte os equipamentos do laboratório ou use Novo equipamento para cadastrar um.',
      'Pesquise por número de série, TAG ou proprietário e filtre por tipo ou nível de risco.',
      'Confira os dados e o nível de risco dos equipamentos listados.',
    );
  }
  if (path === '/tecnico/relatorios' || /^\/tecnico\/relatorios\/[^/]+$/.test(path)) {
    return {
      id: 'relatorios-tecnico',
      label: 'Sobre esta página',
      steps: [
        step('filtros', 'pagina-filtros', 'Busca e filtros', 'Pesquise por registro, amostra, cliente ou tipo e combine os filtros documentais.'),
        step('lista', 'pagina-resultados', 'Lista de relatórios', 'Selecione um documento para consultar seus dados e o estado da validação.'),
        step('detalhe', 'relatorio-detalhe', 'Detalhes do relatório', 'Revise as informações técnicas e use as ações disponíveis para validar ou publicar o documento.'),
      ],
    };
  }
  if (path === '/tecnico/ensaios/novo' || /^\/tecnico\/ensaios\/[^/]+\/editar$/.test(path)) {
    return testFormTour();
  }
  if (/^\/tecnico\/ensaios\/[^/]+\/status$/.test(path)) return testStatusTour();

  return null;
}
