package br.ufpr.tcc.auth.auth.persistence;

import java.util.List;
import java.util.Optional;

import br.ufpr.tcc.auth.auth.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findAllByOrderByNameAscCreatedAtAsc();
}
