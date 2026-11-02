package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.domain.exception.DocumentoOrcamentoMuitoGrandeException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Valida o arquivo da ordem de compra enviado no aceite. São aceitos os mesmos formatos que
 * a tela oferece ao cliente: PDF, DOC e DOCX, com até 50 MiB.
 *
 * <p>O formato é conferido duas vezes: pela extensão do nome e pelos primeiros bytes do
 * conteúdo (a "assinatura" de cada formato). O tipo de conteúdo gravado vem do formato
 * conferido, e não do valor informado pelo navegador, que não é confiável.
 */
@Component
public class ValidadorOrdemCompra {
    private static final int TAMANHO_MAXIMO_NOME = 255;

    public record ArquivoOrdemCompra(String fileName, String contentType, byte[] content) {}

    private enum Formato {
        PDF(".pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
        // Arquivos do Word 97-2003 são documentos OLE2, que começam sempre com estes 8 bytes.
        DOC(
                ".doc",
                "application/msword",
                new byte[] {
                    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
                }),
        // DOCX é um pacote ZIP, que começa com "PK" seguido de 0x03 0x04.
        DOCX(
                ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {'P', 'K', 0x03, 0x04});

        private final String extensao;
        private final String contentType;
        private final byte[] assinatura;

        Formato(String extensao, String contentType, byte[] assinatura) {
            this.extensao = extensao;
            this.contentType = contentType;
            this.assinatura = assinatura;
        }

        static Optional<Formato> pelaExtensao(String nomeArquivo) {
            String nome = nomeArquivo.toLowerCase(Locale.ROOT);
            return Arrays.stream(values()).filter(f -> nome.endsWith(f.extensao)).findFirst();
        }

        boolean conteudoCorresponde(byte[] conteudo) {
            return conteudo.length >= assinatura.length
                    && Arrays.equals(conteudo, 0, assinatura.length, assinatura, 0, assinatura.length);
        }
    }

    public ArquivoOrdemCompra validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw RegraNegocioException.dadosInvalidos("Anexe o arquivo da ordem de compra");
        }
        if (arquivo.getSize() > ValidadorDocumentoOrcamento.TAMANHO_MAXIMO_BYTES) {
            throw new DocumentoOrcamentoMuitoGrandeException(
                    "A ordem de compra deve ter no máximo 50 MiB");
        }

        String nomeArquivo = nomeSemCaminho(arquivo.getOriginalFilename());
        Formato formato =
                Formato.pelaExtensao(nomeArquivo)
                        .orElseThrow(
                                () ->
                                        RegraNegocioException.dadosInvalidos(
                                                "A ordem de compra deve ser um arquivo PDF, DOC ou DOCX"));

        byte[] conteudo = ler(arquivo);
        if (!formato.conteudoCorresponde(conteudo)) {
            throw RegraNegocioException.dadosInvalidos(
                    "O conteúdo da ordem de compra não corresponde a um arquivo "
                            + formato.name());
        }
        return new ArquivoOrdemCompra(nomeArquivo, formato.contentType, conteudo);
    }

    // Alguns navegadores enviam o caminho completo da máquina do cliente; guardamos só o nome.
    private String nomeSemCaminho(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            throw RegraNegocioException.dadosInvalidos("O arquivo da ordem de compra não tem nome");
        }
        int ultimaBarra = Math.max(nomeOriginal.lastIndexOf('/'), nomeOriginal.lastIndexOf('\\'));
        String nome = nomeOriginal.substring(ultimaBarra + 1).trim();
        if (nome.isEmpty()) {
            throw RegraNegocioException.dadosInvalidos("O arquivo da ordem de compra não tem nome");
        }
        if (nome.length() > TAMANHO_MAXIMO_NOME) {
            throw RegraNegocioException.dadosInvalidos(
                    "O nome do arquivo da ordem de compra deve ter no máximo 255 caracteres");
        }
        return nome;
    }

    private byte[] ler(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler a ordem de compra", exception);
        }
    }
}
