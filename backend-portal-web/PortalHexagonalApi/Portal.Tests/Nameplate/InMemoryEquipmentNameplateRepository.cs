using Portal.Application.Nameplate;
using Portal.Application.Nameplate.Exceptions;
using Portal.Domain.Nameplate;

namespace Portal.Tests.Nameplate;

/// <summary>
/// Duble da porta conduzida do cadastro, e o nome diz a tecnologia que ele de fato usa:
/// memoria. Cockburn pede um duble em cada porta justamente para a porta ser diferente de
/// qualquer linha desenhada no diagrama.
///
/// Ele guarda quais series recebeu, e nao so o que respondeu. Nesta fatia isso nao e zelo de
/// cobertura, e a unica prova possivel de duas decisoes que o fio nao mostra.
///
/// A primeira e a pergunta em lote: perguntar uma vez por equipamento dentro do laco devolve
/// exatamente a mesma resposta HTTP e faz um adaptador de banco abrir uma consulta por linha
/// da listagem.
///
/// A segunda e o isolamento. O cadastro nao tem coluna de empresa, entao nao existe filtro de
/// inquilino para conferir: o que protege o cliente e o conjunto de series que a aplicacao
/// decide perguntar. Um servico que perguntasse pela chave vinda da rota, ou pelas series de
/// todos os inquilinos, serviria a mesma resposta para a requisicao legitima, e e por isso
/// que nenhum teste de contrato percebe a troca.
/// </summary>
public sealed class InMemoryEquipmentNameplateRepository : IEquipmentNameplateRepository
{
    private readonly List<EquipmentNameplate> _nameplates;

    public InMemoryEquipmentNameplateRepository(params EquipmentNameplate[] nameplates)
    {
        _nameplates = [.. nameplates];
    }

    public int TimesTheListWasAsked { get; private set; }

    public int TimesOneSerialNumberWasAsked { get; private set; }

    public List<string> SerialNumbersAsked { get; } = [];

    /// <summary>
    /// Quando preenchida, a fonte falha em vez de responder. E o unico jeito de provar o que a
    /// aplicacao faz com a queda do cadastro sem derrubar um SQL Server.
    /// </summary>
    public NameplateSourceUnavailableException? Failure { get; init; }

    public Task<IReadOnlyList<EquipmentNameplate>> GetNameplatesOfAsync(
        IReadOnlyCollection<string> serialNumbers,
        CancellationToken cancellationToken)
    {
        TimesTheListWasAsked++;
        SerialNumbersAsked.AddRange(serialNumbers);

        if (Failure is not null) throw Failure;

        IReadOnlyList<EquipmentNameplate> found = [.. _nameplates
            .Where(nameplate => serialNumbers.Contains(nameplate.SerialNumber, StringComparer.Ordinal))];

        return Task.FromResult(found);
    }

    public Task<EquipmentNameplate?> GetNameplateOfAsync(
        string serialNumber,
        CancellationToken cancellationToken)
    {
        TimesOneSerialNumberWasAsked++;
        SerialNumbersAsked.Add(serialNumber);

        if (Failure is not null) throw Failure;

        EquipmentNameplate? found = _nameplates.FirstOrDefault(nameplate => string.Equals(
            nameplate.SerialNumber, serialNumber, StringComparison.Ordinal));

        return Task.FromResult(found);
    }
}
