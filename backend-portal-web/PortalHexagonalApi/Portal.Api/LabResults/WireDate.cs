namespace Portal.Api.LabResults;

/// <summary>
/// Toda data que sai desta API sai em ano-mes-dia, e so por aqui. Eram quatro formatacoes
/// espalhadas em dois formatos: no mesmo corpo de resposta, a analise selecionada saia em
/// ano-mes-dia e a cabeceira da tabela saia em dia-mes-ano. Quem le precisava descobrir o
/// formato campo a campo, e nao dava para comparar duas datas sem reinterpretar o texto.
///
/// Como a data e exibida e escolha da tela, nao do contrato: o navegador sabe o fuso e o
/// idioma de quem esta lendo, e a API nao sabe nenhum dos dois.
///
/// O formato "O" de <see cref="DateOnly"/> e sempre gregoriano, qualquer que seja o
/// calendario em que o processo esteja rodando. Escrever "yyyy-MM-dd" sem mais nada seguiria
/// o calendario do processo, e medido: a mesma data sai 2026-07-06 em portugues do Brasil,
/// 1448-01-21 em arabe da Arabia Saudita e 2569-07-06 em tailandes.
/// </summary>
internal static class WireDate
{
    public static string From(DateTime moment)
    {
        return DateOnly.FromDateTime(moment).ToString("O");
    }
}
