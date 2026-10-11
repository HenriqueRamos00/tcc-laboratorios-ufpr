namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// A fonte trouxe um nome de resultado que a lista de itens conhecidos nao tem, e que a
/// aritmetica do totalizador tambem nao reconheceu.
///
/// Falhar aqui e deliberado. Formula, unidade, papel, metodo e limite normativo so existem
/// nessa lista, e publicar um resultado sem eles deixaria a tela comparar ppm com miligrama
/// por grama em silencio. O nome recusado viaja na mensagem porque e ele que diz a quem
/// atende qual linha da lista falta escrever.
/// </summary>
public sealed class ResultNameOutsideKnownListException : Exception
{
    public ResultNameOutsideKnownListException(string sourceName)
        : base($"Nome de resultado fora da lista de itens conhecidos: '{sourceName}'.")
    {
        SourceName = sourceName;
    }

    public string SourceName { get; }
}
