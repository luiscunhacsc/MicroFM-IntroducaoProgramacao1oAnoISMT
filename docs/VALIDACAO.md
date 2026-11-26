# Validação da edição gráfica

Verificações executadas em 26 de novembro de 2026, em Windows 11 amd64, com Temurin Java 25.0.4.1. O código foi compilado com `--release 17` para produzir bytecode compatível com Java 17; o ambiente de execução testado foi Java 25.

| Verificação | Resultado |
| --- | --- |
| `TestCareer` | 1619 verificações: calendário, formações, treino, contratações, suplentes, classificação, épocas, UTF-8 e rejeição de gravações inválidas. |
| `TestMatch` | 24 encontros completos, repetição com sementes, 22 jogadores, intervalo, marcadores, cinco substituições, táticas e continuidade da bola a 60 Hz. |
| `microfm.ui.TestSwing` | 65 verificações no EDT: ações reais dos botões, navegação, treino, compra, pausa, 8×, intervalo, demonstração e nove PNG distintos. |
| `microfm.ui.TestNativeSwing --native` | 26 verificações com janelas reais: temporizador, resultado guardado ao fechar após o final, recuperação e gravação assíncronas. |
| Execução do JAR | As nove páginas foram renderizadas a partir de `MicroFM.jar`. |

O ensaio adicional do domínio percorreu 10 080 jogos e 1440 ciclos guardar/carregar, distribuídos por 24 carreiras de 30 épocas. Os testes usam pastas isoladas e não alteram uma carreira do utilizador.

## Medição de desenho

`Testar.cmd --benchmark` mede simulação e pintura em `BufferedImage`, com Java2D e antialiasing, no EDT. O cenário já está em memória. São 150 amostras por cenário; os cenários de jogo normal incluem 75 quadros de aquecimento. A celebração é medida a partir do primeiro golo. As medições oscilam entre execuções.

| Cenário | Mediana | Percentil 95 |
| --- | ---: | ---: |
| 900 × 500, simulação 1× | 3,70 ms | 4,62 ms |
| 1100 × 600, simulação 1× | 4,47 ms | 6,66 ms |
| 1100 × 600, simulação 8× | 2,47 ms | 6,51 ms |
| 1100 × 600, celebração com partículas | 4,94 ms | 23,77 ms |

Estes valores descrevem uma execução local de desenho fora do ecrã, não uma garantia de 60 imagens por segundo numa janela. Incluem efeitos de aquecimento e recolha de memória. As celebrações apresentam alguns quadros acima dos 16,67 ms de referência para 60 Hz. Equipamento, resolução, escala do ecrã e carga do sistema influenciam a experiência.

As pré-visualizações Java2D foram inspecionadas visualmente. A captura do ambiente de trabalho com `Robot` produziu imagens pretas neste ambiente Windows, pelo que o teste nativo verifica eventos e estado, sem alegar validação visual dessas capturas.
