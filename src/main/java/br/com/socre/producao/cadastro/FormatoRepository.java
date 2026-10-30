package br.com.socre.producao.cadastro;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FormatoRepository extends JpaRepository<Formato, UUID> {
    List<Formato> findByAtivoTrueOrderByNome();
}
