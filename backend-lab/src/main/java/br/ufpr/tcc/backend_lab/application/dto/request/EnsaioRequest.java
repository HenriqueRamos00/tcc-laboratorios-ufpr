package br.ufpr.tcc.backend_lab.application.dto.request;

import java.time.LocalDate;
import java.util.List;

public record EnsaioRequest(
        String sampleId,
        NewSampleRequest newSample,
        String type,
        String analysisType,
        String method,
        String standard,
        LocalDate scheduledDate,
        String responsibleId,
        List<String> equipmentIds,
        String analysisClassification,
        String reportText,
        List<MeasurementRequest> measurements,
        Long version) {

    public record NewSampleRequest(
            String quoteId,
            String code,
            String description,
            String analyzedEquipmentId) { }

    public record MeasurementRequest(
            String code,
            String name,
            String unit,
            String method,
            String referenceLimit,
            ResultRequest result) { }

    public record ResultRequest(String kind, String value, String text) { }
}
