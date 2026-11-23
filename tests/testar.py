"""Verificações reais do MicroFM, sem dependências externas.

Executar na raiz: python tests/testar.py
Todos os ficheiros de carreira e classes ficam numa pasta temporária isolada.
"""

from pathlib import Path
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
JAVA_OPTIONS = ["-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dfile.encoding=UTF-8"]

HARNESS = r'''

void exigir(boolean condicao, String mensagem) {
    if (!condicao) { throw new AssertionError(mensagem); }
}

void main(String[] args) throws Exception {
    int[] c = new int[13];
    String[] info = new String[2];
    int[] forcas = new int[8];
    int[][] jogadores = new int[32][7];
    String[] nomes = new String[32];
    int[] onze = new int[11];
    int[][] tabela = new int[8][7];
    int[][] calendario = new int[56][4];
    int[][] mercado = new int[8][5];
    iniciarCarreira(0, "Treinador de teste", c, info, forcas, jogadores, nomes,
        onze, tabela, calendario, mercado);

    switch (args[0]) {
        case "calendario" -> {
            int[][] pares = new int[8][8];
            for (int jornada = 0; jornada < 14; jornada++) {
                boolean[] vistos = new boolean[8];
                for (int i = jornada * 4; i < jornada * 4 + 4; i++) {
                    int casa = calendario[i][0], fora = calendario[i][1];
                    exigir(casa != fora && !vistos[casa] && !vistos[fora], "Clube repetido na jornada");
                    vistos[casa] = true; vistos[fora] = true; pares[casa][fora]++;
                    exigir(calendario[i][2] == -1 && calendario[i][3] == -1, "Resultado prematuro");
                    if (i < 28) {
                        exigir(casa == calendario[i + 28][1] && fora == calendario[i + 28][0],
                            "Segunda volta deve inverter casa e fora");
                    }
                }
                for (boolean visto : vistos) { exigir(visto, "Clube em falta na jornada"); }
            }
            for (int a = 0; a < 8; a++) {
                for (int b = 0; b < 8; b++) {
                    exigir(pares[a][b] == (a == b ? 0 : 1), "Par de clubes repetido/em falta");
                }
            }
            atualizarTabela(0, 1, 2, 0, tabela);
            atualizarTabela(0, 2, 1, 1, tabela);
            atualizarTabela(1, 2, 0, 3, tabela);
            exigir(java.util.Arrays.equals(tabela[0], new int[]{4, 2, 1, 1, 0, 3, 1}), "Pontuacao clube 0");
            exigir(java.util.Arrays.equals(tabela[1], new int[]{0, 2, 0, 0, 2, 0, 5}), "Pontuacao clube 1");
            exigir(java.util.Arrays.equals(tabela[2], new int[]{4, 2, 1, 1, 0, 4, 1}), "Pontuacao clube 2");
            int[] ordem = ordemDaTabela(tabela);
            exigir(ordem[0] == 2 && ordem[1] == 0, "Desempate por diferenca de golos");
            limparTabela(tabela);
            for (int i = 0; i < 56; i++) {
                atualizarTabela(calendario[i][0], calendario[i][1], i * 3 % 5, i * 7 % 4, tabela);
            }
            int gm = 0, gs = 0;
            for (int[] clube : tabela) {
                exigir(clube[1] == 14 && clube[1] == clube[2] + clube[3] + clube[4], "Total de jogos");
                exigir(clube[0] == clube[2] * 3 + clube[3], "Regra dos pontos");
                gm += clube[5]; gs += clube[6];
            }
            exigir(gm == gs, "Golos marcados e sofridos nao coincidem");
        }
        case "onze" -> {
            for (int j = 0; j < c[10]; j++) { jogadores[j][2] = 70; jogadores[j][3] = 80; }
            jogadores[0][2] = 99; jogadores[0][6] = 1;
            for (int desenho = 0; desenho < 3; desenho++) {
                c[5] = desenho;
                escolherMelhorOnze(c, jogadores, onze);
                boolean[] vistos = new boolean[32];
                int[] posicoes = new int[4];
                for (int lugar = 0; lugar < 11; lugar++) {
                    int j = onze[lugar];
                    exigir(j >= 0 && j < 20 && !vistos[j], "Onze deve ter onze jogadores distintos");
                    exigir(jogadores[j][6] == 0, "Lesionado escolhido apesar de alternativas");
                    exigir(jogadores[j][0] == posicaoDoLugar(lugar, desenho), "Posicao errada na formacao");
                    vistos[j] = true; posicoes[jogadores[j][0]]++;
                }
                int[][] contagens = {{1, 4, 3, 3}, {1, 4, 4, 2}, {1, 3, 5, 2}};
                exigir(java.util.Arrays.equals(posicoes, contagens[desenho]), "Formacao incorreta");
            }
            jogadores[0][6] = 0; jogadores[0][2] = 70; jogadores[0][3] = 100;
            jogadores[1][2] = 75; jogadores[1][3] = 1;
            escolherMelhorOnze(c, jogadores, onze);
            exigir(onze[0] == 0, "Condicao deve influenciar selecao do guarda-redes");
        }
        case "treino" -> {
            jogadores[0][2] = 98; jogadores[0][3] = 5;
            jogadores[1][6] = 1;
            int qualidadeLesionado = jogadores[1][2];
            treinar(c, info, jogadores); // Entrada: 2, tecnica.
            exigir(c[7] == 1 && jogadores[0][2] == 99 && jogadores[0][3] == 1, "Efeito/limites do treino");
            exigir(jogadores[1][2] == qualidadeLesionado, "Lesionado nao deve treinar");
            String antes = linhaDeNumeros(c) + linhaDeNumeros(jogadores[0]);
            treinar(c, info, jogadores); // Bloqueado, nao consome entrada.
            exigir(antes.equals(linhaDeNumeros(c) + linhaDeNumeros(jogadores[0])), "Treino repetido na mesma semana");
            c[7] = 0;
            treinar(c, info, jogadores); // Entrada: 0, cancelar.
            exigir(c[7] == 0, "Cancelar nao deve gastar sessao");
            c[6] = 99; jogadores[0][3] = 95;
            treinar(c, info, jogadores); // Entrada: 1, recuperacao.
            exigir(c[6] == 100 && jogadores[0][3] == 100, "Recuperacao deve respeitar limites");
            c[7] = 0;
            treinar(c, info, jogadores); // Entrada: 3, espirito.
            exigir(c[6] == 100 && jogadores[0][3] == 97, "Espirito de equipa");
            c[1] = 14; c[7] = 0;
            treinar(c, info, jogadores);
            exigir(c[7] == 0, "Nao se treina apos o fim de epoca");
        }
        case "mercado" -> {
            int preco = mercado[0][3];
            c[3] = preco;
            exigir(contratar(0, c, jogadores, nomes, mercado), "Compra com saldo exato deve funcionar");
            exigir(c[3] == 0 && c[10] == 21 && mercado[0][4] == 1, "Compra deve debitar uma unica vez");
            exigir(nomes[20].equals(nomeDoMercado(0)) && jogadores[20][3] == 100, "Reforco nao chegou ao plantel");
            exigir(!contratar(0, c, jogadores, nomes, mercado) && c[10] == 21 && c[3] == 0, "Compra duplicada");
            exigir(!contratar(1, c, jogadores, nomes, mercado) && mercado[1][4] == 0, "Compra sem saldo");
            exigir(!contratar(-1, c, jogadores, nomes, mercado) && !contratar(8, c, jogadores, nomes, mercado), "Indice invalido");
            c[3] = 10000000;
            for (int i = 1; i < 8; i++) { exigir(contratar(i, c, jogadores, nomes, mercado), "Compra restante " + i); }
            exigir(c[10] == 28 && c[3] >= 0, "Oito reforcos devem caber no plantel");
            renovarMercado(mercado, 2);
            for (int i = 0; i < 8; i++) { exigir(mercado[i][4] == 1, "Nova epoca duplicaria atleta contratado"); }
            iniciarCarreira(0, "QA", c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado);
            c[10] = jogadores.length;
            int saldo = c[3];
            exigir(!contratar(0, c, jogadores, nomes, mercado) && c[3] == saldo, "Capacidade do plantel");
        }
        case "substituicoes" -> {
            int[] inicial = {0, 2, 3, 4, 5, 8, 9, 10, 15, 16, 17};
            copiarNumeros(inicial, onze);
            boolean[] usados = new boolean[32];
            for (int j : onze) { usados[j] = true; }
            intervalo(c, info, jogadores, nomes, onze, usados, new int[]{0, 0, 30});
            exigir(c[11] == 3, "Limite de tres substituicoes");
            exigir(onze[0] == 1 && onze[1] == 6 && onze[2] == 7, "Substituicoes/reentrada incorretas");
            exigir(usados[0] && usados[1] && usados[6] && usados[7], "Historico de participantes");
        }
        case "persistencia" -> {
            exigir(contratar(0, c, jogadores, nomes, mercado), "Preparacao de persistencia");
            c[4] = 2; c[5] = 1; c[8] = 4; c[9] = 0;
            info[0] = "Luís QA"; info[1] = "Mensagem com acentos: época, vitória.";
            escolherMelhorOnze(c, jogadores, onze);
            exigir(guardar(c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado), "Guardar falhou");
            Path ficheiro = Path.of("carreira.microfm");
            String original = Files.readString(ficheiro);
            for (int i = 0; i < c.length; i++) { c[i] = -1; }
            for (int i = 0; i < forcas.length; i++) { forcas[i] = -1; }
            for (int[] j : jogadores) { java.util.Arrays.fill(j, -1); }
            for (int[] t : tabela) { java.util.Arrays.fill(t, -1); }
            for (int[] j : calendario) { java.util.Arrays.fill(j, -1); }
            for (int[] m : mercado) { java.util.Arrays.fill(m, -1); }
            java.util.Arrays.fill(onze, -1); java.util.Arrays.fill(nomes, "alterado");
            info[0] = "alterado"; info[1] = "alterado";
            exigir(carregar(c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado), "Carregar falhou");
            exigir(guardar(c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado), "Novo guardar falhou");
            exigir(original.equals(Files.readString(ficheiro)), "Roundtrip nao preservou todo o estado");
            String[] linhas = original.split("\n", -1);
            String[] corrompidos = new String[6];
            corrompidos[0] = "MICROFM-1\ntruncado\n";
            corrompidos[1] = original.replaceFirst("MICROFM-1", "OUTRA-VERSAO");
            corrompidos[2] = original.replaceFirst("1800000", "nao-e-numero");
            // O saldo mudou na compra: altera inequivocamente a primeira linha de numeros.
            corrompidos[2] = original.replace(linhas[3] + "\n", "texto-invalido\n");
            String[] partes = linhas[77].split(";"); partes[1] = partes[0];
            String duplicados = String.join(";", partes);
            corrompidos[3] = original.replace(linhas[77] + "\n", duplicados + "\n");
            partes = linhas[3].split(";"); partes[1] = "15";
            corrompidos[4] = original.replace(linhas[3] + "\n", String.join(";", partes) + "\n");
            partes = linhas[78].split(";"); partes[2] = "1"; partes[3] = "0";
            corrompidos[5] = original.replace(linhas[78] + "\n", String.join(";", partes) + "\n");
            for (int i = 0; i < corrompidos.length; i++) {
                Files.writeString(ficheiro, corrompidos[i]);
                exigir(!carregar(c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado), "Corrupcao aceite: " + i);
                guardar(c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado);
                exigir(original.equals(Files.readString(ficheiro)), "Estado alterado apos corrupcao: " + i);
            }
            Files.delete(ficheiro);
            exigir(!carregar(c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado), "Ficheiro inexistente");
            guardar(c, info, forcas, jogadores, nomes, onze, tabela, calendario, mercado);
            exigir(original.equals(Files.readString(ficheiro)), "Ficheiro inexistente alterou estado");
        }
        case "campo" -> {
            double[][] posicoes = new double[22][2];
            for (int i = 0; i < posicoes.length; i++) {
                posicoes[i][0] = 5 + (i % 11) * 3;
                posicoes[i][1] = i < 11 ? 5 : 11;
            }
            String[] clubes = {"Casa QA", "Fora QA"};
            double[] bola = {20, 8};
            int[] resultado = {2, 1, 7, 4, 55, 45};
            desenharCampo(posicoes, bola, 31, 0, 1, clubes, c, resultado,
                "Passe curto QA", "Ultimo golo QA");
            bola[0] = 40;
            resultado[0] = 3;
            desenharCampo(posicoes, bola, 32, 0, 1, clubes, c, resultado,
                "Remate QA", "Novo golo QA");
        }
        default -> throw new AssertionError("Teste desconhecido");
    }
    IO.println("PASSOU " + args[0]);
}
'''


def executar(comando, *, cwd, entrada="", timeout=90):
    resultado = subprocess.run(
        comando, cwd=cwd, input=entrada, capture_output=True,
        text=True, encoding="utf-8", errors="replace", timeout=timeout,
    )
    if resultado.returncode:
        raise AssertionError(
            f"Falhou ({resultado.returncode}): {' '.join(map(str, comando))}\n"
            f"{resultado.stdout[-12000:]}\n{resultado.stderr[-8000:]}"
        )
    return resultado.stdout


def numeros(linha):
    return list(map(int, linha.split(";")))


def ler_carreira(pasta):
    linhas = (pasta / "carreira.microfm").read_text(encoding="utf-8").splitlines()
    assert len(linhas) == 142 and linhas[0] == "MICROFM-1"
    return {
        "c": numeros(linhas[3]),
        "tabela": [numeros(linha) for linha in linhas[5:13]],
        "jogadores": [numeros(linhas[14 + i * 2]) for i in range(32)],
        "onze": numeros(linhas[77]),
        "calendario": [numeros(linha) for linha in linhas[78:134]],
        "mercado": [numeros(linha) for linha in linhas[134:142]],
    }


def main():
    if not shutil.which("java") or not shutil.which("javac"):
        raise SystemExit("Instala o JDK 25 para executar estas verificações.")
    fonte = (ROOT / "MicroFM.java").read_text(encoding="utf-8")
    assert "int[] jogarPartida(" in fonte, "Motor ainda não integrado"
    assert fonte.count("void main() {") == 1, "Ponto de entrada inesperado"
    with tempfile.TemporaryDirectory(prefix=".qa-integracao-", dir=ROOT) as temporaria:
        pasta = Path(temporaria)
        (pasta / "MicroFM.java").write_text(fonte, encoding="utf-8")
        (pasta / "Verificar.java").write_text(
            fonte.replace("void main() {", "void aplicacao() {", 1) + HARNESS,
            encoding="utf-8",
        )
        executar(["javac", "-encoding", "UTF-8", "-d", str(pasta), "MicroFM.java", "Verificar.java"], cwd=pasta)
        entradas = {
            "calendario": "", "onze": "", "treino": "2\n0\n1\n3\n", "mercado": "",
            "substituicoes": "3\n1\n2\n3\n1\n1\n3\n2\n7\n3\n3\n8\n3\n1\n",
            "persistencia": "", "campo": "",
        }
        for teste, entrada in entradas.items():
            saida = executar(["java", *JAVA_OPTIONS, "-cp", str(pasta), "Verificar", teste], cwd=pasta, entrada=entrada)
            assert f"PASSOU {teste}" in saida, teste
            if teste == "campo":
                assert "\x1b" not in saida, "Campo nao deve usar comandos ANSI"
                quadros = saida.split("  MICRO FM | CENTRAL DE JOGO\n")[1:]
                assert len(quadros) == 2, "Devem ser desenhados dois quadros"
                for quadro, minuto, golos, bola_x, lance, golo in zip(
                    quadros, (31, 32), (2, 3), (20, 40),
                    ("Passe curto QA", "Remate QA"), ("Ultimo golo QA", "Novo golo QA"),
                ):
                    linhas = quadro.splitlines()
                    assert f"{minuto}:00   Casa QA {golos} - 1 Fora QA" in linhas[0]
                    assert "Remates 7 - 4   Posse 55% - 45%" in linhas[1]
                    assert "Tática: Equilibrada" in linhas[1]
                    campo = [linha[2:] for linha in linhas[2:19]]
                    assert len(campo) == 17 and all(len(linha) == 65 for linha in campo)
                    assert sum(linha.count("A") for linha in campo) == 11
                    assert sum(linha.count("B") for linha in campo) == 11
                    assert sum(linha.count("o") for linha in campo) == 1
                    assert campo[8][bola_x] == "o", "Bola desenhada na posicao errada"
                    assert "A Casa QA   B Fora QA   o bola" in linhas[19]
                    assert lance in linhas[20] and golo in linhas[21]
            print(f"OK: {teste}")

        # Menus reais: validacao de entradas, 14 jornadas, 56 pausas e guardar/sair.
        campanha = pasta / "campanha"
        campanha.mkdir()
        comandos = ["abc", "", "-1", "999999999999999999999", "1", "QA Mister", "1", "10", "4", "0"]
        for _ in range(14):
            comandos += ["1", "1", "1", "1", "1", "1", ""]
        comandos += ["0", "0"]
        comando_jogo = ["java", *JAVA_OPTIONS, "-cp", str(pasta), "MicroFM"]
        saida = executar(comando_jogo, cwd=campanha, entrada="\n".join(comandos) + "\n")
        assert saida.count("APITO FINAL") == 14, "Nem todos os jogos terminaram"
        assert saida.count("BANCO |") == 56, "Faltaram pausas de decisao"
        assert "Escreve um número inteiro" in saida and "Escolhe um número entre" in saida
        fim = ler_carreira(campanha)
        assert fim["c"][1:3] == [14, 1], "Epoca incompleta"
        assert all(g[2] >= 0 and g[3] >= 0 for g in fim["calendario"])
        assert all(t[1] == 14 and t[0] == 3 * t[2] + t[3] for t in fim["tabela"])
        assert sum(t[5] for t in fim["tabela"]) == sum(t[6] for t in fim["tabela"])
        assert sum(j[4] for j in fim["jogadores"][:fim["c"][10]]) == fim["tabela"][0][5]
        print("OK: integração de 14 jornadas, classificação e estatísticas")

        # Recuperar uma epoca concluida deve validar e transitar o plantel e saldo.
        saida = executar(comando_jogo, cwd=campanha, entrada="2\n6\n\n7\n\n11\n0\n0\n")
        assert "Carreira carregada" in saida and "ÉPOCA TERMINADA" in saida, "Nao recuperou epoca terminada"
        nova = ler_carreira(campanha)
        assert nova["c"][1:3] == [0, 2] and nova["c"][3] == fim["c"][3]
        assert all(v == 0 for t in nova["tabela"] for v in t)
        assert all(g[2:] == [-1, -1] for g in nova["calendario"])
        for antes, depois in zip(fim["jogadores"][:20], nova["jogadores"][:20]):
            assert depois[1] == min(antes[1] + 1, 60)
            assert depois[3:] == [100, 0, 0, 0]
        print("OK: carregar época terminada e começar nova época")
    print("Todas as verificações passaram; nenhuma carreira do utilizador foi alterada.")


if __name__ == "__main__":
    main()
