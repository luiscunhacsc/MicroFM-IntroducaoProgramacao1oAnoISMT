import microfm.match.MatchEngine;
import microfm.match.MatchEvent;
import microfm.model.Career;
import microfm.model.Formation;
import microfm.model.League;
import microfm.model.Mentality;
import microfm.model.Player;
import microfm.model.Position;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Regressões do encontro: estados, continuidade, decisões e isolamento da carreira. */
public final class TestMatch {
    private static int checks;

    public static void main(String[] args) {
        isolation();
        realRosters();
        determinism();
        lifecycleAndBounds();
        substitutions();
        tacticalEffects();
        continuity();
        System.out.println("OK: " + checks + " verificações do encontro.");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void rejected(Runnable action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static Career career(int club, long seed) { return Career.create("Mister de teste", club, seed); }

    private static String careerState(Career c) {
        StringBuilder s = new StringBuilder(c.round() + "|" + c.balance() + "|" + c.morale() + "|" + c.formation() + "|" + c.mentality());
        c.squad().forEach(p -> s.append('|').append(p.id()).append(':').append(p.condition()).append(':').append(p.goals()).append(':').append(p.appearances()));
        c.lineup().forEach(p -> s.append("|L").append(p.id()));
        c.fixtures().forEach(f -> s.append('|').append(f.homeGoals()).append(':').append(f.awayGoals()));
        return s.toString();
    }

    private static void isolation() {
        Career c = career(0, 42);
        String before = careerState(c);
        MatchEngine engine = new MatchEngine(c, 84);
        check(careerState(c).equals(before), "Construir o encontro alterou a carreira");
        engine.setMentality(Mentality.ATTACKING);
        engine.setFormation(Formation.THREE_FIVE_TWO);
        complete(engine, .25);
        check(careerState(c).equals(before), "Simular o encontro alterou a carreira antes de aplicar o resultado");
        check(engine.footballers().size() == 22, "O encontro precisa de 22 jogadores");
        System.out.println("  PASSOU · encontro isolado da carreira");
    }

    private static void determinism() {
        for (int seed = 0; seed < 8; seed++) {
            MatchEngine a = new MatchEngine(career(seed, 712), seed * 997L);
            MatchEngine b = new MatchEngine(career(seed, 712), seed * 997L);
            complete(a, .2); complete(b, .2);
            check(a.homeGoals() == b.homeGoals() && a.awayGoals() == b.awayGoals(), "Resultado não reproduzível");
            check(a.homeShots() == b.homeShots() && a.awayShots() == b.awayShots(), "Remates não reproduzíveis");
            check(a.events().equals(b.events()), "Eventos não reproduzíveis");
            check(a.scorerGoals().equals(b.scorerGoals()), "Marcadores não reproduzíveis");
        }
        System.out.println("  PASSOU · simulação reproduzível com a mesma semente");
    }

    private static void realRosters() {
        League league = League.PRIMEIRA_LIGA_2026_27;
        Career porto = Career.create("Porto", 11, 42, league);
        Career alverca = Career.create("Alverca", 1, 42, league);
        for (Career manager : List.of(porto, alverca)) {
            MatchEngine match = new MatchEngine(manager, 73);
            check(match.footballers().size() == 22, "Encontro real sem 22 jogadores");
            for (MatchEngine.Footballer footballer : match.footballers()) {
                List<Player> squad = (footballer.home() ? porto : alverca).squad();
                check(squad.stream().anyMatch(p -> p.id() == footballer.playerId() && p.name().equals(footballer.name())),
                    "Jogador do encontro fora do plantel real");
            }
        }
        System.out.println("  PASSOU · encontro com os dois plantéis reais");
    }

    private static void lifecycleAndBounds() {
        int totalGoals = 0, totalShots = 0;
        for (int seed = 0; seed < 24; seed++) {
            MatchEngine e = new MatchEngine(career(seed % 8, seed + 100), 4_000L + seed);
            double before = e.minute();
            e.update(Double.NaN); e.update(-1); e.update(0); e.update(Double.POSITIVE_INFINITY);
            check(e.minute() == before, "Intervalo temporal inválido fez avançar o encontro");
            boolean interval = false;
            double previousMinute = 0;
            for (int step = 0; !e.finished() && step < 2_000; step++) {
                e.update(.2);
                check(e.minute() >= previousMinute && e.minute() <= 90, "Relógio fora dos limites");
                previousMinute = e.minute();
                check(e.homePossession() >= 0 && e.homePossession() <= 1, "Posse fora dos limites");
                check(e.footballers().size() == 22, "Número de jogadores alterado");
                for (MatchEngine.Footballer p : e.footballers()) {
                    check(Double.isFinite(p.x()) && Double.isFinite(p.y()), "Coordenadas de jogador inválidas");
                    check(p.x() >= 0 && p.x() <= 1 && p.y() >= 0 && p.y() <= 1, "Jogador fora do campo");
                }
                MatchEngine.Ball ball = e.ball();
                check(Double.isFinite(ball.x()) && Double.isFinite(ball.y()) && Double.isFinite(ball.height()), "Coordenadas da bola inválidas");
                check(ball.x() >= -.02 && ball.x() <= 1.02 && ball.y() >= -.02 && ball.y() <= 1.02 && ball.height() >= 0,
                    "Bola fora do espaço de simulação");
                if (e.halftime()) {
                    check(!interval && e.minute() == 45 && !e.finished(), "Intervalo repetido ou fora dos 45 minutos");
                    interval = true;
                    double x = e.ball().x(), y = e.ball().y();
                    e.update(4);
                    check(e.minute() == 45 && e.ball().x() == x && e.ball().y() == y, "Encontro avançou durante o intervalo");
                    e.resumeHalf();
                    check(!e.halftime(), "Retoma não terminou o intervalo");
                }
            }
            check(e.finished() && interval && e.minute() == 90, "O encontro não chegou ao apito final");
            check(e.homeGoals() <= e.homeShots() && e.awayGoals() <= e.awayShots(), "Mais golos do que remates");
            check(e.homeGoals() >= 0 && e.awayGoals() >= 0 && e.homeGoals() + e.awayGoals() < 40, "Resultado fora dos limites do simulador");
            int userGoals = e.userHome() ? e.homeGoals() : e.awayGoals();
            check(e.scorerGoals().values().stream().mapToInt(Integer::intValue).sum() == userGoals, "Marcadores não correspondem aos golos do clube");
            check(e.events().stream().filter(event -> event.kind().equals("goal")).count() == e.homeGoals() + e.awayGoals(),
                "Relato de golos não corresponde ao marcador");
            check(e.events().stream().filter(event -> event.kind().equals("half")).count() == 1, "Evento de intervalo duplicado");
            check(e.events().stream().filter(event -> event.kind().equals("full")).count() == 1, "Evento de final duplicado");
            int events = e.events().size();
            e.update(20); e.resumeHalf();
            check(e.minute() == 90 && e.events().size() == events && e.finished(), "Encontro concluído voltou a avançar");
            totalGoals += e.homeGoals() + e.awayGoals(); totalShots += e.homeShots() + e.awayShots();
        }
        check(totalGoals > 0 && totalShots > totalGoals, "Conjunto de encontros sem oportunidades ou golos");
        System.out.println("  PASSOU · 24 encontros completos, intervalo, posições e marcadores (" + totalGoals + " golos / " + totalShots + " remates)");
    }

    private static void substitutions() {
        Career c = career(0, 4);
        MatchEngine e = new MatchEngine(c, 5);
        String before = careerState(c);
        Set<Integer> initial = new HashSet<>();
        c.lineup().forEach(p -> initial.add(p.id()));
        List<Player> bench = c.squad().stream().filter(p -> !initial.contains(p.id()) && p.position() != Position.GOALKEEPER).toList();
        Player leaving = e.playerAtSlot(1);
        rejected(() -> e.substitute(-1, bench.get(0)), "Lugar negativo aceite");
        rejected(() -> e.substitute(11, bench.get(0)), "Lugar fora do onze aceite");
        rejected(() -> e.substitute(0, bench.get(0)), "Jogador de campo entrou como guarda-redes");
        rejected(() -> e.substitute(1, null), "Substituto nulo aceite");
        rejected(() -> e.substitute(1, e.playerAtSlot(2)), "Jogador em campo entrou duas vezes");
        e.substitute(1, bench.get(0));
        check(e.playerAtSlot(1).id() == bench.get(0).id() && e.substitutions() == 1, "Primeira substituição não aplicada");
        rejected(() -> e.substitute(2, leaving), "Jogador substituído voltou a entrar");
        for (int i = 1; i < 5; i++) e.substitute(i + 1, bench.get(i));
        check(e.substitutions() == 5, "Cinco substituições não contabilizadas");
        rejected(() -> e.substitute(6, bench.get(5)), "Sexta substituição aceite");
        check(careerState(c).equals(before), "Substituições alteraram o onze da carreira durante o encontro");
        complete(e, .25);
        rejected(() -> e.substitute(6, bench.get(5)), "Substituição após apito final aceite");
        System.out.println("  PASSOU · cinco substituições, sem reentrada nem alterações prematuras à carreira");
    }

    private static void tacticalEffects() {
        MatchEngine defensive = new MatchEngine(career(0, 9), 101);
        MatchEngine attacking = new MatchEngine(career(0, 9), 101);
        defensive.setMentality(Mentality.DEFENSIVE); attacking.setMentality(Mentality.ATTACKING);
        for (int i = 0; i < 120; i++) { defensive.update(1.0 / 60); attacking.update(1.0 / 60); }
        double defenseX = centroid(defensive), attackX = centroid(attacking);
        check(attackX > defenseX + .01, "A mentalidade ofensiva não adiantou o bloco");

        MatchEngine original = new MatchEngine(career(0, 9), 101);
        MatchEngine changed = new MatchEngine(career(0, 9), 101);
        changed.setFormation(Formation.THREE_FIVE_TWO);
        for (int i = 0; i < 180; i++) { original.update(1.0 / 60); changed.update(1.0 / 60); }
        double difference = 0;
        for (int i = 0; i < 11; i++) {
            MatchEngine.Footballer a = original.footballers().get(i), b = changed.footballers().get(i);
            difference += Math.hypot(a.x() - b.x(), a.y() - b.y());
        }
        check(difference > .1, "Mudar a formação não redistribuiu os jogadores");
        rejected(() -> changed.setMentality(null), "Mentalidade nula aceite");
        rejected(() -> changed.setFormation(null), "Formação nula aceite");
        System.out.println("  PASSOU · mentalidade e formação alteram as posições no campo");
    }

    private static double centroid(MatchEngine e) {
        return e.footballers().stream().filter(p -> p.home() == e.userHome() && !p.goalkeeper()).mapToDouble(MatchEngine.Footballer::x).average().orElseThrow();
    }

    private static void continuity() {
        double largest = 0;
        for (int seed = 0; seed < 4; seed++) {
            MatchEngine e = new MatchEngine(career(0, seed), seed);
            for (int step = 0; !e.finished() && step < 20_000; step++) {
                if (e.halftime()) { e.resumeHalf(); continue; }
                MatchEngine.Ball before = e.ball();
                double celebration = e.celebrationRemaining();
                e.update(1.0 / 60);
                // A reposição no centro depois de um golo é uma transição explícita de jogo.
                if (celebration > 0 && e.celebrationRemaining() == 0) continue;
                MatchEngine.Ball after = e.ball();
                double distance = Math.hypot(before.x() - after.x(), before.y() - after.y());
                largest = Math.max(largest, distance);
                check(distance < .12, "Salto da bola de " + distance + " num quadro; semente=" + seed + ", minuto=" + e.minute());
            }
            check(e.finished(), "Encontro de continuidade não terminou");
        }
        System.out.printf(java.util.Locale.ROOT, "  PASSOU · continuidade da bola a 60 Hz (deslocamento máximo %.4f)\n", largest);
    }

    private static void complete(MatchEngine e, double step) {
        int updates = 0;
        while (!e.finished() && updates++ < 10_000) {
            e.update(step);
            if (e.halftime()) e.resumeHalf();
        }
        check(e.finished(), "Encontro não terminou no limite de atualizações");
    }
}
