package br.com.socre.producao.producao;
public enum Situacao {
    PENDENTE("Pendente"), VALIDADO("Validado"), CANCELADO("Cancelado");
    private final String descricao;
    Situacao(String descricao) { this.descricao = descricao; }
    public String getDescricao() { return descricao; }
}
