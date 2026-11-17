namespace Portal.Application.LabResults.Exceptions;

public sealed class ClientLabRequestRejectedException : Exception
{
    public ClientLabRequestRejectedException() : base("A fonte recusou os parâmetros da consulta.") { }
}
