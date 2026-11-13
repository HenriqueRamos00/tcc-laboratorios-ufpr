package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.InstrumentoLaboratorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentoLaboratorialRepository extends JpaRepository<InstrumentoLaboratorial, Long> {
    List<InstrumentoLaboratorial> findAllByOrderByNumeroPatrimonioAsc();
}
