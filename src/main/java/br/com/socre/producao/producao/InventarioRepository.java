package br.com.socre.producao.producao;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface InventarioRepository extends JpaRepository<InventarioInicial, UUID> {
    List<InventarioInicial> findByUnidadeIdOrderByEstadoAscFormatoNomeAsc(UUID unidade);
    List<InventarioInicial> findByFornadaId(UUID fornada);
    @Query("select min(i.dataHora) from InventarioInicial i where i.unidade.id = :unidade")
    Optional<Instant> referencia(UUID unidade);
}
