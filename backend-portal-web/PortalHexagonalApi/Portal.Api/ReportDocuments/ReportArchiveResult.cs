using System.IO.Compression;

using Microsoft.AspNetCore.Mvc;
using Microsoft.Net.Http.Headers;

using Portal.Application.ReportDocuments;

namespace Portal.Api.ReportDocuments;

public sealed class ReportArchiveResult : IActionResult
{
    private readonly string _fileName;
    private readonly IAsyncEnumerator<ArchivedReportFile> _files;

    public ReportArchiveResult(string fileName, IAsyncEnumerator<ArchivedReportFile> startedFiles)
    {
        _fileName = fileName;
        _files = startedFiles;
    }

    public async Task ExecuteResultAsync(ActionContext context)
    {
        ArgumentNullException.ThrowIfNull(context);

        HttpContext http = context.HttpContext;
        HttpResponse response = http.Response;
        CancellationToken cancellationToken = http.RequestAborted;
        ILogger logger = http.RequestServices.GetRequiredService<ILoggerFactory>()
            .CreateLogger<ReportArchiveResult>();

        response.StatusCode = StatusCodes.Status200OK;
        response.ContentType = "application/zip";
        response.Headers.CacheControl = "no-store";
        response.Headers[HeaderNames.ContentDisposition] = $"attachment; filename=\"{_fileName}\"";

        await using IAsyncEnumerator<ArchivedReportFile> files = _files;
        await using EntryBuffer buffer = new();

        try
        {
            using (ZipArchive archive = new(buffer, ZipArchiveMode.Create, leaveOpen: true))
            {
                do
                {
                    ArchivedReportFile file = files.Current;

                    await using (file.Content)
                    {
                        ZipArchiveEntry entry = archive.CreateEntry(file.Name, CompressionLevel.NoCompression);

                        await using (Stream target = entry.Open())
                            await file.Content.CopyToAsync(target, cancellationToken);
                    }

                    await buffer.DrainToAsync(response.Body, cancellationToken);
                }
                while (await files.MoveNextAsync());
            }

            await buffer.DrainToAsync(response.Body, cancellationToken);
        }
        catch (Exception failure) when (failure is not OperationCanceledException || !cancellationToken.IsCancellationRequested)
        {
            logger.LogError(failure, "O arquivo compactado de laudos foi interrompido depois de iniciado.");
            http.Abort();
        }
    }

    private sealed class EntryBuffer : Stream
    {
        private readonly MemoryStream _pending = new();

        public override bool CanRead => false;

        public override bool CanSeek => false;

        public override bool CanWrite => true;

        public override long Length => throw new NotSupportedException();

        public override long Position
        {
            get => throw new NotSupportedException();
            set => throw new NotSupportedException();
        }

        public override void Write(byte[] buffer, int offset, int count) => _pending.Write(buffer, offset, count);

        public override void Write(ReadOnlySpan<byte> buffer) => _pending.Write(buffer);

        public override Task WriteAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken)
        {
            _pending.Write(buffer, offset, count);
            return Task.CompletedTask;
        }

        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default)
        {
            _pending.Write(buffer.Span);
            return ValueTask.CompletedTask;
        }

        public override void Flush()
        {
        }

        public override Task FlushAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public async Task DrainToAsync(Stream target, CancellationToken cancellationToken)
        {
            if (_pending.Length == 0) return;

            await target.WriteAsync(_pending.GetBuffer().AsMemory(0, (int)_pending.Length), cancellationToken);
            await target.FlushAsync(cancellationToken);
            _pending.SetLength(0);
        }

        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();

        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();

        public override void SetLength(long value) => throw new NotSupportedException();

        protected override void Dispose(bool disposing)
        {
            if (disposing) _pending.Dispose();

            base.Dispose(disposing);
        }
    }
}
