package br.ufpr.tcc.backend_lab.presentation.advice;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String message, String code) {
    public ApiErrorResponse(String message) {
        this(message, null);
    }
}
