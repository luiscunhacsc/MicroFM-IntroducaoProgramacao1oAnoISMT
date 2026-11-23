import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

// MicroFM | Java 25 | Programa compacto, como nos slides.
// Todo o estado nasce no main e chega aos outros métodos por parâmetros.
// carreira: clube, jornada, época, saldo, mentalidade, formação, moral,
//           treinoFeito, velocidade, animação, totalJogadores, substituições, demo.
// jogador: posição (0 GR / 1 DEF / 2 MED / 3 AVA), idade, qualidade,
//          condição, golos, jogos, lesão (jornadas ainda indisponível).
// tabela: pontos, jogos, vitórias, empates, derrotas, marcados, sofridos.
// calendário: casa, fora, golosCasa, golosFora (-1 = ainda não disputado).
// mercado: posição, idade, qualidade, preço, contratado (0/1).

void main() {
    String[] clubes = {"Académicos do Mondego", "Atlético de Lisboa", "União Invicta",
        "Vitória da Serra", "Estrela do Algarve", "Marítimos da Costa",
        "Ferroviários do Centro", "Lusitanos do Alentejo"};
    int[] carreira = new int[13];
    String[] info = {"", "Bem-vindo ao clube. A direção acredita em ti."};
    int[] forcas = new int[8];
    int[][] jogadores = new int[32][7];
    String[] nomes = new String[32];
    int[] onze = new int[11];
    int[][] tabela = new int[8][7];
    int[][] calendario = new int[56][4];
    int[][] mercado = new int[8][5];
    boolean aberto = true;
    while (aberto) {
        titulo("M I C R O F M   /   O teu clube. As tuas decisões.");
        IO.println("  LIGA PORTUGUESA FICTÍCIA | 8 clubes | 14 jornadas | Java 25");
        IO.println("\n  1  Nova carreira\n  2  Continuar carreira guardada\n  3  Ver um jogo de demonstração\n  0  Sair");
        int escolha = lerInteiro("\nEscolha: ", 0, 3);
        boolean entrar = false;
        if (escolha == 0) {
            aberto = false;
        } else if (escolha == 1) {
            String treinador = lerNome();
            titulo("ESCOLHE O TEU CLUBE");
            for (int i = 0; i < clubes.length; i++) {
                IO.println(String.format("  %d  %s", i + 1, clubes[i]));
            }
            int clube = lerInteiro("\nClube: ", 1, 8) - 1;
            iniciarCarreira(clube, treinador, carreira, info, forcas, jogadores, nomes,
                onze, tabela, calendario, mercado);
            entrar = true;
        } else if (escolha == 2) {
            entrar = carregar(carreira, info, forcas, jogadores, nomes, onze,
                tabela, calendario, mercado);
        } else {
            iniciarCarreira(0, "Visitante", carreira, info, forcas, jogadores, nomes,
                onze, tabela, calendario, mercado);
            carreira[12] = 1;
            titulo("DEMONSTRAÇÃO | sem pausas de decisão");
            IO.println("Equipa A contra equipa B. A bola é o. O jogo corre sozinho.");
            IO.println("Para mudar a velocidade e gerir a equipa, inicia uma carreira.");
            pausa();
            int[] resultado = jogarPartida(0, 7, clubes, forcas, carreira, info,
                jogadores, nomes, onze);
            mostrarResultado(0, 7, resultado, clubes);
            pausa();
        }
        if (entrar) {
            gerirClube(clubes, carreira, info, forcas, jogadores, nomes, onze,
                tabela, calendario, mercado);
        }
    }
    IO.println("\nAté ao próximo apito, mister!");
}

void titulo(String texto) {
    IO.println("\n========================================================================");
    IO.println("  " + texto);
    IO.println("========================================================================");
}

int lerInteiro(String pergunta, int minimo, int maximo) {
    while (true) {
        try {
            String texto = IO.readln(pergunta);
            // No fim da entrada, a opção mínima permite fechar os menus.
            if (texto == null) {
                return minimo;
            }
            int numero = Integer.parseInt(texto);
            if (numero >= minimo && numero <= maximo) {
                return numero;
            }
            IO.println("Escolhe um número entre " + minimo + " e " + maximo + ".");
        } catch (NumberFormatException e) {
            IO.println("Escreve um número inteiro, por exemplo 1.");
        }
    }
}

String lerNome() {
    String nome = IO.readln("\nNome do treinador (Enter = Mister): ");
    if (nome == null || nome.length() == 0) {
        return "Mister";
    }
    if (nome.length() > 30) {
        nome = nome.substring(0, 30);
    }
    return nome;
}

void pausa() {
    IO.readln("\nEnter para continuar...");
}

int limitar(int valor, int minimo, int maximo) {
    if (valor < minimo) { return minimo; }
    if (valor > maximo) { return maximo; }
    return valor;
}

String dinheiro(int euros) {
    return String.format("%,d EUR", euros);
}

String posicao(int numero) {
    return switch (numero) {
        case 0 -> "GR";
        case 1 -> "DEF";
        case 2 -> "MED";
        default -> "AVA";
    };
}

String mentalidade(int numero) {
    return switch (numero) {
        case 0 -> "Defensiva";
        case 1 -> "Equilibrada";
        default -> "Ofensiva";
    };
}

String formacao(int numero) {
    return switch (numero) {
        case 0 -> "4-3-3";
        case 1 -> "4-4-2";
        default -> "3-5-2";
    };
}

int posicaoDoLugar(int lugar, int desenho) {
    int defesas = 4;
    int medios = 3;
    if (desenho == 1) { medios = 4; }
    if (desenho == 2) { defesas = 3; medios = 5; }
    if (lugar == 0) { return 0; }
    if (lugar <= defesas) { return 1; }
    if (lugar <= defesas + medios) { return 2; }
    return 3;
}

void iniciarCarreira(int clube, String treinador, int[] carreira, String[] info,
        int[] forcas, int[][] jogadores, String[] nomes, int[] onze,
        int[][] tabela, int[][] calendario, int[][] mercado) {
    int[] inicio = {clube, 0, 1, 1800000, 1, 0, 72, 0, 2, 1, 20, 0, 0};
    for (int i = 0; i < carreira.length; i++) { carreira[i] = inicio[i]; }
    info[0] = treinador;
    info[1] = "A direção pede uma classificação na primeira metade da tabela.";
    int[] bases = {70, 73, 72, 71, 67, 68, 66, 65};
    for (int i = 0; i < forcas.length; i++) { forcas[i] = bases[i]; }
    String[] iniciais = {"Tomás Neves", "Rui Valente", "Diogo Serra", "João Matos",
        "André Ramos", "Miguel Costa", "Nuno Reis", "Tiago Lemos", "Pedro Faria",
        "Duarte Silva", "Afonso Maia", "Vasco Melo", "Bruno Pires", "Gonçalo Dias",
        "Rafael Luz", "Henrique Sá", "Salvador Cruz", "Martim Rocha", "Daniel Gama", "Ivo Paiva"};
    for (int i = 0; i < jogadores.length; i++) {
        nomes[i] = "";
        for (int j = 0; j < jogadores[i].length; j++) { jogadores[i][j] = 0; }
        if (i < 20) {
            nomes[i] = iniciais[i];
            int pos = 3;
            if (i < 2) { pos = 0; }
            else if (i < 8) { pos = 1; }
            else if (i < 15) { pos = 2; }
            jogadores[i][0] = pos;
            jogadores[i][1] = 18 + (int) (Math.random() * 16);
            jogadores[i][2] = limitar(forcas[clube] - 8 + (int) (Math.random() * 17), 45, 90);
            jogadores[i][3] = 100;
        }
    }
    limparTabela(tabela);
    criarCalendario(calendario);
    renovarMercado(mercado, 1);
    escolherMelhorOnze(carreira, jogadores, onze);
}

void limparTabela(int[][] tabela) {
    for (int i = 0; i < tabela.length; i++) {
        for (int j = 0; j < tabela[i].length; j++) { tabela[i][j] = 0; }
    }
}

// Método da roda: fixa o clube 0 e roda os restantes sete.
void criarCalendario(int[][] calendario) {
    int[] roda = {0, 1, 2, 3, 4, 5, 6, 7};
    for (int jornada = 0; jornada < 7; jornada++) {
        for (int jogo = 0; jogo < 4; jogo++) {
            int casa = roda[jogo];
            int fora = roda[7 - jogo];
            if (jornada % 2 == 1) {
                int temporario = casa; casa = fora; fora = temporario;
            }
            int indice = jornada * 4 + jogo;
            calendario[indice][0] = casa;
            calendario[indice][1] = fora;
            calendario[indice][2] = -1;
            calendario[indice][3] = -1;
            calendario[indice + 28][0] = fora;
            calendario[indice + 28][1] = casa;
            calendario[indice + 28][2] = -1;
            calendario[indice + 28][3] = -1;
        }
        int ultimo = roda[7];
        for (int i = 7; i > 1; i--) { roda[i] = roda[i - 1]; }
        roda[1] = ultimo;
    }
}

void gerirClube(String[] clubes, int[] carreira, String[] info, int[] forcas,
        int[][] jogadores, String[] nomes, int[] onze, int[][] tabela,
        int[][] calendario, int[][] mercado) {
    boolean noClube = true;
    while (noClube) {
        titulo("MICROFM / " + clubes[carreira[0]] + " / ÉPOCA " + carreira[2]);
        String semana = "ÉPOCA TERMINADA";
        if (carreira[1] < 14) {
            semana = "Jornada " + (carreira[1] + 1) + "/14";
        }
        IO.println(String.format("  %s | %s | Moral %d/100", info[0], semana, carreira[6]));
        IO.println("  Saldo " + dinheiro(carreira[3]) + " | " + formacao(carreira[5])
            + " | " + mentalidade(carreira[4]));
        IO.println("\n  CAIXA DE ENTRADA: " + info[1]);
        if (carreira[1] < 14) {
            int jogo = jogoDoClube(carreira[1], carreira[0], calendario);
            IO.println("  A SEGUIR: " + clubes[calendario[jogo][0]] + " x "
                + clubes[calendario[jogo][1]]);
        }
        IO.println("\n  1  Jogar jornada          2  Plantel e onze inicial");
        IO.println("  3  Táticas                4  Treino semanal");
        IO.println("  5  Mercado                6  Classificação");
        IO.println("  7  Calendário             8  Finanças e direção");
        IO.println("  9  Guardar               10  Preferências do jogo");
        IO.println(" 11  Começar nova época     0  Guardar e voltar ao início");
        int opcao = lerInteiro("\nDecisão: ", 0, 11);
        switch (opcao) {
            case 1 -> disputarJornada(clubes, carreira, info, forcas, jogadores,
                nomes, onze, tabela, calendario);
            case 2 -> gerirPlantel(carreira, jogadores, nomes, onze);
            case 3 -> taticas(carreira, jogadores, onze, true);
            case 4 -> treinar(carreira, info, jogadores);
            case 5 -> gerirMercado(carreira, info, jogadores, nomes, mercado);
            case 6 -> { mostrarTabela(clubes, tabela, carreira[0]); pausa(); }
            case 7 -> mostrarCalendario(clubes, carreira, calendario);
            case 8 -> financas(carreira, jogadores, nomes, clubes, tabela);
            case 9 -> guardar(carreira, info, forcas, jogadores, nomes, onze,
                tabela, calendario, mercado);
            case 10 -> preferencias(carreira);
            case 11 -> novaEpoca(carreira, info, jogadores, forcas, tabela,
                calendario, mercado);
            case 0 -> {
                boolean guardado = guardar(carreira, info, forcas, jogadores,
                    nomes, onze, tabela, calendario, mercado);
                if (guardado) { noClube = false; }
                else {
                    IO.println("A carreira continua aberta. Podes tentar guardar novamente.");
                    noClube = lerInteiro("1 Continuar / 0 Sair sem guardar: ", 0, 1) == 1;
                }
            }
        }
    }
}

int jogoDoClube(int jornada, int clube, int[][] calendario) {
    for (int i = jornada * 4; i < jornada * 4 + 4; i++) {
        if (calendario[i][0] == clube || calendario[i][1] == clube) { return i; }
    }
    return -1;
}

boolean titular(int jogador, int[] onze) {
    for (int numero : onze) { if (numero == jogador) { return true; } }
    return false;
}

void escolherMelhorOnze(int[] carreira, int[][] jogadores, int[] onze) {
    boolean[] escolhidos = new boolean[jogadores.length];
    for (int lugar = 0; lugar < onze.length; lugar++) {
        int melhor = -1;
        int nota = -10000;
        for (int j = 0; j < carreira[10]; j++) {
            if (!escolhidos[j] && jogadores[j][6] == 0) {
                int valor = jogadores[j][2] + jogadores[j][3] / 3;
                if (jogadores[j][0] != posicaoDoLugar(lugar, carreira[5])) { valor -= 40; }
                if (valor > nota) { melhor = j; nota = valor; }
            }
        }
        // Há sempre 20+ jogadores; lesões simultâneas são limitadas pelo motor.
        if (melhor < 0) {
            for (int j = 0; j < carreira[10]; j++) {
                if (!escolhidos[j]) { melhor = j; }
            }
        }
        onze[lugar] = melhor;
        escolhidos[melhor] = true;
    }
}

void mostrarPlantel(int[] carreira, int[][] jogadores, String[] nomes, int[] onze) {
    titulo("PLANTEL | Qualidade e condição de 1 a 100");
    IO.println("  Nº   Jogador                  Pos Idade  Qua Cond  G  J  Estado");
    for (int i = 0; i < carreira[10]; i++) {
        String estado = "Suplente";
        for (int j = 0; j < onze.length; j++) {
            if (onze[j] == i) { estado = "Onze #" + (j + 1); }
        }
        if (jogadores[i][6] > 0) { estado = "Lesão: " + jogadores[i][6] + " jornada(s)"; }
        IO.println(String.format("  %2d   %-24s %3s   %2d   %2d  %3d %2d %2d  %s",
            i + 1, nomes[i], posicao(jogadores[i][0]), jogadores[i][1],
            jogadores[i][2], jogadores[i][3], jogadores[i][4], jogadores[i][5], estado));
    }
    IO.println("\n  ONZE / " + formacao(carreira[5]));
    for (int i = 0; i < onze.length; i++) {
        String aviso = "";
        if (jogadores[onze[i]][0] != posicaoDoLugar(i, carreira[5])) {
            aviso = " [fora de posição: penalização de rendimento]";
        }
        IO.println(String.format("  %2d %-3s  %s%s", i + 1,
            posicao(posicaoDoLugar(i, carreira[5])), nomes[onze[i]], aviso));
    }
}

void gerirPlantel(int[] carreira, int[][] jogadores, String[] nomes, int[] onze) {
    boolean editar = true;
    while (editar) {
        mostrarPlantel(carreira, jogadores, nomes, onze);
        IO.println("\n  1 Melhor onze automático  /  2 Trocar um titular  /  0 Voltar");
        int escolha = lerInteiro("Escolha: ", 0, 2);
        if (escolha == 0) { editar = false; }
        if (escolha == 1) { escolherMelhorOnze(carreira, jogadores, onze); }
        if (escolha == 2) {
            int lugar = lerInteiro("Lugar do onze (1 a 11): ", 1, 11) - 1;
            int jogador = lerInteiro("Nº do jogador que entra: ", 1, carreira[10]) - 1;
            if (jogadores[jogador][6] > 0) {
                IO.println("Esse jogador está lesionado.");
            } else if (titular(jogador, onze)) {
                IO.println("Esse jogador já está no onze. Escolhe um suplente.");
            } else {
                onze[lugar] = jogador;
            }
        }
    }
}

void taticas(int[] carreira, int[][] jogadores, int[] onze, boolean podeFormacao) {
    titulo("QUADRO TÁTICO");
    IO.println("Defensiva: protege a baliza; menos oportunidades e menor desgaste.");
    IO.println("Equilibrada: compromisso entre criação, proteção e condição.");
    IO.println("Ofensiva: cria mais, expõe a defesa e cansa mais os jogadores.");
    carreira[4] = lerInteiro("0 Defensiva / 1 Equilibrada / 2 Ofensiva: ", 0, 2);
    if (podeFormacao) {
        int desenho = lerInteiro("0 4-3-3 / 1 4-4-2 / 2 3-5-2: ", 0, 2);
        if (desenho != carreira[5]) {
            carreira[5] = desenho;
            escolherMelhorOnze(carreira, jogadores, onze);
            IO.println("Onze reorganizado automaticamente para a nova formação.");
        }
    }
}

void intervalo(int[] carreira, String[] info, int[][] jogadores, String[] nomes,
        int[] onze, boolean[] usados, int[] stats) {
    if (carreira[12] == 1) { return; }
    boolean decidir = true;
    while (decidir) {
        titulo("BANCO | " + stats[2] + " min | " + stats[0] + " - " + stats[1]);
        IO.println("  " + mentalidade(carreira[4]) + " | Substituições " + carreira[11] + "/3");
        IO.println("  1 Continuar  /  2 Mudar mentalidade  /  3 Substituir  /  4 Ver equipa");
        int escolha = lerInteiro("Decisão: ", 1, 4);
        if (escolha == 1) { decidir = false; }
        if (escolha == 2) { taticas(carreira, jogadores, onze, false); }
        if (escolha == 4) { mostrarPlantel(carreira, jogadores, nomes, onze); }
        if (escolha == 3) {
            if (carreira[11] >= 3) {
                IO.println("Já usaste as três substituições.");
            } else {
                mostrarPlantel(carreira, jogadores, nomes, onze);
                int lugar = lerInteiro("Lugar do onze que sai (0 cancela): ", 0, 11) - 1;
                if (lugar >= 0) {
                    int entra = lerInteiro("Nº do suplente (0 cancela): ", 0, carreira[10]) - 1;
                    if (entra >= 0) {
                        if (usados[entra]) {
                            IO.println("Quem já participou não pode entrar novamente.");
                        } else if (jogadores[entra][6] > 0) {
                            IO.println("Esse jogador está lesionado.");
                        } else {
                            String sai = nomes[onze[lugar]];
                            onze[lugar] = entra;
                            usados[entra] = true;
                            carreira[11]++;
                            IO.println("Sai " + sai + "; entra " + nomes[entra] + ".");
                        }
                    }
                }
            }
        }
    }
}

void treinar(int[] carreira, String[] info, int[][] jogadores) {
    titulo("CENTRO DE TREINOS");
    if (carreira[1] >= 14) { IO.println("A época terminou. Começa uma nova época."); return; }
    if (carreira[7] == 1) { IO.println("O treino desta semana já foi realizado."); return; }
    IO.println("  1 Recuperação: +14 condição, +2 moral");
    IO.println("  2 Técnica: +1 qualidade, -8 condição");
    IO.println("  3 Espírito de equipa: +8 moral, -3 condição");
    IO.println("  0 Voltar sem treinar");
    int tipo = lerInteiro("Sessão: ", 0, 3);
    if (tipo == 0) { return; }
    for (int i = 0; i < carreira[10]; i++) {
        if (jogadores[i][6] == 0) {
            if (tipo == 1) { jogadores[i][3] = limitar(jogadores[i][3] + 14, 1, 100); }
            if (tipo == 2) {
                jogadores[i][2] = limitar(jogadores[i][2] + 1, 1, 99);
                jogadores[i][3] = limitar(jogadores[i][3] - 8, 1, 100);
            }
            if (tipo == 3) { jogadores[i][3] = limitar(jogadores[i][3] - 3, 1, 100); }
        }
    }
    if (tipo == 1) { carreira[6] = limitar(carreira[6] + 2, 0, 100); }
    if (tipo == 3) { carreira[6] = limitar(carreira[6] + 8, 0, 100); }
    carreira[7] = 1;
    info[1] = "Treino concluído. A próxima sessão fica disponível após a jornada.";
    IO.println(info[1]);
}

String nomeDoMercado(int indice) {
    String[] nomes = {"Leonardo Brito", "Simão Tavares", "Gustavo Vilar", "Francisco Leal",
        "Rodrigo Veiga", "David Monteiro", "Eduardo Seixas", "Lucas Falcão"};
    return nomes[indice];
}

void renovarMercado(int[][] mercado, int epoca) {
    int[] posicoes = {0, 1, 1, 2, 2, 3, 3, 2};
    for (int i = 0; i < mercado.length; i++) {
        // O mercado é um conjunto finito de oito atletas; não duplica os contratados.
        if (epoca == 1) {
            mercado[i][0] = posicoes[i];
            mercado[i][1] = 18 + (i * 3 + epoca) % 14;
            mercado[i][2] = 67 + i * 2;
            mercado[i][3] = 160000 + i * 85000;
            mercado[i][4] = 0;
        } else {
            mercado[i][1] = limitar(mercado[i][1] + 1, 18, 60);
        }
    }
}

boolean contratar(int numero, int[] carreira, int[][] jogadores,
        String[] nomes, int[][] mercado) {
    if (numero < 0 || numero >= mercado.length) { return false; }
    if (mercado[numero][4] == 1 || carreira[10] >= jogadores.length
            || carreira[3] < mercado[numero][3]) { return false; }
    int novo = carreira[10];
    nomes[novo] = nomeDoMercado(numero);
    jogadores[novo][0] = mercado[numero][0];
    jogadores[novo][1] = mercado[numero][1];
    jogadores[novo][2] = mercado[numero][2];
    jogadores[novo][3] = 100;
    jogadores[novo][4] = 0;
    jogadores[novo][5] = 0;
    jogadores[novo][6] = 0;
    carreira[3] -= mercado[numero][3];
    carreira[10]++;
    mercado[numero][4] = 1;
    return true;
}

void gerirMercado(int[] carreira, String[] info, int[][] jogadores,
        String[] nomes, int[][] mercado) {
    boolean comprar = true;
    while (comprar) {
        titulo("MERCADO | Saldo " + dinheiro(carreira[3]));
        IO.println("  Nº Jogador                  Pos Idade Qua   Preço");
        for (int i = 0; i < mercado.length; i++) {
            String preco = dinheiro(mercado[i][3]);
            if (mercado[i][4] == 1) { preco = "CONTRATADO"; }
            IO.println(String.format("  %d  %-24s %3s  %2d   %2d   %s", i + 1,
                nomeDoMercado(i), posicao(mercado[i][0]), mercado[i][1], mercado[i][2], preco));
        }
        IO.println("\nOs salários semanais sobem com cada reforço. Consulta as finanças.");
        int numero = lerInteiro("Contratar jogador (0 volta): ", 0, 8) - 1;
        if (numero < 0) { comprar = false; }
        else if (contratar(numero, carreira, jogadores, nomes, mercado)) {
            info[1] = "Reforço confirmado: " + nomeDoMercado(numero) + ". Disponível no plantel.";
            IO.println(info[1]);
        } else {
            IO.println("Compra indisponível: verifica o saldo ou se o atleta já foi contratado.");
        }
    }
}

// Ordenação por seleção, sem comparadores nem streams.
int[] ordemDaTabela(int[][] tabela) {
    int[] ordem = {0, 1, 2, 3, 4, 5, 6, 7};
    for (int i = 0; i < ordem.length - 1; i++) {
        int melhor = i;
        for (int j = i + 1; j < ordem.length; j++) {
            if (ficaAFrente(ordem[j], ordem[melhor], tabela)) { melhor = j; }
        }
        int temporario = ordem[i]; ordem[i] = ordem[melhor]; ordem[melhor] = temporario;
    }
    return ordem;
}

boolean ficaAFrente(int a, int b, int[][] tabela) {
    if (tabela[a][0] != tabela[b][0]) { return tabela[a][0] > tabela[b][0]; }
    int diferencaA = tabela[a][5] - tabela[a][6];
    int diferencaB = tabela[b][5] - tabela[b][6];
    if (diferencaA != diferencaB) { return diferencaA > diferencaB; }
    if (tabela[a][5] != tabela[b][5]) { return tabela[a][5] > tabela[b][5]; }
    return a < b;
}

void mostrarTabela(String[] clubes, int[][] tabela, int meuClube) {
    titulo("LIGA MICROFM | CLASSIFICAÇÃO");
    IO.println("  # Clube                      Pts  J  V  E  D  GM  GS   DG");
    int[] ordem = ordemDaTabela(tabela);
    for (int i = 0; i < ordem.length; i++) {
        int c = ordem[i];
        String marca = " ";
        if (c == meuClube) { marca = "*"; }
        IO.println(String.format("%s %d %-26s %3d %2d %2d %2d %2d %3d %3d %+4d", marca,
            i + 1, clubes[c], tabela[c][0], tabela[c][1], tabela[c][2], tabela[c][3],
            tabela[c][4], tabela[c][5], tabela[c][6], tabela[c][5] - tabela[c][6]));
    }
    IO.println("\nDesempate: pontos, diferença de golos, golos marcados e ordem dos clubes.");
}

void atualizarTabela(int casa, int fora, int golosCasa, int golosFora, int[][] tabela) {
    tabela[casa][1]++; tabela[fora][1]++;
    tabela[casa][5] += golosCasa; tabela[casa][6] += golosFora;
    tabela[fora][5] += golosFora; tabela[fora][6] += golosCasa;
    if (golosCasa > golosFora) {
        tabela[casa][0] += 3; tabela[casa][2]++; tabela[fora][4]++;
    } else if (golosCasa < golosFora) {
        tabela[fora][0] += 3; tabela[fora][2]++; tabela[casa][4]++;
    } else {
        tabela[casa][0]++; tabela[fora][0]++; tabela[casa][3]++; tabela[fora][3]++;
    }
}

int[] simularOutroJogo(int casa, int fora, int[] forcas) {
    int[] golos = new int[2];
    for (int ataque = 0; ataque < 10; ataque++) {
        double probCasa = 0.13 + (forcas[casa] - forcas[fora]) * 0.003;
        double probFora = 0.11 + (forcas[fora] - forcas[casa]) * 0.003;
        if (Math.random() < probCasa) { golos[0]++; }
        if (Math.random() < probFora) { golos[1]++; }
    }
    return golos;
}

void mostrarResultado(int casa, int fora, int[] resultado, String[] clubes) {
    titulo("APITO FINAL");
    IO.println("  " + clubes[casa] + "  " + resultado[0] + " - " + resultado[1] + "  " + clubes[fora]);
    IO.println(String.format("  Remates: %d - %d | Posse: %d%% - %d%%",
        resultado[2], resultado[3], resultado[4], resultado[5]));
}

int salarios(int[] carreira, int[][] jogadores) {
    int total = 0;
    for (int i = 0; i < carreira[10]; i++) { total += jogadores[i][2] * 55; }
    return total;
}

void disputarJornada(String[] clubes, int[] carreira, String[] info, int[] forcas,
        int[][] jogadores, String[] nomes, int[] onze, int[][] tabela, int[][] calendario) {
    if (carreira[1] >= 14) {
        IO.println("Época concluída. Consulta a tabela ou começa a época seguinte."); return;
    }
    boolean disponiveis = true;
    for (int i : onze) { if (jogadores[i][6] > 0) { disponiveis = false; } }
    if (!disponiveis) {
        escolherMelhorOnze(carreira, jogadores, onze);
        IO.println("Onze reajustado porque havia jogadores lesionados.");
    }
    int jornada = carreira[1];
    int jogoUser = jogoDoClube(jornada, carreira[0], calendario);
    int casa = calendario[jogoUser][0];
    int fora = calendario[jogoUser][1];
    titulo("JORNADA " + (jornada + 1) + " | " + clubes[casa] + " x " + clubes[fora]);
    IO.println("Plano: " + formacao(carreira[5]) + " / " + mentalidade(carreira[4]));
    IO.println("Pausas de decisão aos 30, 45, 60 e 75 minutos. Máximo 3 substituições.");
    int inicio = lerInteiro("1 Entrar em campo / 0 Voltar: ", 0, 1);
    if (inicio == 0) { return; }
    int[] resultado = jogarPartida(casa, fora, clubes, forcas, carreira, info, jogadores, nomes, onze);
    for (int i = jornada * 4; i < jornada * 4 + 4; i++) {
        int[] golos = resultado;
        if (i != jogoUser) { golos = simularOutroJogo(calendario[i][0], calendario[i][1], forcas); }
        calendario[i][2] = golos[0]; calendario[i][3] = golos[1];
        atualizarTabela(calendario[i][0], calendario[i][1], golos[0], golos[1], tabela);
    }
    int marcados = resultado[0];
    int sofridos = resultado[1];
    if (fora == carreira[0]) { marcados = resultado[1]; sofridos = resultado[0]; }
    int premio = 20000;
    if (marcados > sofridos) { premio = 70000; carreira[6] += 7; }
    else if (marcados == sofridos) { premio = 35000; carreira[6] += 1; }
    else { carreira[6] -= 6; }
    carreira[6] = limitar(carreira[6], 0, 100);
    int bilhetes = 0;
    if (casa == carreira[0]) { bilhetes = 95000 + (int) (Math.random() * 25001); }
    int despesa = salarios(carreira, jogadores);
    carreira[3] = limitar(carreira[3] + 50000 + bilhetes + premio - despesa, 0, 100000000);
    carreira[1]++;
    carreira[7] = 0;
    carreira[11] = 0;
    for (int i = 0; i < carreira[10]; i++) {
        jogadores[i][3] = limitar(jogadores[i][3] + 10, 1, 100);
        if (jogadores[i][6] > 0) { jogadores[i][6]--; }
    }
    String clinica = "";
    if (Math.random() < 0.20) {
        int lesionado = onze[(int) (Math.random() * onze.length)];
        jogadores[lesionado][6] = 1;
        clinica = " " + nomes[lesionado] + " falha a próxima jornada por lesão.";
    }
    info[1] = "Último jogo: " + marcados + "-" + sofridos + "." + clinica;
    mostrarResultado(casa, fora, resultado, clubes);
    IO.println("\nPatrocínio: " + dinheiro(50000) + " | Bilheteira: " + dinheiro(bilhetes));
    IO.println("Prémio: " + dinheiro(premio) + " | Salários: " + dinheiro(despesa));
    IO.println("Saldo: " + dinheiro(carreira[3]) + "." + clinica);
    titulo("RESULTADOS DA JORNADA");
    for (int i = jornada * 4; i < jornada * 4 + 4; i++) {
        IO.println(String.format("  %-24s %d - %d  %s", clubes[calendario[i][0]],
            calendario[i][2], calendario[i][3], clubes[calendario[i][1]]));
    }
    mostrarTabela(clubes, tabela, carreira[0]);
    if (carreira[1] == 14) {
        int[] ordem = ordemDaTabela(tabela);
        int lugar = 0;
        for (int i = 0; i < ordem.length; i++) { if (ordem[i] == carreira[0]) { lugar = i + 1; } }
        int premioFinal = (9 - lugar) * 100000;
        carreira[3] += premioFinal;
        info[1] = "Época terminada em " + lugar + ".º. Prémio: " + dinheiro(premioFinal) + ".";
        if (lugar == 1) { info[1] = "CAMPEÕES! " + info[1]; }
        IO.println("\n" + info[1]);
    }
    pausa();
}

void mostrarCalendario(String[] clubes, int[] carreira, int[][] calendario) {
    titulo("CALENDÁRIO DO CLUBE | ÉPOCA " + carreira[2]);
    for (int jornada = 0; jornada < 14; jornada++) {
        int jogo = jogoDoClube(jornada, carreira[0], calendario);
        String resultado = "por jogar";
        if (calendario[jogo][2] >= 0) {
            resultado = calendario[jogo][2] + " - " + calendario[jogo][3];
        }
        IO.println(String.format("  J%02d  %-24s %-9s %s", jornada + 1,
            clubes[calendario[jogo][0]], resultado, clubes[calendario[jogo][1]]));
    }
    pausa();
}

void financas(int[] carreira, int[][] jogadores, String[] nomes, String[] clubes, int[][] tabela) {
    titulo("FINANÇAS E DIREÇÃO");
    IO.println("  Clube: " + clubes[carreira[0]]);
    IO.println("  Tesouraria: " + dinheiro(carreira[3]));
    IO.println("  Salários por jornada: " + dinheiro(salarios(carreira, jogadores)));
    IO.println("  Patrocínio por jornada: 50 000 EUR");
    IO.println("  Bilheteira em casa: 95 000 a 120 000 EUR");
    IO.println("  Prémio por vitória / empate / derrota: 70 000 / 35 000 / 20 000 EUR");
    IO.println("  Objetivo: terminar nos quatro primeiros.");
    int[] ordem = ordemDaTabela(tabela);
    for (int i = 0; i < ordem.length; i++) {
        if (ordem[i] == carreira[0]) { IO.println("  Posição atual: " + (i + 1) + ".º lugar."); }
    }
    IO.println("  Prémio de fim de época: de 100 000 a 800 000 EUR, conforme o lugar.");
    IO.println("\n  Melhor marcador do clube:");
    int melhor = 0;
    for (int i = 1; i < carreira[10]; i++) {
        if (jogadores[i][4] > jogadores[melhor][4]) { melhor = i; }
    }
    IO.println("  " + nomes[melhor] + " | " + jogadores[melhor][4] + " golos.");
    IO.println("  Consulta o plantel para ver as estatísticas de todos os atletas.");
    pausa();
}

void preferencias(int[] carreira) {
    titulo("TRANSMISSÃO DO JOGO");
    IO.println("  1 Rápido / 2 Muito rápido / 3 Relâmpago / 4 Instantâneo");
    carreira[8] = lerInteiro("Velocidade: ", 1, 4);
    IO.println("  1 Campo em texto com quadros sucessivos / 0 Apenas relato");
    carreira[9] = lerInteiro("Apresentação: ", 0, 1);
    IO.println("A velocidade depende do computador. Instantâneo não desenha os quadros.");
}

void novaEpoca(int[] carreira, String[] info, int[][] jogadores, int[] forcas,
        int[][] tabela, int[][] calendario, int[][] mercado) {
    if (carreira[1] < 14) { IO.println("Ainda há jornadas por disputar."); return; }
    carreira[1] = 0;
    carreira[2]++;
    carreira[6] = 72;
    carreira[7] = 0;
    carreira[11] = 0;
    limparTabela(tabela);
    criarCalendario(calendario);
    for (int i = 0; i < carreira[10]; i++) {
        jogadores[i][1] = limitar(jogadores[i][1] + 1, 18, 60);
        jogadores[i][3] = 100;
        jogadores[i][4] = 0;
        jogadores[i][5] = 0;
        jogadores[i][6] = 0;
    }
    renovarMercado(mercado, carreira[2]);
    info[1] = "Nova época, novas oportunidades. O plantel e o saldo transitam.";
    IO.println(info[1]);
}

// Persistência legível: arrays convertidos em linhas separadas por ponto e vírgula.
String linhaDeNumeros(int[] numeros) {
    String linha = "";
    for (int i = 0; i < numeros.length; i++) {
        if (i > 0) { linha += ";"; }
        linha += numeros[i];
    }
    return linha + "\n";
}

boolean guardar(int[] carreira, String[] info, int[] forcas, int[][] jogadores,
        String[] nomes, int[] onze, int[][] tabela, int[][] calendario, int[][] mercado) {
    String texto = "MICROFM-1\n" + info[0] + "\n" + info[1] + "\n";
    texto += linhaDeNumeros(carreira);
    texto += linhaDeNumeros(forcas);
    for (int[] clube : tabela) { texto += linhaDeNumeros(clube); }
    for (int i = 0; i < jogadores.length; i++) {
        texto += nomes[i] + "\n" + linhaDeNumeros(jogadores[i]);
    }
    texto += linhaDeNumeros(onze);
    for (int[] jogo : calendario) { texto += linhaDeNumeros(jogo); }
    for (int[] atleta : mercado) { texto += linhaDeNumeros(atleta); }
    try {
        Files.writeString(Path.of("carreira.microfm"), texto);
        IO.println("Carreira guardada em carreira.microfm.");
        return true;
    } catch (IOException e) {
        IO.println("Não foi possível guardar: " + e.getMessage());
        return false;
    }
}

boolean lerNumeros(String linha, int[] destino) {
    String[] partes = linha.split(";");
    if (partes.length != destino.length) { return false; }
    for (int i = 0; i < partes.length; i++) { destino[i] = Integer.parseInt(partes[i]); }
    return true;
}

void copiarNumeros(int[] origem, int[] destino) {
    for (int i = 0; i < origem.length; i++) { destino[i] = origem[i]; }
}

void copiarMatriz(int[][] origem, int[][] destino) {
    for (int i = 0; i < origem.length; i++) { copiarNumeros(origem[i], destino[i]); }
}

boolean carregar(int[] carreira, String[] info, int[] forcas, int[][] jogadores,
        String[] nomes, int[] onze, int[][] tabela, int[][] calendario, int[][] mercado) {
    try {
        ArrayList<String> linhas = new ArrayList<>(Files.readAllLines(Path.of("carreira.microfm")));
        if (linhas.size() != 142 || !linhas.get(0).equals("MICROFM-1")) {
            IO.println("Ficheiro de carreira inválido ou de outra versão."); return false;
        }
        int[] novaCarreira = new int[13];
        int[] novasForcas = new int[8];
        int[][] novosJogadores = new int[32][7];
        String[] novosNomes = new String[32];
        int[] novoOnze = new int[11];
        int[][] novaTabela = new int[8][7];
        int[][] novoCalendario = new int[56][4];
        int[][] novoMercado = new int[8][5];
        boolean valido = lerNumeros(linhas.get(3), novaCarreira);
        valido = lerNumeros(linhas.get(4), novasForcas) && valido;
        int linha = 5;
        for (int i = 0; i < 8; i++) { valido = lerNumeros(linhas.get(linha++), novaTabela[i]) && valido; }
        for (int i = 0; i < 32; i++) {
            novosNomes[i] = linhas.get(linha++);
            valido = lerNumeros(linhas.get(linha++), novosJogadores[i]) && valido;
        }
        valido = lerNumeros(linhas.get(linha++), novoOnze) && valido;
        for (int i = 0; i < 56; i++) { valido = lerNumeros(linhas.get(linha++), novoCalendario[i]) && valido; }
        for (int i = 0; i < 8; i++) { valido = lerNumeros(linhas.get(linha++), novoMercado[i]) && valido; }
        valido = valido && validarCarreira(novaCarreira, novasForcas, novosJogadores,
            novosNomes, novoOnze, novaTabela, novoCalendario, novoMercado);
        if (!valido || linhas.get(1).length() > 30 || linhas.get(2).length() > 500) {
            IO.println("A carreira contém dados inconsistentes. Nada foi alterado."); return false;
        }
        // Só substitui o estado atual depois de validar o ficheiro completo.
        copiarNumeros(novaCarreira, carreira); copiarNumeros(novasForcas, forcas);
        copiarNumeros(novoOnze, onze); copiarMatriz(novosJogadores, jogadores);
        copiarMatriz(novaTabela, tabela); copiarMatriz(novoCalendario, calendario);
        copiarMatriz(novoMercado, mercado);
        for (int i = 0; i < nomes.length; i++) { nomes[i] = novosNomes[i]; }
        info[0] = linhas.get(1); info[1] = linhas.get(2);
        IO.println("Carreira carregada. Bem-vindo de volta, " + info[0] + ".");
        return true;
    } catch (IOException e) {
        IO.println("Não foi possível ler carreira.microfm. Inicia uma carreira ou verifica o ficheiro.");
    } catch (NumberFormatException e) {
        IO.println("O ficheiro contém um número inválido. Nada foi alterado.");
    }
    return false;
}

boolean validarCarreira(int[] c, int[] forcas, int[][] jogadores, String[] nomes,
        int[] onze, int[][] tabela, int[][] calendario, int[][] mercado) {
    if (c[0] < 0 || c[0] > 7 || c[1] < 0 || c[1] > 14 || c[2] < 1 || c[2] > 10000
            || c[3] < 0 || c[3] > 110000000 || c[4] < 0 || c[4] > 2
            || c[5] < 0 || c[5] > 2 || c[6] < 0 || c[6] > 100
            || c[7] < 0 || c[7] > 1 || c[8] < 1 || c[8] > 4
            || c[9] < 0 || c[9] > 1 || c[10] < 20 || c[10] > 28
            || c[11] != 0 || c[12] != 0) { return false; }
    for (int f : forcas) { if (f < 1 || f > 99) { return false; } }
    boolean[] vistos = new boolean[jogadores.length];
    for (int i : onze) {
        if (i < 0 || i >= c[10]) { return false; }
        if (vistos[i]) { return false; }
        vistos[i] = true;
    }
    int totalGolos = 0;
    for (int i = 0; i < c[10]; i++) {
        int[] j = jogadores[i];
        if (nomes[i].length() < 1 || nomes[i].length() > 40 || j[0] < 0 || j[0] > 3
                || j[1] < 18 || j[1] > 60 || j[2] < 1 || j[2] > 99
                || j[3] < 1 || j[3] > 100 || j[4] < 0 || j[4] > 420
                || j[5] < 0 || j[5] > c[1] || j[6] < 0 || j[6] > 1) { return false; }
        totalGolos += j[4];
    }
    int[][] esperado = new int[56][4];
    int[][] recalculada = new int[8][7];
    criarCalendario(esperado);
    for (int i = 0; i < calendario.length; i++) {
        int[] jogo = calendario[i];
        if (jogo[0] != esperado[i][0] || jogo[1] != esperado[i][1]) { return false; }
        if (i < c[1] * 4) {
            if (jogo[2] < 0 || jogo[2] > 30 || jogo[3] < 0 || jogo[3] > 30) { return false; }
            atualizarTabela(jogo[0], jogo[1], jogo[2], jogo[3], recalculada);
        } else if (jogo[2] != -1 || jogo[3] != -1) { return false; }
    }
    for (int i = 0; i < tabela.length; i++) {
        for (int j = 0; j < tabela[i].length; j++) {
            if (tabela[i][j] != recalculada[i][j]) { return false; }
        }
    }
    if (totalGolos != tabela[c[0]][5]) { return false; }
    int comprados = 0;
    for (int i = 0; i < mercado.length; i++) {
        int[] m = mercado[i];
        if (m[0] < 0 || m[0] > 3 || m[1] < 18 || m[1] > 60
                || m[2] < 1 || m[2] > 99 || m[3] < 0 || m[3] > 5000000
                || m[4] < 0 || m[4] > 1) { return false; }
        comprados += m[4];
    }
    return c[10] == 20 + comprados;
}

// MOTOR DE JOGO — módulos 1 a 5: decisões, ciclos, métodos e matrizes.
// O campo é uma representação abstrata dos lances sorteados pelo simulador.
int[] jogarPartida(int casa, int fora, String[] clubes, int[] forcas,
        int[] carreira, String[] info, int[][] jogadores, String[] nomes, int[] onze) {
    int[] resultado = new int[6];
    boolean[] usados = new boolean[jogadores.length];
    for (int i = 0; i < onze.length; i++) {
        usados[onze[i]] = true;
    }
    carreira[11] = 0;
    boolean animar = carreira[9] == 1 && carreira[8] != 4;
    int posseCasa = 0;
    double[][] posicoes = new double[22][2];
    int[][] destinos = posicoesDoLance(3, casa, fora, carreira);
    for (int i = 0; i < 22; i++) {
        posicoes[i][0] = destinos[i][0];
        posicoes[i][1] = destinos[i][1];
    }
    double[] bola = {32, 8};
    String ultimoGolo = "A bola vai rolar. Boa sorte, mister!";
    if (!animar) {
        titulo("EM DIRETO | " + clubes[casa] + " - " + clubes[fora]);
    }

    // Um lance representa três minutos: 30 lances, duas partes de 45 minutos.
    for (int minuto = 3; minuto <= 90; minuto += 3) {
        double forcaCasa = forcas[casa] + 3;
        double forcaFora = forcas[fora];
        if (casa == carreira[0]) {
            forcaCasa = forcaDoOnze(jogadores, onze, carreira) + 3;
        } else {
            forcaFora = forcaDoOnze(jogadores, onze, carreira);
        }
        double probabilidadeCasa = 0.5 + (forcaCasa - forcaFora) * 0.006;
        if (casa == carreira[0]) {
            probabilidadeCasa += (carreira[4] - 1) * 0.035;
        } else {
            probabilidadeCasa -= (carreira[4] - 1) * 0.035;
        }
        if (probabilidadeCasa < 0.25) {
            probabilidadeCasa = 0.25;
        }
        if (probabilidadeCasa > 0.75) {
            probabilidadeCasa = 0.75;
        }
        boolean ataqueCasa = Math.random() < probabilidadeCasa;
        int atacante = fora;
        int inicio = 11;
        if (ataqueCasa) {
            atacante = casa;
            inicio = 0;
            posseCasa++;
        }
        resultado[4] = posseCasa * 100 / (minuto / 3);
        resultado[5] = 100 - resultado[4];

        double probabilidadeRemate = 0.48;
        if (atacante == carreira[0]) {
            probabilidadeRemate += (carreira[4] - 1) * 0.055;
        }
        boolean remate = Math.random() < probabilidadeRemate;
        destinos = posicoesDoLance(minuto, casa, fora, carreira);
        int receptor1 = inicio + 5 + (int) (Math.random() * 4);
        int receptor2 = inicio + 8 + (int) (Math.random() * 3);
        String lance = String.format("%02d' %s troca a bola no meio-campo.", minuto, clubes[atacante]);
        if (animar) {
            animarPasse(bola, destinos[receptor1][0], destinos[receptor1][1],
                    posicoes, destinos, minuto, casa, fora, clubes, carreira,
                    resultado, lance, ultimoGolo);
            animarPasse(bola, destinos[receptor2][0], destinos[receptor2][1],
                    posicoes, destinos, minuto, casa, fora, clubes, carreira,
                    resultado, String.format("%02d' Passe a rasgar a defesa...", minuto), ultimoGolo);
        }

        if (remate) {
            double diferenca = forcaFora - forcaCasa;
            if (ataqueCasa) {
                diferenca = forcaCasa - forcaFora;
                resultado[2]++;
            } else {
                resultado[3]++;
            }
            double probabilidadeGolo = 0.18 + diferenca * 0.003;
            // Atacar dá mais oportunidades, mas deixa espaço ao adversário.
            if (atacante == carreira[0]) {
                probabilidadeGolo += (carreira[4] - 1) * 0.015;
            } else {
                probabilidadeGolo += (carreira[4] - 1) * 0.035;
            }
            if (probabilidadeGolo < 0.07) {
                probabilidadeGolo = 0.07;
            }
            if (probabilidadeGolo > 0.34) {
                probabilidadeGolo = 0.34;
            }
            boolean golo = Math.random() < probabilidadeGolo;
            boolean paraDireita = ataqueCasa;
            if (minuto > 45) {
                paraDireita = !paraDireita;
            }
            int balizaX = 0;
            if (paraDireita) {
                balizaX = 64;
            }
            int balizaY = 7 + (int) (Math.random() * 3);
            String desfecho = "O guarda-redes segura!";
            if (!golo) {
                if (Math.random() < 0.5) {
                    balizaY = 3;
                    desfecho = "Remate para fora!";
                } else {
                    if (paraDireita) {
                        balizaX = 60;
                    } else {
                        balizaX = 4;
                    }
                }
            }
            if (animar) {
                animarPasse(bola, balizaX, balizaY, posicoes, destinos,
                        minuto, casa, fora, clubes, carreira, resultado,
                        String.format("%02d' %s remata!", minuto, clubes[atacante]), ultimoGolo);
            }
            if (golo) {
                if (ataqueCasa) {
                    resultado[0]++;
                } else {
                    resultado[1]++;
                }
                String marcador = "";
                if (atacante == carreira[0]) {
                    int jogador = sortearMarcador(jogadores, onze);
                    jogadores[jogador][4]++;
                    marcador = " | " + nomes[jogador];
                }
                ultimoGolo = String.format("%02d' GOLO! %s%s", minuto, clubes[atacante], marcador);
                lance = ultimoGolo;
                if (animar) {
                    for (int quadro = 0; quadro < 10; quadro++) {
                        desenharCampo(posicoes, bola, minuto, casa, fora, clubes,
                                carreira, resultado, lance, ultimoGolo);
                        cadenciaDoCampo(carreira[8]);
                    }
                }
                bola[0] = 32;
                bola[1] = 8;
            } else {
                lance = String.format("%02d' %s %s", minuto, clubes[atacante], desfecho);
            }
        } else {
            lance = String.format("%02d' %s procura espaço. A defesa fecha bem.", minuto, clubes[atacante]);
            if (animar) {
                int apoio = inicio + 1 + (int) (Math.random() * 7);
                animarPasse(bola, destinos[apoio][0], destinos[apoio][1],
                        posicoes, destinos, minuto, casa, fora, clubes, carreira,
                        resultado, lance, ultimoGolo);
            }
        }
        if (animar) {
            desenharCampo(posicoes, bola, minuto, casa, fora, clubes,
                    carreira, resultado, lance, ultimoGolo);
        } else {
            IO.println(lance);
        }

        // Só quem está em campo perde condição. Atacar exige mais esforço.
        boolean desgaste = minuto % 6 == 0;
        if (carreira[4] == 0) { desgaste = minuto % 9 == 0; }
        if (carreira[4] == 2) { desgaste = true; }
        for (int i = 0; i < onze.length; i++) {
            usados[onze[i]] = true;
            if (desgaste) {
                jogadores[onze[i]][3] = limitar(jogadores[onze[i]][3] - 1, 1, 100);
            }
        }
        if (minuto == 30 || minuto == 45 || minuto == 60 || minuto == 75) {
            // Contrato: marcador, minuto, clubes, remates e percentagens de posse.
            int[] situacao = {resultado[0], resultado[1], minuto, casa, fora,
                    resultado[2], resultado[3], resultado[4], resultado[5]};
            intervalo(carreira, info, jogadores, nomes, onze, usados, situacao);
            // Também marca logo um suplente que entre na pausa dos 75 minutos.
            for (int i = 0; i < onze.length; i++) {
                usados[onze[i]] = true;
            }
        }
    }
    for (int i = 0; i < usados.length; i++) {
        if (usados[i]) {
            jogadores[i][5]++;
        }
    }
    IO.println(String.format("FIM DO JOGO | %s %d - %d %s", clubes[casa], resultado[0], resultado[1], clubes[fora]));
    IO.println(String.format("Remates: %d - %d | Posse: %d%% - %d%%", resultado[2], resultado[3], resultado[4], resultado[5]));
    return resultado;
}

double forcaDoOnze(int[][] jogadores, int[] onze, int[] carreira) {
    double soma = 0;
    for (int i = 0; i < onze.length; i++) {
        int jogador = onze[i];
        double qualidade = jogadores[jogador][2];
        if (jogadores[jogador][0] != posicaoEsperadaJogo(i, carreira[5])) {
            qualidade -= 18;
        }
        soma += qualidade * (0.80 + jogadores[jogador][3] * 0.002);
    }
    return soma / onze.length + (carreira[6] - 50) * 0.07;
}

int posicaoEsperadaJogo(int lugar, int formacao) {
    int defesas = 4;
    int medios = 3;
    if (formacao == 1) {
        medios = 4;
    }
    if (formacao == 2) {
        defesas = 3;
        medios = 5;
    }
    if (lugar == 0) {
        return 0;
    }
    if (lugar <= defesas) {
        return 1;
    }
    if (lugar <= defesas + medios) {
        return 2;
    }
    return 3;
}

int sortearMarcador(int[][] jogadores, int[] onze) {
    int[] pesos = new int[onze.length];
    int total = 0;
    for (int i = 0; i < onze.length; i++) {
        int posicao = jogadores[onze[i]][0];
        pesos[i] = switch (posicao) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 3;
            default -> 7;
        };
        total += pesos[i];
    }
    int sorteado = (int) (Math.random() * total);
    int acumulado = 0;
    int marcador = onze[10];
    boolean encontrado = false;
    for (int i = 0; i < onze.length; i++) {
        acumulado += pesos[i];
        if (!encontrado && sorteado < acumulado) {
            marcador = onze[i];
            encontrado = true;
        }
    }
    return marcador;
}

int[][] posicoesDoLance(int minuto, int casa, int fora, int[] carreira) {
    int[][] posicoes = new int[22][2];
    for (int equipa = 0; equipa < 2; equipa++) {
        int clube = casa;
        if (equipa == 1) {
            clube = fora;
        }
        int formacao = 0;
        if (clube == carreira[0]) {
            formacao = carreira[5];
        }
        for (int lugar = 0; lugar < 11; lugar++) {
            int linha = posicaoEsperadaJogo(lugar, formacao);
            int anteriores = 0;
            int quantidade = 0;
            for (int outro = 0; outro < 11; outro++) {
                if (posicaoEsperadaJogo(outro, formacao) == linha) {
                    quantidade++;
                    if (outro < lugar) {
                        anteriores++;
                    }
                }
            }
            int x = switch (linha) {
                case 0 -> 3;
                case 1 -> 17;
                case 2 -> 30;
                default -> 45;
            };
            int y = 8;
            if (quantidade > 1) {
                y = 2 + anteriores * 12 / (quantidade - 1);
            }
            if (linha != 0) {
                x += (int) (Math.random() * 5) - 2;
                y = limitar(y + (int) (Math.random() * 3) - 1, 1, 15);
            }
            boolean inverter = equipa == 1;
            if (minuto > 45) {
                inverter = !inverter;
            }
            if (inverter) {
                x = 64 - x;
            }
            posicoes[equipa * 11 + lugar][0] = x;
            posicoes[equipa * 11 + lugar][1] = y;
        }
    }
    return posicoes;
}

void animarPasse(double[] bola, double destinoX, double destinoY,
        double[][] posicoes, int[][] destinos, int minuto, int casa, int fora,
        String[] clubes, int[] carreira, int[] resultado, String lance, String ultimoGolo) {
    int quadros = 8;
    if (carreira[8] == 1) {
        quadros = 10;
    }
    if (carreira[8] == 3) {
        quadros = 4;
    }
    double inicioX = bola[0];
    double inicioY = bola[1];
    for (int quadro = 1; quadro <= quadros; quadro++) {
        bola[0] = inicioX + (destinoX - inicioX) * quadro / quadros;
        bola[1] = inicioY + (destinoY - inicioY) * quadro / quadros;
        for (int i = 0; i < posicoes.length; i++) {
            posicoes[i][0] += (destinos[i][0] - posicoes[i][0]) * 0.25;
            posicoes[i][1] += (destinos[i][1] - posicoes[i][1]) * 0.25;
        }
        desenharCampo(posicoes, bola, minuto, casa, fora, clubes,
                carreira, resultado, lance, ultimoGolo);
        cadenciaDoCampo(carreira[8]);
    }
}

void desenharCampo(double[][] posicoes, double[] bola, int minuto, int casa,
        int fora, String[] clubes, int[] carreira, int[] resultado,
        String lance, String ultimoGolo) {
    char[][] campo = new char[17][65];
    for (int y = 0; y < campo.length; y++) {
        for (int x = 0; x < campo[y].length; x++) {
            char caracter = ' ';
            if (y == 0 || y == 16) {
                caracter = '-';
            }
            if (x == 0 || x == 64 || x == 32) {
                caracter = '|';
            }
            if ((x == 9 || x == 55) && y >= 4 && y <= 12) {
                caracter = '|';
            }
            if ((y == 4 || y == 12) && (x <= 9 || x >= 55)) {
                caracter = '-';
            }
            if ((x == 0 || x == 64) && y >= 6 && y <= 10) {
                caracter = '#';
            }
            if ((x == 29 || x == 35) && y >= 6 && y <= 10) {
                caracter = ':';
            }
            campo[y][x] = caracter;
        }
    }
    for (int i = 0; i < posicoes.length; i++) {
        int x = limitar((int) (posicoes[i][0] + 0.5), 1, 63);
        int y = limitar((int) (posicoes[i][1] + 0.5), 1, 15);
        char simbolo = 'A';
        if (i >= 11) {
            simbolo = 'B';
        }
        // Evita que dois jogadores desapareçam na mesma célula do desenho.
        while (campo[y][x] == 'A' || campo[y][x] == 'B') {
            x++;
            if (x >= 64) {
                x = 1;
                y++;
                if (y >= 16) {
                    y = 1;
                }
            }
        }
        campo[y][x] = simbolo;
    }
    int bolaX = limitar((int) (bola[0] + 0.5), 0, 64);
    int bolaY = limitar((int) (bola[1] + 0.5), 0, 16);
    campo[bolaY][bolaX] = 'o';
    // Cada quadro é texto normal: o terminal desloca o anterior para cima.
    // Não são necessários comandos ANSI nem uma biblioteca gráfica.
    String texto = "  MICRO FM | CENTRAL DE JOGO\n";
    texto += String.format("  %02d:00   %s %d - %d %s\n", minuto,
            clubes[casa], resultado[0], resultado[1], clubes[fora]);
    texto += String.format("  Remates %d - %d   Posse %d%% - %d%%   Tática: %s\n",
            resultado[2], resultado[3], resultado[4], resultado[5],
            switch (carreira[4]) {
                case 0 -> "Defensiva";
                case 1 -> "Equilibrada";
                default -> "Ofensiva";
            });
    for (int y = 0; y < campo.length; y++) {
        texto += "  ";
        for (int x = 0; x < campo[y].length; x++) {
            texto += campo[y][x];
        }
        texto += "\n";
    }
    texto += "  A " + clubes[casa] + "   B " + clubes[fora] + "   o bola\n";
    texto += "  " + lance + "\n";
    texto += "  " + ultimoGolo + "\n";
    texto += "  Pausas táticas: 30 / 45 / 60 / 75 min.\n";
    IO.print(texto);
}

void cadenciaDoCampo(int velocidade) {
    // As aulas não incluem relógios nem Thread.sleep. Este ciclo repete uma
    // operação já ensinada (Math.random) para o desenho ser visível. A duração
    // depende do computador e usa CPU; o modo instantâneo não executa o ciclo.
    int repeticoes = 1000000;
    if (velocidade == 1) {
        repeticoes = 1800000;
    }
    if (velocidade == 3) {
        repeticoes = 350000;
    }
    if (velocidade == 4) {
        repeticoes = 0;
    }
    for (int i = 0; i < repeticoes; i++) {
        Math.random();
    }
}
