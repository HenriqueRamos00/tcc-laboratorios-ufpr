package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.domain.exception.DocumentoOrcamentoMuitoGrandeException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Component
public class ValidadorDocumentoOrcamento {
    public static final long TAMANHO_MAXIMO_BYTES = 50L * 1024 * 1024;
    private static final byte[] ASSINATURA_PDF = {'%', 'P', 'D', 'F', '-'};

    public byte[] validar(MultipartFile documento) {
        if (documento == null) {
            return null;
        }
        if (documento.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new DocumentoOrcamentoMuitoGrandeException(
                    "O PDF do orçamento deve ter no máximo 50 MiB");
        }

        String nomeArquivo = documento.getOriginalFilename();
        if (nomeArquivo == null
                || !nomeArquivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw RegraNegocioException.dadosInvalidos(
                    "O documento do orçamento deve ter extensão PDF");
        }

        byte[] conteudo;
        try {
            conteudo = documento.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o PDF do orçamento", exception);
        }

        if (!temAssinaturaPdf(conteudo)) {
            throw RegraNegocioException.dadosInvalidos(
                    "O documento do orçamento não possui uma assinatura PDF válida");
        }
        return conteudo;
    }

    private boolean temAssinaturaPdf(byte[] conteudo) {
        if (conteudo.length < ASSINATURA_PDF.length) {
            return false;
        }
        for (int indice = 0; indice < ASSINATURA_PDF.length; indice++) {
            if (conteudo[indice] != ASSINATURA_PDF[indice]) {
                return false;
            }
        }
        return true;
    }
}
