namespace Portal.Domain.Nameplate.Exceptions;

/// <summary>
/// Pediu-se a concordancia das linhas de uma serie e uma das linhas fala de outra serie.
///
/// Nao e dado ruim do cadastro: e defeito de quem agrupou antes de chamar. A concordancia
/// campo a campo em cima de linhas de series diferentes produziria uma placa inventada, com o
/// fabricante de um equipamento e a tensao de outro, e nenhuma resposta HTTP ficaria invalida
/// por isso.
///
/// A serie recusada nao viaja na mensagem, e aqui esta fatia se afasta de
/// <c>AnalysisSeries.Build</c>, que nomeia o protocolo recusado. <c>NumSerie</c> e dado do
/// cliente e dez linhas do cadastro o trazem com nome de empresa dentro, entao a frase diz o
/// que aconteceu e o log nao ganha o valor.
/// </summary>
public sealed class NameplateOfAnotherSerialNumberException : Exception
{
    public NameplateOfAnotherSerialNumberException()
        : base("Linha de cadastro pertence a outro numero de serie que nao o consultado.")
    {
    }
}
