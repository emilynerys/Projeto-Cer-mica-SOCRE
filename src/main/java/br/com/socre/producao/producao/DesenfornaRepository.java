package br.com.socre.producao.producao;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DesenfornaRepository extends JpaRepository<Desenforna, UUID> {
    List<Desenforna> findByChaveEnvioOrderByFormatoNome(UUID chave);
    @Query("select count(d) > 0 from Desenforna d where d.fornada.id = :fornada and d.formato.id = :formato and d.data = :data and d.situacao <> br.com.socre.producao.producao.Situacao.CANCELADO")
    boolean existeAtivo(UUID fornada, UUID formato, LocalDate data);
    @Query("select d from Desenforna d where d.fornada.id = :fornada and d.situacao <> br.com.socre.producao.producao.Situacao.CANCELADO")
    List<Desenforna> daFornada(UUID fornada);
    @Query("select d from Desenforna d where d.data between :inicio and :fim and (:autor is null or d.autor.id = :autor) and (:unidade is null or d.fornada.unidade.id = :unidade) and (:situacao is null or d.situacao = :situacao)")
    List<Desenforna> consultar(LocalDate inicio, LocalDate fim, UUID autor, UUID unidade, Situacao situacao);
}
