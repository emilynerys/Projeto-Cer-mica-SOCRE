package br.com.socre.producao.producao;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MarombaRepository extends JpaRepository<Maromba, UUID> {
    List<Maromba> findByChaveEnvioOrderByFormatoNome(UUID chave);
    @Query("select count(m) > 0 from Maromba m where m.unidade.id = :unidade and m.formato.id = :formato and m.data = :data and m.situacao <> br.com.socre.producao.producao.Situacao.CANCELADO")
    boolean existeAtivo(UUID unidade, UUID formato, LocalDate data);
    @Query("select m from Maromba m where m.data between :inicio and :fim and (:autor is null or m.autor.id = :autor) and (:unidade is null or m.unidade.id = :unidade) and (:situacao is null or m.situacao = :situacao)")
    Page<Maromba> consultar(LocalDate inicio, LocalDate fim, UUID autor, UUID unidade, Situacao situacao, Pageable pagina);
    @Query("select m from Maromba m where m.data between :inicio and :fim and (:autor is null or m.autor.id = :autor) and (:unidade is null or m.unidade.id = :unidade) and (:situacao is null or m.situacao = :situacao)")
    List<Maromba> listar(LocalDate inicio, LocalDate fim, UUID autor, UUID unidade, Situacao situacao);
    interface TotalDiario {
        LocalDate getData(); UUID getFormato(); Long getQuantidade();
    }
    @Query("select m.data as data, m.formato.id as formato, sum(m.quantidade) as quantidade from Maromba m where m.situacao = br.com.socre.producao.producao.Situacao.VALIDADO and m.data between :inicio and :fim and (:unidade is null or m.unidade.id = :unidade) group by m.data, m.formato.id order by m.data")
    List<TotalDiario> totais(LocalDate inicio, LocalDate fim, UUID unidade);
    @Query("select distinct m.data from Maromba m where m.data between :inicio and :fim and m.unidade.id = :unidade and m.situacao <> br.com.socre.producao.producao.Situacao.CANCELADO")
    List<LocalDate> datasRegistradas(LocalDate inicio, LocalDate fim, UUID unidade);
}
