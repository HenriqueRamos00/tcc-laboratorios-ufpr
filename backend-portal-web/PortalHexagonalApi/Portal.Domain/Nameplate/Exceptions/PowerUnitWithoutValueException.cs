namespace Portal.Domain.Nameplate.Exceptions;

/// <summary>
/// Tentou-se montar uma potencia de placa com unidade e sem valor.
///
/// O caso existe na fonte: 7 linhas tem <c>UnidadePotencia</c> preenchida e <c>Potencia</c>
/// vazia. Uma unidade sozinha nao e uma grandeza, e publicar "kVA" na coluna de potencia
/// faria a tela afirmar que o cadastro informa a potencia quando ele nao informa. Quem le o
/// cadastro descarta a unidade orfa; esta excecao impede que um adaptador novo a promova a
/// potencia.
/// </summary>
public sealed class PowerUnitWithoutValueException : Exception
{
    public PowerUnitWithoutValueException()
        : base("Unidade de potencia sem valor de potencia.")
    {
    }
}
