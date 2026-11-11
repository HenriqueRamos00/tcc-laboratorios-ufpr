using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;

namespace Portal.Application.LabResults;

/// <summary>
/// O que a porta condutora devolve quando a pergunta e sobre equipamento: o que os laudos
/// provam e o que o cadastro afirma, lado a lado e sem se misturar.
///
/// Sao dois objetos e nao um porque as duas procedencias nao tem a mesma garantia.
/// <see cref="Equipment"/> nasce das analises do inquilino e existe sempre que existe laudo.
/// <see cref="Nameplate"/> vem de uma tabela que nao tem a serie como chave, nao tem data e
/// nao tem empresa, e e nula com frequencia. Despejar os campos do cadastro dentro de
/// <see cref="TestedEquipment"/> apagaria essa diferenca: aquele tipo afirma o que o laudo
/// garante, e passaria a afirmar tambem o que uma tabela sem chave sugere.
///
/// Mora na aplicacao, ao lado de <see cref="SampleHealthIndicators"/>, porque e forma de
/// resposta de uma porta condutora e nao conceito de dominio. O dominio continua com dois
/// conceitos separados, em dois grupos separados, e nenhum dos dois conhece o outro.
/// </summary>
public sealed record EquipmentWithNameplate(
    TestedEquipment Equipment,
    EquipmentNameplate? Nameplate,
    int ReportCount);
