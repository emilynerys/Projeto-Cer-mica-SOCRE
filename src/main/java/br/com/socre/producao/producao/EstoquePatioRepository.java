package br.com.socre.producao.producao;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstoquePatioRepository extends JpaRepository<EstoquePatio, UUID> {
    List<EstoquePatio> findByUnidadeIdOrderByDataDescCriadoEmDesc(UUID unidade, Pageable pagina);
}
