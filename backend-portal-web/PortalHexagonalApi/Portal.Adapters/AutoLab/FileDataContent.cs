using System.IO.Compression;

namespace Portal.Adapters.AutoLab;

public static class FileDataContent
{
    public const int MaxDecodedBytes = 64 * 1024 * 1024;

    private const int GZipOffset = 17;

    private static readonly byte[] CompressedSignature =
        Convert.FromHexString("128B08DA41663B41BBFC2829752DCF96");

    public static byte[] Decode(byte[] stored)
    {
        ArgumentNullException.ThrowIfNull(stored);

        if (!IsCompressed(stored)) return stored;

        using MemoryStream input = new(stored, GZipOffset, stored.Length - GZipOffset, writable: false);
        using GZipStream gzip = new(input, CompressionMode.Decompress);
        using MemoryStream output = new();

        byte[] buffer = new byte[81920];
        int read;

        while ((read = gzip.Read(buffer, 0, buffer.Length)) > 0)
        {
            if (output.Length + read > MaxDecodedBytes)
                throw new InvalidDataException($"O arquivo descomprimido passa de {MaxDecodedBytes} bytes.");

            output.Write(buffer, 0, read);
        }

        return output.ToArray();
    }

    private static bool IsCompressed(byte[] stored)
    {
        return stored.Length > GZipOffset + 2
            && stored.AsSpan(0, CompressedSignature.Length).SequenceEqual(CompressedSignature)
            && stored[GZipOffset] == 0x1F
            && stored[GZipOffset + 1] == 0x8B;
    }
}
