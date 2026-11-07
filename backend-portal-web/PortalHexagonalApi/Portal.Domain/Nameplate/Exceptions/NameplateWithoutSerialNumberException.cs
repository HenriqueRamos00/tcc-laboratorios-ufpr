namespace Portal.Domain.Nameplate.Exceptions;

/// <summary>
/// Tentou-se afirmar uma placa sem dizer de qual numero de serie ela e.
///
/// Falhar aqui e deliberado. Uma placa sem serie nao casa com equipamento nenhum, entao ou
/// ela desapareceria em silencio, ou, se alguem casasse por vazio, apareceria no equipamento
/// errado do cliente errado. Neste cadastro isso nao e hipotese: 2790 linhas trazem a serie
/// vazia ou um marcador no lugar dela, e casar vazio com vazio publicaria uma dessas placas
/// em todo equipamento enderecado pelo protocolo. O adaptador que le o cadastro ja descarta
/// essas linhas; esta excecao e a garantia de que um adaptador novo nao as reintroduza sem
/// ninguem perceber.
///
/// A mensagem nao tem nada da linha recusada, de proposito. A recusa acontece justamente
/// quando a serie esta vazia, e interpolar as outras colunas levaria <c>Tag</c> para o log:
/// dez linhas do cadastro carregam nome de cliente real dentro de <c>Tag</c> e de
/// <c>NumSerie</c>.
/// </summary>
public sealed class NameplateWithoutSerialNumberException : Exception
{
    public NameplateWithoutSerialNumberException()
        : base("Placa de equipamento sem numero de serie.")
    {
    }
}
