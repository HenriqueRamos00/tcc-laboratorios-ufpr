namespace Portal.Domain.Pipeline;

/// <summary>
/// Os degraus da progressao que a tela do cliente mostra, na ordem em que a solicitacao
/// anda. E a mesma escada de <c>frontend/src/app/model/analysis-pipeline.ts</c>: degrau
/// novo aqui sem degrau novo la, ou em ordem diferente, deixa a tela e a API contando
/// historias diferentes sobre o mesmo pedido.
///
/// Os seis primeiros degraus sao do lado comercial e esta fatia nao os produz. Os oito
/// ultimos sao a metade do laboratorio, e desses o AutoLAB produz seis: nenhuma linha das
/// 3345 chega a <see cref="Received"/> nem a <see cref="ProtocolAssigned"/>, porque
/// <c>Recebido</c> e <c>Protocolado</c> nunca vem separados e <c>SituacaoEnsaios</c> sempre
/// chega preenchido ou nulo, o que ja vale <see cref="InProgress"/>. Os dois degraus
/// existem porque a escada e compartilhada com o lado comercial, que produz "recebido" a
/// partir de "Fechado ganho".
///
/// O que cada degrau do laboratorio quer dizer, no vocabulario do laboratorio:
/// <list type="bullet">
/// <item><see cref="InProgress"/>: os ensaios da amostra estao sendo executados.</item>
/// <item><see cref="Complete"/>: os ensaios terminaram e aguardam validacao.</item>
/// <item><see cref="Validated"/>: o resultado dos ensaios foi validado.</item>
/// <item><see cref="Finalized"/>: o processo de ensaio esta encerrado.</item>
/// <item><see cref="DraftingReport"/>: o relatorio do cliente esta sendo escrito.</item>
/// <item><see cref="ReportPublished"/>: o relatorio esta disponivel ao cliente.</item>
/// </list>
///
/// Cancelado e recusado nao estao aqui de proposito: nao sao degraus da progressao, e sim
/// o fim dela, e a tela os modela separados em <c>ClosedStage</c>. Nenhuma coluna de
/// <c>SituaProcess</c> informa encerramento.
/// </summary>
public enum PipelineStage
{
    Qualification,
    AreaAnalysis,
    DraftingProposal,
    Negotiation,
    ApprovedByClient,
    AwaitingSample,
    Received,
    ProtocolAssigned,
    InProgress,
    Complete,
    Validated,
    Finalized,
    DraftingReport,
    ReportPublished
}
