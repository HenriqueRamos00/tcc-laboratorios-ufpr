package br.ufpr.tcc.auth.auth.web;

import br.ufpr.tcc.auth.auth.setup.SetupService;
import br.ufpr.tcc.auth.auth.web.dto.SetupRequest;
import br.ufpr.tcc.auth.auth.web.dto.SetupStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
class SetupController {
    private final SetupService setup;

    SetupController(SetupService setup) {
        this.setup = setup;
    }

    @GetMapping("/status")
    SetupStatusResponse status() {
        return setup.status();
    }

    @PostMapping
    ResponseEntity<Void> create(@RequestHeader(value = "X-Setup-Token", required = false) String token,
                                @Valid @RequestBody SetupRequest request,
                                HttpServletRequest servletRequest) {
        setup.createFirstAdmin(token, clientAddress(servletRequest), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
