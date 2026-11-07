namespace Portal.Adapters.Quotes;

/// <summary>
/// As clausulas que o orcamento carrega iguais em todo documento. Elas sao texto do Lactec, e
/// nao dado do Salesforce: nenhuma depende do orcamento que esta sendo emitido.
///
/// Estao aqui como texto e nao como arquivo de recurso porque o documento inteiro e montado
/// por codigo, e separar o paragrafo do lugar onde ele e desenhado faria a revisao do
/// documento precisar de dois arquivos abertos.
/// </summary>
internal static class QuotationTerms
{
    public const string Greeting = "Prezado(a) Cliente,";

    public const string Introduction =
        "Conforme solicitado, disponibilizamos o presente orçamento para a realização dos serviços descritos abaixo.";

    public static readonly string[] Taxes =
    [
        "ISSQN não retido e não incidente por força de Medida Liminar concedida nos autos do Mandado de "
        + "Segurança nº 0000430-11.2026.8.16.0004 - 3ª Vara da Fazenda Pública de Curitiba.",
        "Lactec é isento de PIS/COFINS.",
    ];

    public static readonly string[] Billing =
    [
        "Condição de faturamento: 15 dias",
        "Forma de pagamento: Boleto bancário;Depósito bancário",
        "Código de atividade: 17.09 - Perícias, laudos, exames técnicos e análises técnicas",
        "Lei Complementar 16/2003",
        "Atrasos no pagamento incidirão em multa de 2% mais juros de mora de 1% ao mês pro rata die.",
    ];

    public const string InsuranceAndGuarantees =
        "Preços apresentados não incluem despesas para contratação de seguros e garantias específicos para "
        + "execução do escopo proposto. Sendo necessários, favor solicitar revisão da proposta para inclusão dos mesmos.";

    public const string PriceAdjustment =
        "O valor global do Contrato estará sujeito a reajuste anual, com base no IPCA, divulgado pelo IBGE. O "
        + "primeiro reajuste ocorrerá após 12 meses da data de assinatura do CONTRATO, considerando-se o período "
        + "entre o mês anterior ao da assinatura do CONTRATO e o mês anterior ao do reajuste. Os demais reajustes "
        + "ocorrerão a cada 12 meses após o último ocorrido, referentes ao período entre o mês do último reajuste "
        + "e o mês anterior ao reajuste presente.";

    public const string ExecutionDeadline =
        "O período de início dos ensaios/serviços deverá ser agendado com o técnico responsável do laboratório, "
        + "assim como a data de entrega dos equipamentos, após o aceite do orçamento, envio da ordem de compra "
        + "e/ou contrato assinado.";

    public const string Results =
        "Será emitido um relatório, certificado ou laudo contendo os resultados dos serviços realizados, com "
        + "assinatura digital certificada. Caso esse documento deva ser emitido em nome de terceiros, esta "
        + "solicitação deverá ser feita formalmente no ato do aceite do orçamento. A interpretação dos resultados "
        + "ou declaração de conformidade não faz parte do serviço a ser executado. Caso sejam necessárias, o "
        + "laboratório deverá ser consultado previamente e uma regra de decisão deve ser acordada.";

    public const string DeliveryAddressTitle = "Endereço de entrega dos equipamentos";

    public static readonly string[] Delivery =
    [
        "Unidade: LAC - Centro Politécnico da UFPR - Rod. BR-116, nº 8813 - CEP 81531-980, Jardim das Américas, "
        + "Curitiba, Paraná. Caixa Postal: 19067",
        "Horário: das 9h às 11:30 ou das 14h às 16:30h.",
        "Recebimento dos equipamentos: somente em dias úteis, durante horário de atendimento. Os equipamentos "
        + "devem estar acompanhados de nota fiscal de remessa, com identificação do número do orçamento, que "
        + "consta no cabeçalho deste documento. Caso a entrega dos equipamentos aconteça fora do prazo combinado "
        + "ou falte algum acessório indispensável à execução do serviço, um novo prazo precisará ser agendado "
        + "conforme disponibilidade do laboratório. Não serão recebidos os equipamentos sem ordem de compra ou contrato.",
    ];

    public static readonly string[] Return =
    [
        "Após término dos ensaios/serviços, a retirada das amostras deverá ser realizada em até 30 (trinta) dias, "
        + "sendo que após esse período estas serão descartadas de acordo com a legislação vigente.",
        "Para a retirada dos equipamentos é necessária a realização de agendamento prévio com, no mínimo, 01 dia "
        + "de antecedência.",
    ];

    public const string Freight = "Por conta do contratante";

    public const string AdditionalInformation =
        "Qualquer solicitação de alteração no orçamento ou recebimento de equipamentos diferentes do acordado, "
        + "poderá acarretar a alteração do valor da proposta e/ou no prazo de entrega dos resultados. Caso ocorra, "
        + "será emitido uma nova revisão do orçamento.";

    public const string AboutTitle = "Sobre o Lactec";

    public const string About =
        "O Lactec é um dos maiores centros de pesquisa, tecnologia e inovação do Brasil. Atua fortemente nos "
        + "mercados de Energia, Saneamento, Meio Ambiente, Indústria e Infraestrutura em todo o ciclo de inovação, "
        + "desde P&D, ensaios e análises até a execução de processos complexos para o setor de infraestrutura. Há "
        + "67 anos, suas soluções e serviços atendem às demandas atuais dos diversos setores produtivos da economia "
        + "brasileira, como empresas, indústrias e concessionárias de energia. Ao longo da sua história, "
        + "consolidou-se com a conclusão de mais de 500 projetos. Possui mais de 300 profissionais, entre mestres "
        + "e doutores, com sólida experiência de mercado. Sua sede administrativa fica em Curitiba, onde estão "
        + "instaladas outras quatro unidades tecnológicas com ampla infraestrutura de laboratórios para atendimento "
        + "a diversos segmentos industriais, além da unidade localizada em Salvador-BA. O Lactec é Unidade Embrapii "
        + "de Inteligência embarcada e em 2023 tornou-se Future Grid, o Centro de Competência Embrapii Lactec em "
        + "Smart grids e Eletromobilidade.";

    public const string LearnMoreTitle = "Saiba Mais!";

    public const string LearnMore =
        "Acesse o site www.lactec.com.br e acompanhe nossas redes sociais:";

    public static readonly string[] SectionTitles =
    [
        "1. Descrição do orçamento",
        "2. Serviços/ensaios a serem realizados",
        "3. Impostos",
        "4. Informações sobre faturamento e pagamento",
        "5. Seguros e garantias",
        "6. Reajuste de preços",
        "7. Prazo de execução dos serviços/ensaios",
        "8. Apresentação dos resultados",
        "9. Entrega das amostras/equipamentos - Quando aplicável",
        "10. Devolução das amostras/equipamentos - Quando aplicável",
        "11. Transporte e frete - Quando aplicável",
        "12. Informações adicionais",
    ];
}
