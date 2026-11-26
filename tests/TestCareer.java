import microfm.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Testes de comportamento sem bibliotecas externas; os ficheiros ficam numa pasta temporária. */
public final class TestCareer {
    private static int checks;

    @FunctionalInterface
    private interface Scenario { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        scenario("calendário completo, sem repetições", TestCareer::calendar);
        scenario("Primeira Liga 2026/27 com calendários, plantéis e gravação", TestCareer::primeiraLiga);
        scenario("seleção e formações para os oito clubes", TestCareer::lineups);
        scenario("troca de titulares com validação", TestCareer::swaps);
        scenario("uma sessão de treino em cada jornada", TestCareer::training);
        scenario("contratação debita exatamente uma vez", TestCareer::transfers);
        scenario("resultado atualiza jogadores e classificação", TestCareer::matchResult);
        scenario("suplentes participantes recebem presença e golos", TestCareer::participants);
        scenario("época completa e transição de época", TestCareer::season);
        scenario("mesma semente reproduz o mesmo cenário", TestCareer::determinism);
        scenario("guardar e recuperar a carreira em UTF-8", TestCareer::persistence);
        scenario("gravações incompletas são rejeitadas", TestCareer::badSaves);
        scenario("gravações incoerentes não contornam as regras", TestCareer::inconsistentSaves);
        System.out.println("OK: " + checks + " verificações da carreira.");
    }

    private static void scenario(String name, Scenario scenario) throws Exception {
        scenario.run();
        System.out.println("  PASSOU · " + name);
    }

    private static Career career() { return Career.create("Luís · Treinador", 0, 74123L); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejected(Runnable action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void calendar() {
        Career c = career();
        check(c.clubs().size() == 8 && c.fixtures().size() == 56, "Dimensão da liga");
        int[][] pairs = new int[8][8];
        for (int round = 0; round < 14; round++) {
            Set<Integer> seen = new HashSet<>();
            int games = 0;
            for (Fixture f : c.fixtures()) {
                if (f.round() != round) continue;
                check(f.homeId() != f.awayId(), "Um clube não pode jogar consigo próprio");
                check(seen.add(f.homeId()) && seen.add(f.awayId()), "Clube repetido na jornada");
                check(!f.played(), "Resultado antes de disputar o jogo");
                pairs[f.homeId()][f.awayId()]++;
                games++;
            }
            check(games == 4 && seen.size() == 8, "Cada jornada tem quatro jogos e oito clubes");
        }
        for (int a = 0; a < 8; a++)
            for (int b = 0; b < 8; b++)
                check(pairs[a][b] == (a == b ? 0 : 1), "Cada par deve ter um encontro em cada estádio");
        check(c.nextFixture() != null && c.nextFixture().round() == 0, "Próximo jogo inicial");
    }

    private static void lineups() {
        for (Club club : Club.all()) {
            Career c = Career.create("Mister", club.id(), 200 + club.id());
            for (Formation formation : Formation.values()) {
                c.setFormation(formation);
                c.autoSelect();
                Set<Integer> ids = new HashSet<>();
                check(c.lineup().size() == 11, "Onze incompleto");
                for (int slot = 0; slot < 11; slot++) {
                    Player p = c.lineup().get(slot);
                    check(ids.add(p.id()), "Jogador repetido no onze");
                    check(p.available(), "Jogador indisponível escolhido");
                    check(p.position() == formation.positionAt(slot), "Posição incompatível com a formação");
                }
                double[][] points = formation.positions();
                check(points.length == 11, "Onze coordenadas táticas");
                for (double[] point : points)
                    check(point.length == 2 && point[0] >= 0 && point[0] <= 1 && point[1] >= 0 && point[1] <= 1,
                        "Coordenadas fora do campo");
            }
        }
        double[][] copy = Formation.FOUR_THREE_THREE.positions();
        copy[0][0] = -100;
        check(Formation.FOUR_THREE_THREE.positions()[0][0] >= 0, "Não deve ser possível alterar as coordenadas internas");
    }

    private static void primeiraLiga() throws IOException {
        League league = League.PRIMEIRA_LIGA_2026_27;
        check(league.clubs().size() == 18 && league.roundCount() == 34 && league.fixtureCount() == 306,
            "Dimensão da Primeira Liga");
        Set<Integer> ids = new HashSet<>();
        for (Club club : league.clubs()) {
            Career c = Career.create("Mister", club.id(), 42, league);
            check(c.squad().size() >= 20 && c.squad().stream().allMatch(p -> ids.add(p.id())), "Plantel ausente ou repetido");
            check(c.fixtures().size() == 306 && c.standings().size() == 18, "Calendário ou classificação incompletos");
            for (Formation formation : Formation.values()) {
                c.setFormation(formation);
                for (int slot = 0; slot < 11; slot++)
                    check(c.lineup().get(slot).position() == formation.positionAt(slot), "Posição incompatível na Primeira Liga");
            }
        }
        check(ids.size() == 577, "Número de jogadores no retrato da 11.ª jornada");
        Career porto = Career.create("Rita", 11, 42, league);
        check(porto.squad().stream().anyMatch(p -> p.name().equals("Diogo Costa") && p.age() == 27
            && p.position() == Position.GOALKEEPER), "Plantel oficial do FC Porto");
        check(porto.nextFixture().homeId() == 11 && porto.nextFixture().awayId() == 1, "FC Porto – FC Alverca na jornada 1");
        check(porto.fixtures().stream().anyMatch(f -> f.round() == 17 && f.homeId() == 1 && f.awayId() == 11),
            "FC Alverca – FC Porto na jornada 18");
        check(porto.opponentLineup().size() == 11, "Onze adversário em falta");
        for (int round = 0; round < 34; round++) porto.finishMatch(1, 0, Map.of());
        check(porto.nextFixture() == null && porto.fixtures().stream().allMatch(Fixture::played), "Época real incompleta");

        Path directory = Files.createTempDirectory("microfm-primeira-liga-");
        try {
            Path file = directory.resolve("primeira-liga.microfm");
            CareerStore.save(porto, file);
            Career restored = CareerStore.load(file);
            check(restored.league() == league && snapshot(porto).equals(snapshot(restored)), "Gravação da Primeira Liga alterada");
            restored.nextSeason();
            check(restored.round() == 0 && restored.season() == 2 && restored.fixtures().size() == 306, "Segunda época inválida");
        } finally {
            removeTemporaryDirectory(directory);
        }
    }

    private static void swaps() {
        Career c = career();
        Player original = c.lineup().get(0);
        Set<Integer> starters = new HashSet<>();
        c.lineup().forEach(p -> starters.add(p.id()));
        Player reserve = c.squad().stream()
            .filter(p -> !starters.contains(p.id()) && p.position() == original.position() && p.available())
            .findFirst().orElseThrow();
        c.swapStarter(0, reserve.id());
        check(c.lineup().get(0).id() == reserve.id(), "Suplente não entrou no onze");
        check(c.lineup().stream().noneMatch(p -> p.id() == original.id()), "Titular anterior permaneceu no onze");
        rejected(() -> c.swapStarter(-1, original.id()), "Lugar negativo aceite");
        rejected(() -> c.swapStarter(11, original.id()), "Lugar fora do onze aceite");
        rejected(() -> c.swapStarter(0, Integer.MAX_VALUE), "Jogador inexistente aceite");
    }

    private static void training() {
        for (Training type : Training.values()) {
            Career c = career();
            check(!c.trained(), "Treino inicial já gasto");
            c.train(type);
            check(c.trained(), "Sessão não foi consumida");
            String after = snapshot(c);
            rejected(() -> c.train(type), "Segundo treino da jornada aceite");
            check(snapshot(c).equals(after), "Treino rejeitado alterou o estado");
            for (Player p : c.squad()) {
                check(p.condition() >= 0 && p.condition() <= 100, "Condição fora dos limites");
                check(p.quality() >= 1 && p.quality() <= 99, "Qualidade fora dos limites");
            }
            c.finishMatch(0, 0, Map.of());
            check(!c.trained(), "Treino não renovou na jornada seguinte");
        }
    }

    private static void transfers() {
        Career c = career();
        MarketOffer offer = c.market().stream().filter(o -> o.price() <= c.balance()).findFirst().orElseThrow();
        int previousBalance = c.balance(), previousSize = c.squad().size();
        c.sign(offer.player().id());
        check(c.balance() == previousBalance - offer.price(), "Débito errado na contratação");
        check(c.squad().size() == previousSize + 1, "Reforço não entrou no plantel");
        check(c.squad().stream().anyMatch(p -> p.id() == offer.player().id()), "Identidade do reforço perdida");
        check(offer.signed(), "Oferta não marcada como contratada");
        String after = snapshot(c);
        rejected(() -> c.sign(offer.player().id()), "Dupla contratação aceite");
        rejected(() -> c.sign(Integer.MAX_VALUE), "Contratação inexistente aceite");
        check(snapshot(c).equals(after), "Contratação rejeitada alterou o estado");

        // Consumir o orçamento com ofertas acessíveis; a primeira demasiado cara tem de ser rejeitada.
        for (MarketOffer candidate : c.market())
            if (!candidate.signed() && candidate.price() <= c.balance()) c.sign(candidate.player().id());
        MarketOffer expensive = c.market().stream().filter(o -> !o.signed() && o.price() > c.balance())
            .findFirst().orElseThrow();
        int balance = c.balance(), size = c.squad().size();
        rejected(() -> c.sign(expensive.player().id()), "Saldo insuficiente não foi verificado");
        check(c.balance() == balance && c.squad().size() == size, "Compra sem saldo alterou o clube");
        check(c.balance() >= 0, "Orçamento ficou negativo");
    }

    private static void matchResult() {
        Career c = career();
        Fixture fixture = c.nextFixture();
        int scorer = c.lineup().stream().filter(p -> p.position() == Position.FORWARD).findFirst().orElseThrow().id();
        boolean home = fixture.homeId() == c.club().id();
        c.finishMatch(home ? 2 : 1, home ? 1 : 2, Map.of(scorer, 2));
        check(c.round() == 1 && fixture.played(), "Resultado não fez avançar a jornada");
        check(fixture.homeGoals() == (home ? 2 : 1) && fixture.awayGoals() == (home ? 1 : 2), "Resultado trocado");
        check(c.fixtures().stream().filter(Fixture::played).count() == 4, "Jornada incompleta");
        Standing standing = c.standings().stream().filter(s -> s.clubId() == c.club().id()).findFirst().orElseThrow();
        check(standing.points() == 3 && standing.wins() == 1 && standing.played() == 1, "Vitória não aplicada à tabela");
        check(standing.goalsFor() == 2 && standing.goalsAgainst() == 1, "Golos incorretos na tabela");
        check(c.squad().stream().filter(p -> p.id() == scorer).findFirst().orElseThrow().goals() == 2,
            "Golos do marcador não atualizados");
        String before = snapshot(c);
        rejected(() -> c.finishMatch(-1, 0, Map.of()), "Resultado negativo aceite");
        check(snapshot(c).equals(before), "Resultado rejeitado alterou a carreira");
    }

    private static void participants() {
        Career c = career();
        Set<Integer> playing = new HashSet<>();
        c.lineup().forEach(p -> playing.add(p.id()));
        Player reserve = c.squad().stream().filter(p -> !playing.contains(p.id()) && p.available()).findFirst().orElseThrow();
        int starter = c.lineup().get(10).id();
        int homeGoals = c.nextFixture().homeId() == c.club().id() ? 2 : 0;
        int awayGoals = homeGoals == 2 ? 0 : 2;
        String before = snapshot(c);
        rejected(() -> c.finishMatch(homeGoals, awayGoals, Map.of(reserve.id(), 1), playing),
            "Golo de jogador fora dos participantes aceite");
        Set<Integer> shortTeam = new HashSet<>(playing);
        shortTeam.remove(starter);
        rejected(() -> c.finishMatch(homeGoals, awayGoals, Map.of(), shortTeam), "Equipa com dez participantes aceite");
        Set<Integer> unknownPlayer = new HashSet<>(playing);
        unknownPlayer.add(Integer.MAX_VALUE);
        rejected(() -> c.finishMatch(homeGoals, awayGoals, Map.of(), unknownPlayer), "Participante fora do plantel aceite");
        check(snapshot(c).equals(before), "Participantes rejeitados alteraram a carreira");
        playing.add(reserve.id());
        c.finishMatch(homeGoals, awayGoals, Map.of(starter, 1, reserve.id(), 1), playing);
        check(reserve.appearances() == 1 && reserve.goals() == 1, "Suplente não recebeu presença ou golo");
        for (Player p : c.squad())
            check(p.appearances() == (playing.contains(p.id()) ? 1 : 0), "Presença incorreta para " + p.name());
        check(c.squad().stream().mapToInt(Player::goals).sum() == 2, "Golos duplicados entre titular e suplente");
    }

    private static void season() {
        Career c = career();
        rejected(c::nextSeason, "Nova época antes do final aceite");
        for (int round = 0; round < 14; round++) c.finishMatch(round % 3, (round + 1) % 3, Map.of());
        check(c.round() == 14 && c.nextFixture() == null, "Fim da época incorreto");
        check(c.fixtures().stream().allMatch(Fixture::played), "Ficaram jogos por disputar");
        int goalsFor = 0, goalsAgainst = 0, previousPoints = Integer.MAX_VALUE;
        for (Standing s : c.standings()) {
            check(s.played() == 14, "Clube não disputou 14 jogos");
            check(s.played() == s.wins() + s.draws() + s.losses(), "Contabilidade dos resultados");
            check(s.points() == s.wins() * 3 + s.draws(), "Regra dos pontos");
            check(s.points() <= previousPoints, "Classificação não ordenada pelos pontos");
            previousPoints = s.points(); goalsFor += s.goalsFor(); goalsAgainst += s.goalsAgainst();
        }
        check(goalsFor == goalsAgainst, "Golos totais marcados e sofridos não coincidem");
        rejected(() -> c.finishMatch(0, 0, Map.of()), "Décima quinta jornada aceite");
        rejected(() -> c.train(Training.RECOVERY), "Treino após final da época aceite");
        c.nextSeason();
        check(c.season() == 2 && c.round() == 0 && c.nextFixture() != null, "Transição de época");
        check(c.fixtures().stream().noneMatch(Fixture::played), "Resultados antigos na época nova");
        check(c.standings().stream().allMatch(s -> s.points() == 0 && s.played() == 0), "Tabela não foi limpa");
        check(c.squad().stream().allMatch(p -> p.goals() == 0 && p.appearances() == 0), "Estatísticas antigas na época nova");
    }

    private static void determinism() {
        Career a = career(), b = career();
        check(snapshot(a).equals(snapshot(b)), "Criação com a mesma semente difere");
        for (int round = 0; round < 5; round++) {
            a.train(Training.TECHNICAL); b.train(Training.TECHNICAL);
            a.finishMatch(2, 1, Map.of()); b.finishMatch(2, 1, Map.of());
            check(snapshot(a).equals(snapshot(b)), "Simulação determinística divergiu na jornada " + round);
        }
    }

    private static void persistence() throws Exception {
        Career original = career();
        original.setFormation(Formation.FOUR_FOUR_TWO);
        original.setMentality(Mentality.ATTACKING);
        original.sign(original.market().get(0).player().id());
        original.train(Training.INTENSIVE);
        original.finishMatch(2, 1, Map.of());
        Path directory = Files.createTempDirectory("microfm-career-test-");
        Path file = directory.resolve("carreira-swing.microfm");
        try {
            CareerStore.save(original, file);
            check(Files.size(file) > 0, "Gravação vazia");
            Career restored = CareerStore.load(file);
            check(snapshot(original).equals(snapshot(restored)), "A carreira recuperada difere da original");
            check(restored.manager().equals("Luís · Treinador"), "Acentos no nome do treinador perderam-se");
            original.train(Training.RECOVERY);
            CareerStore.save(original, file);
            check(snapshot(original).equals(snapshot(CareerStore.load(file))), "Guardar por cima não atualizou o ficheiro");
            original.finishMatch(1, 1, Map.of());
            restored = CareerStore.load(file);
            restored.finishMatch(1, 1, Map.of());
            check(snapshot(original).equals(snapshot(restored)), "Estado aleatório não foi preservado entre sessões");

            Properties legacy = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { legacy.load(reader); }
            legacy.setProperty("version", "1");
            legacy.remove("league");
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { legacy.store(writer, "MicroFM v1"); }
            Career restoredLegacy = CareerStore.load(file);
            restoredLegacy.finishMatch(1, 1, Map.of());
            check(restoredLegacy.league() == League.FICTIONAL && snapshot(original).equals(snapshot(restoredLegacy)),
                "Gravação da versão 1 já não pode ser retomada");
        } finally { removeTemporaryDirectory(directory); }
    }

    private static void badSaves() throws Exception {
        Path directory = Files.createTempDirectory("microfm-invalid-test-");
        Path file = directory.resolve("invalid.microfm");
        try {
            for (String invalid : List.of("", "isto não é uma carreira", "version=999\n", "manager=Luís\nround=-1\n")) {
                Files.writeString(file, invalid, StandardCharsets.UTF_8);
                try { CareerStore.load(file); }
                catch (IOException expected) { checks++; continue; }
                throw new AssertionError("Gravação inválida aceite: " + invalid);
            }
            Files.delete(file);
            try { CareerStore.load(file); }
            catch (IOException expected) { checks++; return; }
            throw new AssertionError("Gravação inexistente aceite");
        } finally { removeTemporaryDirectory(directory); }
    }

    private static void inconsistentSaves() throws Exception {
        Path directory = Files.createTempDirectory("microfm-inconsistent-test-");
        Path file = directory.resolve("invalid.microfm");
        Career c = career();
        try {
            CareerStore.save(c, file);
            Properties valid = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { valid.load(reader); }
            List<Map.Entry<String, String>> corruptions = List.of(
                Map.entry("balance", "-1"),
                Map.entry("round", "15"),
                Map.entry("lineup.0", valid.getProperty("lineup.1")),
                Map.entry("squad.0.condition", "101"),
                Map.entry("squad.0.quality", "0"),
                Map.entry("fixture.0.away", valid.getProperty("fixture.0.home")),
                Map.entry("fixture.0.hg", "1"),
                Map.entry("trained", "talvez"),
                Map.entry("formation", "FORMAÇÃO_DESCONHECIDA")
            );
            for (Map.Entry<String, String> change : corruptions) {
                Properties broken = new Properties();
                broken.putAll(valid);
                broken.setProperty(change.getKey(), change.getValue());
                try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { broken.store(writer, "teste"); }
                try { CareerStore.load(file); }
                catch (IOException expected) { checks++; continue; }
                throw new AssertionError("Estado incoerente aceite: " + change.getKey());
            }
            check(c.round() == 0 && c.balance() > 0, "Carregamento inválido afetou a carreira existente");
        } finally { removeTemporaryDirectory(directory); }
    }

    private static String snapshot(Career c) {
        StringBuilder s = new StringBuilder();
        s.append(c.manager()).append('|').append(c.club().id()).append('|').append(c.round())
            .append('|').append(c.season()).append('|').append(c.balance()).append('|').append(c.morale())
            .append('|').append(c.formation()).append('|').append(c.mentality()).append('|').append(c.trained());
        for (Player p : c.squad()) {
            s.append("\nP|").append(p.id()).append('|').append(p.name()).append('|').append(p.position())
                .append('|').append(p.age()).append('|').append(p.quality()).append('|').append(p.condition())
                .append('|').append(p.goals()).append('|').append(p.appearances()).append('|').append(p.injuredRounds());
        }
        for (Player p : c.lineup()) s.append("\nL|").append(p.id());
        for (MarketOffer m : c.market()) s.append("\nM|").append(m.player().id()).append('|').append(m.price()).append('|').append(m.signed());
        for (Fixture f : c.fixtures()) s.append("\nF|").append(f.round()).append('|').append(f.homeId()).append('|').append(f.awayId())
            .append('|').append(f.homeGoals()).append('|').append(f.awayGoals());
        for (Standing t : c.standings()) s.append("\nS|").append(t.clubId()).append('|').append(t.played()).append('|').append(t.wins())
            .append('|').append(t.draws()).append('|').append(t.losses()).append('|').append(t.goalsFor()).append('|').append(t.goalsAgainst()).append('|').append(t.points());
        c.news().forEach(n -> s.append("\nN|").append(n));
        return s.toString();
    }

    private static void removeTemporaryDirectory(Path directory) throws IOException {
        // O caminho é criado pelo próprio teste e só contém os seus ficheiros temporários.
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) Files.deleteIfExists(child);
        }
        Files.deleteIfExists(directory);
    }
}
