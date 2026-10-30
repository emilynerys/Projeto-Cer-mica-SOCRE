package br.com.socre.producao.producao;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FornadaRepository extends JpaRepository<Fornada, UUID> {
    List<Fornada> findByUnidadeIdAndSituacaoOrderByMesAscNumeroAsc(UUID unidade, Fornada.Situacao situacao);
    @Query("select f from Fornada f where f.unidade.id = :unidade order by f.mes desc, f.numero desc")
    List<Fornada> recentes(UUID unidade, org.springframework.data.domain.Pageable pagina);
    @Query("select coalesce(max(f.numero), 0) from Fornada f where f.unidade.id = :unidade and f.mes = :mes")
    int ultimoNumero(UUID unidade, LocalDate mes);
}
