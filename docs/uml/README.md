# Diagramas UML — acompanhamento da produção cerâmica

Os arquivos desta pasta são uma proposta inicial editável para orientar o desenvolvimento do MVP. A base é o **Documento de Requisitos v1.7 (parcial)** e o protótipo visual do projeto. Os documentos originais e suas extrações ficam fora do repositório por conterem contexto interno. Os diagramas documentam também funcionalidades futuras; o [README principal](../../README.md) indica o que já está implementado.

O modelo contempla o piloto de Carolina e permite novas unidades. Vendas, finanças, estoque, migração das planilhas, rastreamento do retorno do Cru e índice de perda por fornada ficam fora do escopo definido no documento.

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

Se quiser reduzir o conjunto para começar a programar, priorize **01, 02, 03, 04, 05 e 07**. Os demais detalham BI, fornadas e arquitetura. Não há necessidade inicial de diagramas de objetos, comunicação ou tempo para este MVP. Um DER pode complementar o diagrama de classes quando o banco for escolhido; ele não é um tipo de UML.

## Decisões adotadas para tornar a proposta implementável

Estas escolhas estão identificadas também nos diagramas. Você pode alterá-las diretamente nos `.puml`.

1. **Um lançamento contém os dois formatos.** Cabeçalho único com data, unidade, etapa e fornada; um detalhe para 6 Furos e outro para 8 Furos, incluindo zeros. Isso acompanha o formulário do protótipo e evita criar dois lançamentos que violariam a RN09. Na Desenforna, cada formato tem três quantidades: 1ª qualidade, 2ª qualidade e Cru. Nas demais etapas, usa-se uma quantidade sem classificação.
2. **As linhas pertencem ao formato.** Elas não se repetem por classificação. Somá-las após juntar as três classificações duplicaria ou triplicaria o indicador. Maromba não tem linhas nem fornada; Enforna e Desenforna exigem ambos.
3. **Unicidade também na Maromba.** A chave é unidade + data + etapa + fornada. Como a fornada não existe na Maromba, o banco precisa tratar esse caso explicitamente: índice parcial, `NULLS NOT DISTINCT` ou mecanismo equivalente da tecnologia escolhida. Não basta presumir que um índice único comum bloqueará valores nulos repetidos.
4. **Histórico preservado.** O registro operacional contém os valores atuais. A auditoria guarda os valores originais da criação e cada diferença posterior, permitindo reconstruir o histórico. Negócio e auditoria são gravados juntos, em transação. Nenhum perfil pode editar/apagar a trilha; redefinição de senha registra a ação, sem guardar senha ou hash nela.
5. **Concorrência controlada.** O atributo técnico `versao` e o bloqueio na transação evitam uma edição silenciosamente sobrepor outra ou o Supervisor editar um registro validado enquanto sua tela estava aberta. A gravação de lançamento e o fechamento de fornada devem compartilhar o controle de concorrência.
6. **Acesso administrativo conservador.** A descrição dos atores exclui o Administrador das consultas de produção, mas a matriz das interfaces marca dashboard para ele. Adotei a descrição dos atores: Administrador acessa cadastros. O Gestor de Produção acessa dashboard, conforme a matriz e o protótipo. Ajuste o arquivo 01 se decidir outra permissão.
7. **Fornada criada aberta.** Gestor ou Administrador podem cadastrar, conforme RF34/RF26; o cadastro já cria a fornada Aberta, como no protótipo. O fechamento operacional fica com o Gestor, seguindo RF34 e o dicionário. Não modelei reabertura nem exclusão. O texto “manutenção” pelo Administrador não foi interpretado como autorização de fechamento.
8. **Correção após validação.** O Gestor pode corrigir um Validado e ele continua Validado, com auditoria. O documento propõe esse comportamento, mas ainda o lista como ponto em aberto. O Supervisor só corrige o próprio Pendente da sua unidade.
9. **Correção com fornada fechada.** Propus permitir corrigir os registros já vinculados à fornada fechada, respeitando a permissão sobre o lançamento. Se a correção mudar fornada, unidade ou etapa, deve validar o novo contexto e exigir fornada aberta quando aplicável. Essa exceção para vínculo histórico não está definida expressamente nos requisitos.
10. **Indicadores e presença de registro usam consultas diferentes.** Totais oficiais consideram só Validados. Para RF33, um Pendente já conta como dia com lançamento. Adotei presença por unidade e período, independentemente dos filtros de etapa/formato; o aviso não significa produção zero nem ausência de validação. Em seleção de várias unidades, apure as lacunas de cada unidade separadamente.
11. **Conversão sem alterar a origem.** As quantidades persistidas ficam em tijolos; agregações e conversão para milheiros pertencem à camada de apresentação/visualização, que pode executar no servidor. Nunca somar as três etapas entre si; produção acabada é a Desenforna de 1ª + 2ª, deixando Cru separado.
12. **Aviso de Enforna.** Por formato, `esperado = linhas × capacidade_por_linha`; avisar se `abs(informado − esperado) > 0,02 × esperado`. Valores de referência: 2.200 tijolos por linha para 6 Furos e 1.610 para 8 Furos. O aviso permite confirmar e continuar. Para esperado zero e informado positivo, propus avisar, sem dividir por zero. Não aplicar à Desenforna.
13. **Arquitetura provisória.** Componentes representam módulos lógicos de uma aplicação, sem exigir microsserviços ou uma linguagem específica. Autorização e regras são verificadas no servidor. O diagrama de implantação usa hospedagem e banco em nuvem, sem escolher fornecedor.

## Pontos que merecem ajuste antes da implementação final

| Ponto em aberto | Tratamento nos diagramas |
| --- | --- |
| Estado Cancelado e justificativa | Não incluídos; cancelar o formulário antes de salvar não é cancelar um registro existente. |
| Validação em lote no protótipo | Não incluída; o próprio documento informa que ainda precisa virar requisito. |
| Capacidade de 46 ou 72 linhas | Atributo configurável da unidade, sem valor fixado. Confirmar antes de calcular RF28. |
| RF28: taxa de ocupação | Opcional, pois é desejável. O documento não define o recorte temporal. Há proposta provisória por lançamento/etapa, somando linhas dos formatos uma única vez e dividindo pela capacidade positiva. Movimentação acumulada não mede ocupação instantânea; não somar várias datas ou entrada e saída como ocupação atual. |
| Mudança de capacidade ao longo do tempo | O modelo contém a capacidade atual. Se for necessário reproduzir taxas históricas com o denominador da época, acrescentar histórico de capacidade ou cópia da capacidade usada no cálculo. Essa regra ainda precisa ser definida. |
| Capacidade por linha e identificação da carga | Valores de RF25 usados como referência; confirmar variações por empilhamento e rótulo da fornada por lado/data. Código automático é sugestão do protótipo, não requisito fechado. |
| Existência de turno noturno | Mantida a unicidade diária da RN09. Se houver mais de um registro por turno, rever a chave e o modelo. |
| Dia sem produção e lançamento todo zero | RN03 exige alguma quantidade positiva; assim, um dia realmente sem produção não pode ser registrado como lançamento todo zero. RF33 deve dizer “sem registro”, sem concluir erro ou produção zero. |
| Devolução de Cru a outra fornada | Quantidade registrada, sem rastrear o vínculo de retorno, conforme delimitação do MVP. |
| Inativação de usuários/unidades | Preserva histórico. Propus impedir novas operações com conta/unidade inativa; definir também o efeito exato da inativação sobre sessões já abertas. |

## Rastreabilidade principal

| Diagrama | Requisitos e regras principais |
| --- | --- |
| 01 — Casos de uso | Atores; RF01–RF34, respeitando as prioridades de RF21, RF27 e RF28. |
| 02 — Classes | Dicionário; RF04, RF10, RF13, RF14, RF18, RF22, RF23, RF26, RF32, RF34; RN01–RN03, RN06, RN08, RN09; RNF08–RNF11, RNF13, RNF14. |
| 03/04 — Registro | RF04–RF14, RF25; RN02, RN03, RN06, RN08, RN09; RNF07. |
| 05/07 — Correção, validação e estados | RF16–RF18, RF30; RN01, RN04, RN11; RNF10, RNF11. |
| 06 — Indicadores | RF19–RF21, RF24, RF27, RF28, RF33; RN04, RN05, RN07, RN10. |
| 08 — Fornada | RF09, RF26, RF34; RN06, RN08; dicionário de fornadas. |
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
