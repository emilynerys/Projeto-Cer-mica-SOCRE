package br.com.socre.producao.producao;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EnfornaRepository extends JpaRepository<Enforna, UUID> {
    List<Enforna> findByChaveEnvioOrderByFormatoNome(UUID chave);
    @Query("select count(e) > 0 from Enforna e where e.fornada.id = :fornada and e.formato.id = :formato and e.data = :data and e.situacao <> br.com.socre.producao.producao.Situacao.CANCELADO")
    boolean existeAtivo(UUID fornada, UUID formato, LocalDate data);
    @Query("select e from Enforna e where e.fornada.id = :fornada and e.situacao <> br.com.socre.producao.producao.Situacao.CANCELADO")
    List<Enforna> daFornada(UUID fornada);
    @Query("select e from Enforna e where e.data between :inicio and :fim and (:autor is null or e.autor.id = :autor) and (:unidade is null or e.fornada.unidade.id = :unidade) and (:situacao is null or e.situacao = :situacao)")
    List<Enforna> consultar(LocalDate inicio, LocalDate fim, UUID autor, UUID unidade, Situacao situacao);
}
