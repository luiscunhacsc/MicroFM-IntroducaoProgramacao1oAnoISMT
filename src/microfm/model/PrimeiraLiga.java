package microfm.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dados de arranque da época 2026/27, incluídos no JAR. */
final class PrimeiraLiga {
    static final List<Club> CLUBS = List.of(
        new Club(0, "Académico de Viseu", "AVFC", "Viseu", 0x8F9DAA, 69),
        new Club(1, "FC Alverca", "ALV", "Alverca do Ribatejo", 0x3E82DA, 69),
        new Club(2, "FC Arouca", "FCA", "Arouca", 0xE7B644, 72),
        new Club(3, "Casa Pia AC", "CPAC", "Lisboa", 0xB0B7C5, 69),
        new Club(4, "CD Nacional", "CDN", "Funchal", 0xA8B0C0, 68),
        new Club(5, "Estoril Praia", "EPF", "Estoril", 0xF0CB46, 71),
        new Club(6, "Estrela Amadora", "CFEA", "Amadora", 0x4AB88B, 69),
        new Club(7, "FC Famalicão", "FCF", "Vila Nova de Famalicão", 0x526ACC, 74),
        new Club(8, "Gil Vicente FC", "GVFC", "Barcelos", 0xE75B64, 75),
        new Club(9, "CS Marítimo", "CSM", "Funchal", 0x5AAA67, 68),
        new Club(10, "Moreirense FC", "MFC", "Moreira de Cónegos", 0x61AA71, 73),
        new Club(11, "FC Porto", "FCP", "Porto", 0x438DF1, 86),
        new Club(12, "Rio Ave FC", "RAFC", "Vila do Conde", 0x54A982, 70),
        new Club(13, "Santa Clara", "CDSC", "Ponta Delgada", 0xD05B61, 74),
        new Club(14, "SC Braga", "SCB", "Braga", 0xE36563, 81),
        new Club(15, "SL Benfica", "SLB", "Lisboa", 0xE44950, 86),
        new Club(16, "Sporting CP", "SCP", "Lisboa", 0x4DB880, 85),
        new Club(17, "Vitória SC", "VSC", "Guimarães", 0xBBC1CC, 76));

    private static final LocalDate ROSTER_DATE = LocalDate.of(2026, 11, 8);
    private static final String ROSTERS = "primeira-liga-2026-27.tsv";
    private static final String CALENDAR = "calendario-2026-27.txt";
    private static List<List<RosterPlayer>> rosters;
    private static List<FirstLegMatch> firstLeg;

    private record RosterPlayer(String name, Position position, int age) { }
    private record FirstLegMatch(int round, int home, int away) { }

    private PrimeiraLiga() { }

    static List<Player> squad(Club club) {
        List<RosterPlayer> roster = rosters().get(club.id());
        List<Player> players = new ArrayList<>(roster.size());
        for (int index = 0; index < roster.size(); index++) {
            RosterPlayer entry = roster.get(index);
            int id = 10_000 + club.id() * 100 + index;
            int variation = Math.floorMod(entry.name().hashCode(), 9) - 4;
            int quality = club.strength() + variation - Math.max(0, 24 - entry.age());
            players.add(new Player(id, entry.name(), entry.position(), entry.age(), quality, 100, 0, 0, 0));
        }
        return players;
    }

    static List<Fixture> fixtures() {
        List<FirstLegMatch> matches = firstLeg();
        List<Fixture> result = new ArrayList<>(matches.size() * 2);
        int firstLegRounds = CLUBS.size() - 1;
        for (FirstLegMatch match : matches) result.add(new Fixture(match.home(), match.away(), match.round()));
        for (FirstLegMatch match : matches) result.add(new Fixture(match.away(), match.home(), match.round() + firstLegRounds));
        return result;
    }

    private static synchronized List<List<RosterPlayer>> rosters() {
        if (rosters != null) return rosters;
        List<List<RosterPlayer>> loaded = new ArrayList<>();
        for (Club club : CLUBS) loaded.add(new ArrayList<>());
        try {
            for (String line : lines(ROSTERS)) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 4 || fields[1].isBlank() || fields[1].length() > 80)
                    throw new IllegalArgumentException("Linha de plantel inválida.");
                List<RosterPlayer> roster = loaded.get(Integer.parseInt(fields[0]));
                int age = Period.between(LocalDate.parse(fields[3]), ROSTER_DATE).getYears();
                if (age < Player.MIN_AGE || age > Player.MAX_AGE)
                    throw new IllegalArgumentException("Idade inválida para " + fields[1]);
                roster.add(new RosterPlayer(fields[1], Position.valueOf(fields[2]), age));
            }
            for (Club club : CLUBS) {
                List<RosterPlayer> roster = loaded.get(club.id());
                if (roster.size() < 20 || roster.size() > League.PRIMEIRA_LIGA_2026_27.squadLimit()
                    || roster.stream().noneMatch(p -> p.position() == Position.GOALKEEPER))
                    throw new IllegalArgumentException("Plantel inválido: " + club.name());
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Não foi possível importar os plantéis da Primeira Liga 2026/27.", e);
        }
        rosters = loaded.stream().map(List::copyOf).toList();
        return rosters;
    }

    private static synchronized List<FirstLegMatch> firstLeg() {
        if (firstLeg != null) return firstLeg;
        List<FirstLegMatch> loaded = new ArrayList<>();
        List<String> rounds = lines(CALENDAR);
        int clubCount = CLUBS.size();
        boolean[][] played = new boolean[clubCount][clubCount];
        try {
            if (rounds.size() != clubCount - 1) throw new IllegalArgumentException("Número de jornadas inválido.");
            for (int round = 0; round < rounds.size(); round++) {
                Set<Integer> roundClubs = new HashSet<>();
                for (String pair : rounds.get(round).split(" ")) {
                    String[] ids = pair.split("-", -1);
                    if (ids.length != 2) throw new IllegalArgumentException("Encontro inválido.");
                    int home = Integer.parseInt(ids[0]), away = Integer.parseInt(ids[1]);
                    if (!roundClubs.add(home) || !roundClubs.add(away) || played[home][away])
                        throw new IllegalArgumentException("Encontro repetido ou inválido.");
                    played[home][away] = played[away][home] = true;
                    loaded.add(new FirstLegMatch(round, home, away));
                }
                if (roundClubs.size() != clubCount) throw new IllegalArgumentException("Jornada incompleta.");
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Não foi possível importar o calendário da Primeira Liga 2026/27.", e);
        }
        firstLeg = List.copyOf(loaded);
        return firstLeg;
    }

    private static List<String> lines(String file) {
        InputStream input = PrimeiraLiga.class.getResourceAsStream("/microfm/data/" + file);
        if (input == null) throw new IllegalStateException("Falta o ficheiro de dados " + file + ".");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String> result = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null;) {
                if (!line.isBlank() && !line.startsWith("#")) result.add(line);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler os dados de " + file + ".", e);
        }
    }
}
