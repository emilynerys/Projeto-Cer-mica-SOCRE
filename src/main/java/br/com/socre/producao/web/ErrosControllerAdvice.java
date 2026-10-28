package br.com.socre.producao.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class ErrosControllerAdvice {
    @ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
    String negado(Model model) { model.addAttribute("mensagem", "Seu perfil não permite esta operação ou o registro não pertence a você."); return "erro"; }
}
