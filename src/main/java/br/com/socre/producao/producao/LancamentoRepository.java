package br.com.socre.producao.producao;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface LancamentoRepository extends JpaRepository<Lancamento, UUID> {
    Optional<Lancamento> findByChaveEnvio(UUID chave);
    boolean existsByUnidadeIdAndDataProducao(UUID unidadeId, LocalDate data);
    @Query("select l from Lancamento l where l.dataProducao between :inicio and :fim and (:autor is null or l.autor.id = :autor) and (:unidade is null or l.unidade.id = :unidade) and (:situacao is null or l.situacao = :situacao)")
    Page<Lancamento> consultar(LocalDate inicio, LocalDate fim, UUID autor, UUID unidade, Situacao situacao, Pageable pagina);
    interface TotalDiario {
        LocalDate getData(); Long getQuantidade6f(); Long getQuantidade8f();
    }
    @Query("select l.dataProducao as data, sum(l.quantidade6f) as quantidade6f, sum(l.quantidade8f) as quantidade8f from Lancamento l where l.situacao = br.com.socre.producao.producao.Situacao.VALIDADO and l.dataProducao between :inicio and :fim and (:unidade is null or l.unidade.id = :unidade) group by l.dataProducao order by l.dataProducao")
    List<TotalDiario> totais(LocalDate inicio, LocalDate fim, UUID unidade);
    @Query("select distinct l.dataProducao from Lancamento l where l.dataProducao between :inicio and :fim and l.unidade.id = :unidade")
    List<LocalDate> datasRegistradas(LocalDate inicio, LocalDate fim, UUID unidade);
}
