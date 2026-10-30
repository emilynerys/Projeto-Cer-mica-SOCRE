package br.com.socre.producao.auditoria;
import java.util.*;
import org.springframework.data.repository.Repository;
public interface AuditoriaRepository extends Repository<EventoAuditoria, UUID> {
    EventoAuditoria save(EventoAuditoria evento);
    List<EventoAuditoria> findByTabelaAndIdRegistroOrderByDataHoraAsc(String tabela, UUID idRegistro);
}
