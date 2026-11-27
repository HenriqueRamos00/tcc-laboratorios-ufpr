package br.ufpr.tcc.backend_lab.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PatchMeuPerfilRequest {
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    private String name;

    @Size(max = 30, message = "Telefone deve ter no máximo 30 caracteres")
    private String phone;

    @Size(max = 120, message = "Cargo deve ter no máximo 120 caracteres")
    private String position;

    @JsonIgnore
    private final Set<String> suppliedFields = new LinkedHashSet<>();

    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        suppliedFields.add("name");
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
        suppliedFields.add("phone");
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
        suppliedFields.add("position");
    }

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    @JsonIgnore
    public Set<String> getSuppliedFields() {
        return suppliedFields;
    }

    @JsonIgnore
    public Map<String, Object> getUnsupportedFields() {
        return unsupportedFields;
    }
}
