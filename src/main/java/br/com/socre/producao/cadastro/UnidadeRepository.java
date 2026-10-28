package br.com.socre.producao.cadastro;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UnidadeRepository extends JpaRepository<Unidade, UUID> {
    List<Unidade> findAllByOrderByNome();
}
