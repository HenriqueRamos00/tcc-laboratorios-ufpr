using Portal.Adapters.Nameplate;
using Portal.Application.Nameplate;

namespace Portal.Api.Nameplate;

/// <summary>
/// O configurador da porta conduzida do cadastro, na raiz de composicao. E o quinto elemento
/// do padrao: o unico codigo que conhece ao mesmo tempo a aplicacao e os atores conduzidos, e
/// que decide qual deles a aplicacao vai usar.
///
/// Fica em arquivo proprio, ao lado de <c>LabTestSourceRegistration</c> e de
/// <c>LaboratoryProgressSourceRegistration</c>, porque a fonte do cadastro e ortogonal a
/// fonte dos ensaios: o portal pode rodar com a amostra embutida de laudos e o cadastro
/// desligado, e e assim que ele roda em producao hoje.
/// </summary>
public static class EquipmentRegistrySourceRegistration
{
    public static IServiceCollection AddEquipmentRegistrySource(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        EquipmentRegistryOptions options = configuration
            .GetSection(EquipmentRegistryOptions.SectionName)
            .Get<EquipmentRegistryOptions>() ?? new EquipmentRegistryOptions();

        string source = string.IsNullOrWhiteSpace(options.Source)
            ? EquipmentRegistryOptions.NotConnectedSource
            : options.Source;

        switch (source)
        {
            case EquipmentRegistryOptions.NotConnectedSource:
                services.AddScoped<IEquipmentNameplateRepository, NullEquipmentNameplateRepository>();
                return services;

            case EquipmentRegistryOptions.InMemorySampleSource:
                services.AddScoped<IEquipmentNameplateRepository, InMemoryEquipmentRegistryRepository>();
                return services;

            // Quem escrever este adaptador precisa saber de tres coisas medidas nas 20575
            // linhas do extrato, porque nenhuma delas aparece num teste em memoria.
            //
            // Primeira: o SQL Server ignora espaco a direita na comparacao de texto, por
            // preenchimento ANSI, entao um `WHERE NumSerie = '5233'` tambem casa '5233   ', e
            // o casamento exato que esta fatia promete deixa de valer no banco. Aqui isso nao
            // e detalhe: casar duas series distintas publica a placa de um transformador na
            // linha de outro, possivelmente de outro cliente.
            //
            // Segunda: `NumSerie` nao e chave. Um `WHERE NumSerie IN (...)` com 40 series pode
            // voltar com 41 linhas, e o adaptador tem de entregar TODAS as linhas de cada
            // serie para `EquipmentRegistryReading`. Resolver no SQL com `DISTINCT`, `TOP 1`
            // ou `GROUP BY` escolhe dado errado nas 49 series ambiguas, em silencio, e a
            // resposta HTTP continua valida.
            //
            // Terceira: dez linhas carregam nome de cliente dentro de `NumSerie` e de `Tag`.
            // Nenhum valor dessas duas colunas pode entrar em linha de log nem em mensagem de
            // excecao, nem no texto da consulta registrada.
            case EquipmentRegistryOptions.SqlServerSource:
                throw new NotSupportedException(
                    $"A fonte '{EquipmentRegistryOptions.SqlServerSource}' ainda nao tem adaptador. "
                    + "Nao existe conexao com o SQL Server do AutoLAB.");

            default:
                throw new InvalidOperationException(
                    $"Configuracao '{EquipmentRegistryOptions.SectionName}:Source' com valor desconhecido "
                    + $"'{options.Source}'. Valores aceitos: "
                    + $"'{EquipmentRegistryOptions.NotConnectedSource}', "
                    + $"'{EquipmentRegistryOptions.InMemorySampleSource}', "
                    + $"'{EquipmentRegistryOptions.SqlServerSource}'.");
        }
    }
}
