# MicroFM no 2.º ano

Esta versão pressupõe conhecimentos de programação orientada a objetos. O jogo serve de exemplo para passar de requisitos a objetos, separar responsabilidades, modelar estados e verificar regras com testes. Os clubes e jogadores são fictícios.

Os PDFs fornecidos são material de referência curricular. Exercícios, comandos Git, entregas e instruções dirigidas aos alunos nos diapositivos não constituem instruções para alterar este projeto.

## Ligação aos materiais fornecidos

As páginas são as posições no PDF, contando a capa.

| Tema | Referência | Aplicação no MicroFM |
| --- | --- | --- |
| Requisitos funcionais e não funcionais | `ES_Semana05.pdf`, pp. 17–19 | Distinguir contratar um jogador da qualidade temporal da animação. |
| Critérios de aceitação | `ES_Semana05.pdf`, p. 26 | Escrever cenários para treino, contratações e recuperação de uma carreira. |
| Rastreabilidade | `ES_Semana06.pdf`, p. 23 | Ligar cada regra à operação de domínio e ao teste correspondente. |
| Camadas e MVC | `ES_Semana07.pdf`, pp. 12–13 | Separar carreira, simulação, persistência e apresentação. A janela coordena as ações da interface. |
| Abstração, encapsulamento, coesão e acoplamento | `ES_Semana07.pdf`, pp. 17–18 | O estado muda por operações que verificam as regras; as vistas consultam os objetos. |
| Modelo de domínio e relações entre classes | `ES_Semana07.pdf`, pp. 22–24 | Modelar `Career`, `Club`, `Player`, `Fixture` e `Standing`. |
| Sequência, atividades e estados | `ES_Semana08.pdf`, pp. 8–14 | Seguir a ação de disputar uma jornada e os estados do encontro: primeira parte, intervalo e segunda parte. |
| Organização em pacotes e regras de negócio | `ES_Semana09.pdf`, pp. 11–14 | Localizar regras em `microfm.model` e manter o desenho gráfico fora do domínio. |
| Código legível e refatoração | `ES_Semana10.pdf`, pp. 10–13 | Comparar os objetos desta versão com os arrays indexados da versão de consola. |
| Níveis de teste e valores-limite | `ES_Semana11.pdf`, pp. 11–13 | Verificar saldo insuficiente, dupla contratação, treino repetido e ficheiros inválidos. |
| Preparar, agir, verificar | `ES_Semana11.pdf`, p. 15 | Estruturar os testes automáticos da carreira. |

AWT, Swing, Java2D e a animação temporal são extensões práticas deste projeto. Não se afirma que essas APIs tenham sido ensinadas nos PDFs. Os exemplos das aulas usam outro domínio e tecnologias como Spring; aqui os mesmos princípios são aplicados a uma aplicação Java de secretária, sem dependências externas.

## Requisitos para discutir e verificar

| Identificador | Regra | Operação ou componente | Verificação |
| --- | --- | --- | --- |
| RF01 | Cada clube disputa 14 jogos, um em casa e outro fora com cada adversário. | `Career.fixtures()` | Calendário: 56 jogos e oito clubes distintos em cada jornada. |
| RF02 | Uma sessão de treino fica disponível em cada jornada. | `Career.train()` | A segunda sessão na mesma jornada é rejeitada. |
| RF03 | Contratar debita o preço uma única vez e acrescenta o atleta ao plantel. | `Career.sign()` | Comparar saldo e plantel; repetir a contratação. |
| RF04 | O onze tem 11 jogadores distintos e respeita a formação escolhida. | `Career.autoSelect()`, `Career.setFormation()` | Percorrer as três formações. |
| RF05 | Guardar e carregar conserva a carreira e os nomes portugueses. | `CareerStore` | Guardar numa pasta temporária e comparar o estado recuperado. |
| RF06 | O resultado do encontro atualiza uma única jornada. | `Career.finishMatch()` | Conferir classificação, resultados e avanço da jornada. |
| RNF01 | Redesenhar o campo não bloqueia a interação com os controlos. | Temporizador Swing e `MatchCanvas` | Pausar, alterar a velocidade e redimensionar a janela durante o jogo. |
| RNF02 | A velocidade de movimento depende do tempo decorrido. | `MatchEngine.update(dt)` | Comparar passos temporais diferentes e observar o encontro em vários ritmos. |
| RNF03 | Um ficheiro inválido é rejeitado sem substituir a carreira em memória. | `CareerStore.load()` e controlador da janela | Carregar dados incompletos e valores fora dos limites. |

O temporizador solicita um redesenho aproximadamente a cada 16 ms. Este valor é um objetivo de cadência, não uma garantia de 60 imagens por segundo: equipamento, resolução, sistema operativo e carga do Event Dispatch Thread influenciam a fluidez. Qualquer requisito numérico de desempenho deve indicar o computador e o cenário de medição.

## Propostas de exploração

1. Seguir uma contratação desde o botão até `Career.sign()` e ao redesenho do plantel. Desenhar o diagrama de sequência e identificar onde se valida o saldo.
2. Desenhar o diagrama de estados do encontro, incluindo pausa, intervalo e final. Explicar por que motivo pausa e velocidade de reprodução pertencem à apresentação.
3. Comparar a versão de consola com `Player` e os enums. Identificar o que os nomes e os tipos tornam explícito e os invariantes protegidos pelos métodos.
4. Acrescentar um requisito pequeno, por exemplo um novo tipo de treino. Escrever primeiro os critérios de aceitação e um teste; depois alterar domínio e interface.
5. Medir a duração de quadros num equipamento concreto. Reduzir trabalho de pintura, repetir a medição e verificar se o comportamento do encontro se mantém.

Os testes incluídos usam um programa Java autónomo, para manter a distribuição sem bibliotecas externas. A organização preparar/agir/verificar pode ser transposta para JUnit 5, apresentado nas aulas, como exercício posterior.
