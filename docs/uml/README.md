# Diagramas UML — acompanhamento da produção cerâmica

Os arquivos desta pasta são uma proposta inicial editável para orientar o desenvolvimento do MVP. A base é o **Documento de Requisitos v1.7 (parcial)** e o protótipo visual do projeto. Os documentos originais e suas extrações ficam fora do repositório por conterem contexto interno. Os diagramas documentam também funcionalidades futuras; o [README principal](../../README.md) indica o que já está implementado.

O modelo contempla o piloto de Carolina e permite novas unidades. A revisão de outubro/2026, feita após o levantamento na cerâmica, incorporou ao modelo o **movimento do pátio**, o **inventário inicial**, a **carga histórica**, a **validação em lote**, o **cancelamento com motivo**, a **fornada como ciclo de queima** com até duas em andamento e os **valores de referência configuráveis**. Finanças, rastreamento do retorno do Cru e índice de perda por fornada continuam fora do escopo. As vendas e o estoque de produto acabado foram adiados em 12/10/2026 e aparecem marcados como adiados. Na mesma data, o perfil Supervisor de Produção passou a se chamar **Registro de Produção**, porque quem digita é a secretária. As decisões de 27 e 28/10/2026 tiraram todo bloqueio do registro (só avisos), deixaram de controlar o Cru que fica no forno, trocaram o estoque do pátio por movimento, porque a cerâmica não conta o tijolo em secagem, e fixaram o inventário inicial em 13/08/2026. Em 30/10/2026, as etapas passaram a ter tabelas próprias (**Maromba**, **Enforna** e **Desenforna**), cada registro com um formato em um dia; o modelo lógico do banco está em [`docs/modelo-banco-socre.html`](../modelo-banco-socre.html) e os scripts de estudo em [`docs/postgresql`](../postgresql) e [`docs/sqlserver`](../sqlserver). As regras completas estão em `docs/REGRAS_NEGOCIO_REVISADAS.md`, mantido fora do repositório.

## Quais diagramas fazer e por quê

Recomendo **sete tipos de UML, distribuídos em dez diagramas pequenos**. Comece pelos casos de uso e pelas classes; depois use os fluxos e estados para implementar as regras. Componentes e implantação ajudam a organizar o código e escolher a hospedagem.

| Tipo | Para que ajuda no desenvolvimento | Código PlantUML |
| --- | --- | --- |
| Casos de uso | Delimita as funcionalidades e as permissões dos quatro perfis. | [01 — Casos de uso](01_casos_de_uso.puml) |
| Classes de domínio | Define entidades, atributos, relações e cardinalidades; serve de base ao banco e às regras. | [02 — Classes](02_classes_dominio.puml) |
| Atividade | Orienta o formulário guiado, os campos condicionais, os avisos e as saídas de erro. | [03 — Registrar produção](03_atividade_registrar_producao.puml) |
| Sequência | Especifica como tela, aplicação e banco colaboram nos fluxos críticos. | [04 — Registrar](04_sequencia_registrar_producao.puml), [05 — Corrigir/validar](05_sequencia_corrigir_validar.puml), [06 — Indicadores](06_sequencia_consultar_indicadores.puml) |
| Estados | Esclarece transições permitidas de lançamento e fornada. | [07 — Lançamento](07_estados_lancamento.puml), [08 — Fornada](08_estados_fornada.puml) |
| Componentes | Organiza responsabilidades e dependências entre os módulos da aplicação. | [09 — Componentes](09_componentes.puml) |
| Implantação | Mostra onde o sistema executa e como os dispositivos acessam a aplicação e o banco. | [10 — Implantação](10_implantacao.puml) |

Se quiser reduzir o conjunto para começar a programar, priorize **01, 02, 03, 04, 05 e 07**. Os demais detalham BI, fornadas e arquitetura. Não há necessidade inicial de diagramas de objetos, comunicação ou tempo para este MVP. O modelo lógico do banco (DER) complementa o diagrama de classes: [`docs/modelo-banco-socre.html`](../modelo-banco-socre.html). Ele não é um tipo de UML.

## Decisões adotadas para tornar a proposta implementável

Estas escolhas estão identificadas também nos diagramas. Você pode alterá-las diretamente nos `.puml`.

1. **Uma tabela por etapa, um registro por formato e dia.** Maromba, Enforna e Desenforna têm tabelas próprias, com os campos comuns (data, autor, validador, situação, origem, versão) repetidos e os números de cada etapa: Maromba guarda a quantidade; Enforna, linhas, tijolos por linha e quantidade; Desenforna, linhas, 1ª, 2ª e Cru. A tela continua mostrando os formatos ativos juntos, como no protótipo e no relatório diário da cerâmica; ao salvar, cada formato com quantidade maior que zero vira um registro, e o formato sem produção não gera registro (RN03). No diagrama de classes, as três etapas herdam de `RegistroEtapa`.
2. **Linhas e fornada só onde existem.** Maromba não tem linhas nem fornada e se liga à unidade; Enforna e Desenforna exigem ambos e chegam à unidade pela fornada. Na Desenforna, 1ª, 2ª e Cru são colunas do mesmo registro, então as linhas aparecem uma vez só e não se multiplicam pela classificação.
3. **Unicidade por etapa (RN09).** Maromba: unidade + formato + data. Enforna e Desenforna: fornada + formato + data. As três chaves valem só entre registros não cancelados (índice único parcial), então cancelar libera um novo registro com a mesma chave.
4. **Histórico preservado.** O registro operacional contém os valores atuais. A auditoria guarda os valores originais da criação e cada diferença posterior, permitindo reconstruir o histórico. Ela identifica o registro por `tabela` + `id_registro`, porque aponta para três tabelas diferentes, e guarda o motivo de correções e cancelamentos. Negócio e auditoria são gravados juntos, em transação. Nenhum perfil pode editar/apagar a trilha; redefinição de senha registra a ação, sem guardar senha ou hash nela.
5. **Concorrência controlada.** O atributo técnico `versao` e o bloqueio na transação evitam uma edição silenciosamente sobrepor outra ou o Registro de Produção editar um registro validado enquanto sua tela estava aberta. A gravação de lançamento, a marcação de "Enforna concluída" e a conclusão de fornada devem compartilhar o controle de concorrência.
6. **Administrador com leitura de tudo.** Confirmado no levantamento: o Administrador consulta produção, indicadores e estoques, mas escreve apenas cadastros, valores de referência, inventário inicial e carga histórica (RN05).
7. **Fornada como ciclo de queima.** A fornada nasce quando o Registro de Produção escolhe "Nova fornada" na primeira Enforna do ciclo; o sistema gera o número sequencial do mês por unidade (RN06). As fases Enfornando, Esquente, Queima e Queimada seguem os horários registrados (RN21), e o forno não precisa estar cheio para o esquente começar. Até duas fornadas ficam em andamento ao mesmo tempo: a anterior sendo desenfornada e a seguinte sendo enfornada nas linhas já esvaziadas (RN08). Quando a fornada termina de ser enchida, o Registro de Produção marca "Enforna concluída"; a partir daí ela não recebe Enforna, e só então o sistema oferece "Nova fornada". A conclusão é manual pelo Gestor de Produção, com sugestão do sistema quando as linhas desenfornadas alcançam as enfornadas; o sistema mostra a diferença entre o enfornado e o desenfornado, para mais ou para menos, sem exigir justificativa. Não há reabertura nem exclusão.
8. **Correção após validação.** O Gestor pode corrigir um Validado e ele continua Validado, com auditoria. O Registro de Produção só corrige o próprio Pendente da sua unidade. Toda correção exige motivo de uma lista configurável (RN15).
9. **Correção com fornada concluída.** Propus permitir corrigir os registros já vinculados à fornada concluída, respeitando a permissão sobre o lançamento. Se a correção mudar fornada, unidade ou etapa, deve validar o novo contexto e exigir fornada em andamento quando aplicável.
10. **Indicadores e presença de registro usam consultas diferentes.** Totais oficiais consideram só Validados. Para RF33, um Pendente já conta como dia com lançamento. Adotei presença por unidade e período, independentemente dos filtros de etapa/formato; o aviso não significa produção zero nem ausência de validação. Em seleção de várias unidades, apure as lacunas de cada unidade separadamente.
11. **Conversão sem alterar a origem.** As quantidades persistidas ficam em tijolos; agregações e conversão para milheiros pertencem à camada de apresentação/visualização, que pode executar no servidor. Nunca somar as três etapas entre si; produção acabada é a Desenforna de 1ª + 2ª, deixando Cru separado.
12. **Quantidade sugerida pelas linhas.** Na Enforna, a cerâmica não conta tijolo a tijolo: conta as linhas. O sistema sugere `linhas × tijolos por linha`, com a referência do formato (2.200 para 6 Furos e 1.610 para 8 Furos, configuráveis); a quantidade por linha e o total podem ser alterados naquele lançamento, e o lançamento grava o valor usado (RN12, RN13). Valores fora do esperado, inclusive Desenforna acima do enfornado na fornada, geram aviso com confirmação de um toque, nunca bloqueio; o Gestor vê os avisos na validação (RN14). O aviso de ±2% foi removido.
13. **Cancelamento com motivo.** Novo estado final Cancelado: sai de indicadores e do movimento do pátio, permanece para consulta e não bloqueia novo lançamento com a mesma chave (RN04, RN09, RN16).
14. **Pátio com movimento, não estoque.** O Gestor de Produção confirmou que o tijolo que vai secar no pátio não é contado nem informado. Por isso o sistema mostra apenas o movimento do pátio, Maromba − Enforna por formato e período, só com Validados, sem saldo nem contagem (RN19). O produto acabado (saldo + 1ª e 2ª da Desenforna − vendas) continua adiado com as vendas (RN18). Só o Cru que sai do forno é registrado; o que fica para nova queima não é controlado e sai depois como 1ª ou 2ª (RN10).
15. **Inventário inicial em 13/08/2026.** É o primeiro momento com "Status do Forno" completo, linha por linha. O inventário registra as fornadas que estavam no forno e o produto acabado do "Controle de Estoque" do dia; o pátio fica de fora. Lançamentos anteriores são recusados, e a carga histórica parte dessa data (RN20, RN22).
16. **Arquitetura.** Componentes representam módulos lógicos de uma aplicação, sem exigir microsserviços. A implementação atual usa Java, Spring Boot e PostgreSQL. Autorização e regras são verificadas no servidor. O diagrama de implantação usa hospedagem e banco em nuvem, sem escolher fornecedor.

## Pontos que merecem ajuste antes da implementação final

| Ponto em aberto | Tratamento nos diagramas |
| --- | --- |
| Registro de vendas (*) | Modelado como total diário por formato e qualidade, na data da nota assinada. Decidido em 12/10: vendas ficam fora por enquanto, e o estoque de produto acabado (RN18) foi adiado. |
| Carga histórica | Decidido em 12/10: os lançamentos importados entram como Validados. Em 28/10, o início passou a 13/08/2026, data do inventário inicial (RN20, RN22). |
| Movimento do pátio com lançamentos pendentes | Decidido em 12/10: usa só Validados, pois a validação é diária (RN19). |
| Cru | O Cru que sai do forno é registrado todo dia, como no relatório da cerâmica, e volta para a matéria-prima ou é descartado: fica fora da produção acabada. O Cru que fica no forno não é registrado (decidido em 27/10): pode ser parte de uma linha ou a linha inteira, ninguém anota esse número, e ele sai depois como 1ª ou 2ª (RN10). Como a Enforna reconta esse Cru, o movimento do pátio é aproximado. |
| Validação em lote | Aprovada em 12/10 com baixa prioridade; cada lançamento validado mantém sua própria auditoria. |
| Capacidade do forno | Carolina: 46 linhas, com as linhas 1 e 46 vagas. Atributo configurável da unidade. |
| RF28: taxa de ocupação | Opcional, pois é desejável. O documento não define o recorte temporal. Há proposta provisória por lançamento/etapa, somando linhas dos formatos uma única vez e dividindo pela capacidade positiva. Movimentação acumulada não mede ocupação instantânea; não somar várias datas ou entrada e saída como ocupação atual. |
| Mudança de capacidade ao longo do tempo | O modelo contém a capacidade atual. Se for necessário reproduzir taxas históricas com o denominador da época, acrescentar histórico de capacidade ou cópia da capacidade usada no cálculo. Essa regra ainda precisa ser definida. |
| Existência de turno noturno | Há turno noturno só para cuidar do fogo; as contagens continuam uma por dia. Mantida a unicidade diária da RN09. |
| Dia sem produção e lançamento todo zero | RN03 exige alguma quantidade positiva; assim, um dia realmente sem produção não pode ser registrado como lançamento todo zero. RF33 deve dizer “sem registro”, sem concluir erro ou produção zero. |
| Inativação de usuários/unidades | Preserva histórico. Propus impedir novas operações com conta/unidade inativa; definir também o efeito exato da inativação sobre sessões já abertas. |

## Rastreabilidade principal

| Diagrama | Requisitos e regras principais |
| --- | --- |
| 01 — Casos de uso | Atores; RF01–RF34, respeitando as prioridades de RF21, RF27 e RF28; RN05, RN08, RN13–RN16, RN19–RN22. |
| 02 — Classes | Dicionário; RF04, RF10, RF13, RF14, RF18, RF22, RF23, RF26, RF32, RF34; RN01–RN03, RN06, RN08–RN10, RN12, RN13, RN15, RN16, RN20, RN22; RNF08–RNF11, RNF13, RNF14. |
| 03/04 — Registro | RF04–RF14, RF25; RN02, RN03, RN06, RN08–RN10, RN12, RN14, RN17, RN20; RNF07. |
| 05/07 — Correção, cancelamento, validação e estados | RF16–RF18, RF30; RN01, RN04, RN11, RN15, RN16, RN22; validação em lote; RNF10, RNF11. |
| 06 — Indicadores | RF19–RF21, RF24, RF27, RF28, RF33; RN04, RN05, RN07, RN10, RN19. |
| 08 — Fornada | RF09, RF26, RF34; RN06, RN08, RN20, RN21; dicionário de fornadas. |
| 09/10 — Arquitetura | RNF03–RNF14 aplicáveis; restrições de software, hardware e conectividade. |

Os diagramas não comprovam metas de usabilidade/desempenho: os 15 toques, 90 segundos de registro e 1 segundo de processamento ainda precisam ser verificados na implementação e no piloto. O mesmo vale para os critérios de avaliação do TCC.

## Como editar e visualizar

Cada arquivo é independente: copie o conteúdo inteiro, de `@startuml` a `@enduml`, para seu editor PlantUML, ou abra o próprio `.puml`. Os comentários começam com `'`; notas com “proposta” indicam decisões ajustáveis. Os identificadores técnicos usam nomes sem acentos; os rótulos apresentados ao leitor estão em português.

As prévias SVG ficam em `svg/`: [casos de uso](svg/01_casos_de_uso.svg), [classes](svg/02_classes_dominio.svg), [atividade](svg/03_atividade_registrar_producao.svg), [sequência de registro](svg/04_sequencia_registrar_producao.svg), [sequência de correção/validação](svg/05_sequencia_corrigir_validar.svg), [sequência de indicadores](svg/06_sequencia_consultar_indicadores.svg), [estados de lançamento](svg/07_estados_lancamento.svg), [estados de fornada](svg/08_estados_fornada.svg), [componentes](svg/09_componentes.svg) e [implantação](svg/10_implantacao.svg).

Após alterar um `.puml`, gere novamente sua imagem. O motor Smetana está indicado nos diagramas estruturais para permitir renderização sem uma instalação separada de Graphviz, conforme a [documentação oficial](https://plantuml.com/smetana02).

Para gerar os SVGs localmente, baixe o JAR do PlantUML da distribuição oficial e informe seu caminho. A partir da raiz do projeto e com Java instalado:

```powershell
$env:PLANTUML_JAR = 'C:\ferramentas\plantuml.jar'
java -jar $env:PLANTUML_JAR -charset UTF-8 -tsvg -o svg 'docs\uml\*.puml'
```

O renderizador foi obtido da [distribuição oficial do PlantUML](https://plantuml.com/download). Os parâmetros de geração estão na [documentação de linha de comando](https://plantuml.com/command-line). Nenhum conteúdo do projeto precisa ser enviado a um servidor externo para renderizar localmente.
