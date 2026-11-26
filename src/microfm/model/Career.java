package microfm.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Agregado da carreira: a interface pede ações e este objeto garante as regras.
 * Não conhece Swing, relógios, janelas nem ficheiros; pode ser testado sem ecrã.
 */
public final class Career {
    private final String manager;
    private final int clubId;
    private final long seed;
    private final League league;
    private int season = 1, round, balance = 2_400_000, morale = 76;
    private Formation formation = Formation.FOUR_THREE_THREE;
    private Mentality mentality = Mentality.BALANCED;
    private boolean trained;
    private final List<Player> squad = new ArrayList<>();
    private final List<Player> lineup = new ArrayList<>();
    private final List<MarketOffer> market = new ArrayList<>();
    private final List<Fixture> fixtures = new ArrayList<>();
    private final List<String> news = new ArrayList<>();

    private Career(String manager, int clubId, long seed, League league) {
        if (manager == null || manager.isBlank() || manager.length() > 40)
            throw new IllegalArgumentException("O nome do treinador deve ter entre 1 e 40 caracteres.");
        if (league == null) throw new IllegalArgumentException("Liga em falta.");
        if (clubId < 0 || clubId >= league.clubs().size()) throw new IllegalArgumentException("Clube inválido.");
        this.manager = manager.strip(); this.clubId = clubId; this.seed = seed; this.league = league;
    }

    public static Career create(String manager, int clubId, long seed) {
        return create(manager, clubId, seed, League.FICTIONAL);
    }

    public static Career create(String manager, int clubId, long seed, League league) {
        Career career = new Career(manager, clubId, seed, league);
        if (league == League.PRIMEIRA_LIGA_2026_27) career.squad.addAll(PrimeiraLiga.squad(career.club()));
        else career.createFictionalSquad();
        career.createFixtures(); career.createMarket(); career.autoSelect();
        career.addNews("Bem-vindo, " + career.manager + ". A direção pede um lugar na primeira metade da tabela.");
        return career;
    }

    private void createFictionalSquad() {
        String[] names = { "Tomás Neves", "Rui Valente", "Diogo Serra", "João Matos", "André Ramos",
            "Miguel Costa", "Nuno Reis", "Tiago Lemos", "Pedro Faria", "Duarte Silva", "Afonso Maia",
            "Vasco Melo", "Bruno Pires", "Gonçalo Dias", "Rafael Luz", "Henrique Sá", "Salvador Cruz",
            "Martim Rocha", "Daniel Gama", "Ivo Paiva", "Lourenço Leal", "Rodrigo Vale" };
        Random random = new Random(seed);
        for (int i = 0; i < names.length; i++) {
            Position position = i < 2 ? Position.GOALKEEPER : i < 10 ? Position.DEFENDER
                : i < 17 ? Position.MIDFIELDER : Position.FORWARD;
            squad.add(new Player(i, names[i], position, 18 + random.nextInt(15),
                clamp(club().strength() - 8 + random.nextInt(17), 45, 90), 100, 0, 0, 0));
        }
    }

    public String manager() { return manager; }
    public League league() { return league; }
    public Club club() { return clubs().get(clubId); }
    public List<Club> clubs() { return league.clubs(); }
    public int season() { return season; }
    public int round() { return round; }
    public int balance() { return balance; }
    public int morale() { return morale; }
    public Formation formation() { return formation; }
    public Mentality mentality() { return mentality; }
    public boolean trained() { return trained; }
    public List<Player> squad() { return List.copyOf(squad); }
    public List<Player> lineup() { return List.copyOf(lineup); }
    public List<MarketOffer> market() { return List.copyOf(market); }
    public List<Fixture> fixtures() { return List.copyOf(fixtures); }
    public List<String> news() { return List.copyOf(news); }
    long seed() { return seed; }

    public Fixture nextFixture() {
        return fixtures.stream().filter(f -> f.round() == round && (f.homeId() == clubId || f.awayId() == clubId))
            .findFirst().orElse(null);
    }

    public List<Player> opponentLineup() {
        Fixture fixture = nextFixture();
        if (league != League.PRIMEIRA_LIGA_2026_27 || fixture == null) return List.of();
        int opponentId = fixture.homeId() == clubId ? fixture.awayId() : fixture.homeId();
        return selectLineup(PrimeiraLiga.squad(clubs().get(opponentId)), Formation.FOUR_THREE_THREE);
    }

    /** A classificação deriva dos resultados: não há uma segunda tabela para dessincronizar. */
    public List<Standing> standings() {
        List<Standing> table = new ArrayList<>();
        for (Club club : clubs()) table.add(new Standing(club.id()));
        for (Fixture fixture : fixtures) if (fixture.played()) {
            table.get(fixture.homeId()).add(fixture.homeGoals(), fixture.awayGoals());
            table.get(fixture.awayId()).add(fixture.awayGoals(), fixture.homeGoals());
        }
        table.sort(Comparator.comparingInt(Standing::points).reversed()
            .thenComparing(Comparator.comparingInt((Standing s) -> s.goalsFor() - s.goalsAgainst()).reversed())
            .thenComparing(Comparator.comparingInt(Standing::goalsFor).reversed()).thenComparingInt(Standing::clubId));
        return List.copyOf(table);
    }

    public void setFormation(Formation formation) {
        if (formation == null) throw new IllegalArgumentException("Formação em falta.");
        this.formation = formation; autoSelect();
    }
    public void setMentality(Mentality mentality) {
        if (mentality == null) throw new IllegalArgumentException("Mentalidade em falta.");
        this.mentality = mentality;
    }

    public void autoSelect() {
        List<Player> chosen = selectLineup(squad, formation);
        lineup.clear(); lineup.addAll(chosen);
    }

    private static List<Player> selectLineup(List<Player> squad, Formation formation) {
        List<Player> chosen = new ArrayList<>();
        for (int slot = 0; slot < 11; slot++) {
            Position required = formation.positionAt(slot);
            Player best = squad.stream().filter(Player::available).filter(p -> !chosen.contains(p))
                .max(Comparator.comparingDouble(p -> selectionScore(p, required))).orElseThrow(
                    () -> new IllegalStateException("Não existem onze jogadores disponíveis."));
            chosen.add(best);
        }
        return chosen;
    }

    private static double selectionScore(Player player, Position required) {
        return player.quality() * .8 + player.condition() * .2 + (player.position() == required ? 100 : 0);
    }

    public void swapStarter(int lineupSlot, int reserveId) {
        if (lineupSlot < 0 || lineupSlot >= 11) throw new IllegalArgumentException("Seleciona um lugar do onze.");
        Player reserve = squad.stream().filter(p -> p.id() == reserveId).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Jogador fora do plantel."));
        if (!reserve.available()) throw new IllegalStateException("Este jogador está lesionado.");
        if (lineup.contains(reserve)) throw new IllegalStateException("Este jogador já está no onze.");
        lineup.set(lineupSlot, reserve);
    }

    public void train(Training training) {
        if (training == null) throw new IllegalArgumentException("Treino em falta.");
        if (round >= league.roundCount()) throw new IllegalStateException("A época terminou. Inicia uma nova época.");
        if (trained) throw new IllegalStateException("A sessão desta jornada já foi realizada.");
        for (Player player : squad) player.train(training);
        trained = true;
        morale = clamp(morale + (training == Training.RECOVERY ? 3 : 1), 25, 100);
        addNews("Treino de " + training.label().toLowerCase(java.util.Locale.forLanguageTag("pt-PT")) + " concluído.");
    }

    public void sign(int playerId) {
        MarketOffer offer = market.stream().filter(o -> o.player().id() == playerId).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Oferta indisponível."));
        if (offer.signed()) throw new IllegalStateException("Este jogador já foi contratado.");
        if (squad.size() >= league.squadLimit())
            throw new IllegalStateException("O plantel já atingiu o limite de " + league.squadLimit() + " jogadores.");
        if (balance < offer.price()) throw new IllegalStateException("O clube não tem saldo para esta contratação.");
        balance -= offer.price(); squad.add(offer.player()); offer.markSigned();
        addNews("Reforço confirmado: " + offer.player().name() + ".");
    }

    public void finishMatch(int homeGoals, int awayGoals, Map<Integer, Integer> scorerGoals) {
        Set<Integer> starters = new HashSet<>();
        for (Player player : lineup) starters.add(player.id());
        finishMatch(homeGoals, awayGoals, scorerGoals, starters);
    }

    /** Regista também os suplentes utilizados: jogar alguns minutos conta como presença. */
    public void finishMatch(int homeGoals, int awayGoals, Map<Integer, Integer> scorerGoals,
            Set<Integer> participants) {
        Fixture fixture = nextFixture();
        if (fixture == null) throw new IllegalStateException("Todas as jornadas desta época já foram disputadas.");
        if (homeGoals < 0 || homeGoals > 99 || awayGoals < 0 || awayGoals > 99)
            throw new IllegalArgumentException("O resultado deve conter golos entre 0 e 99.");
        if (scorerGoals == null) throw new IllegalArgumentException("Marcadores em falta.");
        if (participants == null || participants.size() < 11 || participants.size() > 16)
            throw new IllegalArgumentException("Um encontro deve ter entre 11 e 16 jogadores utilizados.");
        Set<Integer> used = new HashSet<>(participants);
        for (Integer id : used) {
            if (id == null || squad.stream().noneMatch(p -> p.id() == id && p.available()))
                throw new IllegalArgumentException("Jogador utilizado fora do plantel ou indisponível.");
        }
        int ownGoals = fixture.homeId() == clubId ? homeGoals : awayGoals;
        int conceded = fixture.homeId() == clubId ? awayGoals : homeGoals;
        long attributed = 0;
        for (Map.Entry<Integer, Integer> entry : scorerGoals.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0
                || !used.contains(entry.getKey()))
                throw new IllegalArgumentException("Marcadores inválidos.");
            attributed += entry.getValue();
        }
        if (attributed > ownGoals) throw new IllegalArgumentException("Os marcadores excedem os golos da equipa.");
        Random random = new Random(seed ^ (season * 9_973L + round * 104_729L));
        for (Fixture other : fixtures) if (other.round() == round) {
            if (other == fixture) other.result(homeGoals, awayGoals);
            else {
                double gap = (clubs().get(other.homeId()).strength() - clubs().get(other.awayId()).strength()) / 15.0;
                other.result(poisson(random, Math.max(.35, 1.45 + gap)), poisson(random, Math.max(.35, 1.15 - gap)));
            }
        }
        Player fallback = squad.stream().filter(p -> used.contains(p.id()))
            .max(Comparator.comparingInt(p -> p.position() == Position.FORWARD ? 1 : 0)).orElseThrow();
        int extraGoals = ownGoals - (int) attributed;
        int activeInjuries = (int) squad.stream().filter(p -> !p.available()).count();
        for (Player player : squad) {
            boolean played = used.contains(player.id());
            int scored = scorerGoals.getOrDefault(player.id(), 0) + (player == fallback ? extraGoals : 0);
            int injury = played && activeInjuries < 2 && random.nextDouble() < .018 ? 1 + random.nextInt(2) : 0;
            if (injury > 0) { activeInjuries++; addNews(player.name() + " ficará indisponível durante " + injury + " jornada(s)."); }
            player.finishWeek(played, scored, 13 + random.nextInt(8), injury);
        }
        int change = ownGoals > conceded ? 6 : ownGoals == conceded ? 1 : -5;
        morale = clamp(morale + change, 25, 100);
        int receipts = fixture.homeId() == clubId ? 150_000 : 90_000;
        int wages = 55_000 + squad.size() * 1_100;
        balance = Math.max(0, balance + receipts - wages + (ownGoals > conceded ? 45_000 : 0));
        addNews("Jornada " + (round + 1) + ": " + clubs().get(fixture.homeId()).shortName() + " "
            + homeGoals + "–" + awayGoals + " " + clubs().get(fixture.awayId()).shortName() + ".");
        round++; trained = false; autoSelect();
        if (round == league.roundCount()) {
            int rank = 1;
            for (Standing standing : standings()) { if (standing.clubId() == clubId) break; rank++; }
            int prize = (clubs().size() + 1 - rank) * 125_000;
            balance += prize;
            addNews("Época concluída em " + rank + ".º lugar! Prémio de classificação: " + prize + " €.");
        }
    }

    public void nextSeason() {
        if (round != league.roundCount())
            throw new IllegalStateException("Conclui as " + league.roundCount() + " jornadas antes de iniciar uma nova época.");
        if (season >= 30) throw new IllegalStateException("Concluíste 30 épocas. Inicia uma nova carreira para continuar.");
        season++; round = 0; trained = false; morale = clamp(morale + 5, 25, 100);
        balance += 350_000;
        for (Player player : squad) player.newSeason();
        fixtures.clear(); createFixtures(); createMarket(); autoSelect();
        addNews("Começa a época " + season + ". A direção disponibilizou 350 000 € de patrocínios.");
    }

    private void createFixtures() {
        if (league == League.PRIMEIRA_LIGA_2026_27 && season == 1) {
            fixtures.addAll(PrimeiraLiga.fixtures());
            return;
        }
        int clubCount = clubs().size();
        int firstLegRounds = clubCount - 1;
        int[] wheel = new int[clubCount];
        for (int i = 0; i < clubCount; i++) wheel[i] = i;
        List<Fixture> returnLeg = new ArrayList<>();
        for (int week = 0; week < firstLegRounds; week++) {
            for (int pair = 0; pair < clubCount / 2; pair++) {
                int home = wheel[pair], away = wheel[firstLegRounds - pair];
                if ((week & 1) == 1) { int swap = home; home = away; away = swap; }
                fixtures.add(new Fixture(home, away, week));
                returnLeg.add(new Fixture(away, home, week + firstLegRounds));
            }
            int last = wheel[firstLegRounds];
            System.arraycopy(wheel, 1, wheel, 2, clubCount - 2); wheel[1] = last;
        }
        fixtures.addAll(returnLeg);
    }

    private void createMarket() {
        market.clear();
        String[] names = { "Gabriel Teixeira", "Simão Baptista", "Leonardo Moura", "Francisco Vaz",
            "Bernardo Nobre", "David Coelho", "Samuel Esteves", "Lucas Pacheco" };
        Random random = new Random(seed ^ (season * 65_537L));
        for (int i = 0; i < names.length; i++) {
            int quality = 65 + random.nextInt(21);
            int id = 1_000 + season * 100 + i;
            Player player = new Player(id, names[i], Position.values()[i % 4], 18 + random.nextInt(13), quality, 100, 0, 0, 0);
            int price = 180_000 + (quality - 60) * 33_000 + (30 - player.age()) * 5_000;
            market.add(new MarketOffer(player, Math.max(100_000, price), false));
        }
    }

    private void addNews(String message) { news.add(0, message); if (news.size() > 24) news.remove(news.size() - 1); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static int poisson(Random random, double mean) {
        int goals = 0; double product = random.nextDouble(), limit = Math.exp(-mean);
        while (product > limit && goals < 8) { goals++; product *= random.nextDouble(); }
        return goals;
    }

    /** Restauração usada apenas pelo repositório de persistência, depois da leitura tipada. */
    static Career restore(String manager, int clubId, long seed, int season, int round, int balance, int morale,
            Formation formation, Mentality mentality, boolean trained, List<Player> squad,
            List<Integer> lineupIds, List<MarketOffer> market, List<Fixture> fixtures, List<String> news, League league) {
        Career career = new Career(manager, clubId, seed, league);
        int roundCount = league.roundCount(), clubCount = league.clubs().size();
        if (season < 1 || season > 30 || round < 0 || round > roundCount || balance < 0 || balance > 1_000_000_000
            || morale < 25 || morale > 100 || squad.size() < 20 || squad.size() > league.squadLimit() || market.size() != 8
            || fixtures.size() != league.fixtureCount() || news.size() > 24 || formation == null || mentality == null
            || (round == roundCount && trained))
            throw new IllegalArgumentException("Estado de carreira inválido.");
        Set<Integer> ids = new HashSet<>();
        for (Player player : squad) {
            if (!ids.add(player.id()) || player.appearances() > round || player.goals() > round * 99)
                throw new IllegalArgumentException("Plantel inválido.");
        }
        if (squad.stream().filter(Player::available).count() < 11 || lineupIds.size() != 11
            || new HashSet<>(lineupIds).size() != 11) throw new IllegalArgumentException("Onze inválido.");
        for (int id : lineupIds) {
            Player player = squad.stream().filter(p -> p.id() == id && p.available()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jogador indisponível no onze."));
            career.lineup.add(player);
        }
        Set<Integer> offerIds = new HashSet<>();
        for (MarketOffer offer : market) {
            if (!offerIds.add(offer.player().id()) || offer.price() > 10_000_000 || offer.signed() != ids.contains(offer.player().id()))
                throw new IllegalArgumentException("Mercado inválido.");
        }
        Set<String> pairs = new HashSet<>();
        for (int week = 0; week < roundCount; week++) {
            Set<Integer> clubs = new HashSet<>();
            for (Fixture fixture : fixtures) if (fixture.round() == week) {
                if (fixture.homeId() >= clubCount || fixture.awayId() >= clubCount
                    || !clubs.add(fixture.homeId()) || !clubs.add(fixture.awayId())
                    || fixture.played() != (week < round) || !pairs.add(fixture.homeId() + ":" + fixture.awayId()))
                    throw new IllegalArgumentException("Calendário inválido.");
            }
            if (clubs.size() != clubCount) throw new IllegalArgumentException("Jornada incompleta.");
        }
        career.season = season; career.round = round; career.balance = balance; career.morale = morale;
        career.formation = formation; career.mentality = mentality; career.trained = trained;
        career.squad.addAll(squad); career.market.addAll(market); career.fixtures.addAll(fixtures);
        for (String item : news) {
            if (item == null || item.length() > 400) throw new IllegalArgumentException("Notícia inválida.");
            career.news.add(item);
        }
        return career;
    }
}
