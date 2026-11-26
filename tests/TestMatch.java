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
        offsidePosition();
        dangerousPeriodEnd();
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
        Set<String> decisions = new HashSet<>();
        for (int seed = 0; seed < 24; seed++) {
            MatchEngine e = new MatchEngine(career(seed % 8, seed + 100), 4_000L + seed);
            double before = e.minute();
            e.update(Double.NaN); e.update(-1); e.update(0); e.update(Double.POSITIVE_INFINITY);
            check(e.minute() == before, "Intervalo temporal inválido fez avançar o encontro");
            boolean interval = false;
            double previousMinute = 0;
            int previousStoppage = 0;
            for (int step = 0; !e.finished() && step < 2_000; step++) {
                e.update(.2);
                check(e.minute() >= previousMinute && e.minute() <= 90, "Relógio fora dos limites");
                previousMinute = e.minute();
                check(e.stoppageSeconds() >= previousStoppage, "O tempo de compensação recuou durante a parte");
                check(e.stoppageSeconds() == 0 || e.minute() == 45 || e.minute() == 90,
                    "A compensação começou antes do fim do tempo regulamentar");
                previousStoppage = e.stoppageSeconds();
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
                    int stoppage = e.stoppageSeconds();
                    e.update(4);
                    check(e.minute() == 45 && e.stoppageSeconds() == stoppage && e.ball().x() == x && e.ball().y() == y,
                        "Encontro avançou durante o intervalo");
                    e.resumeHalf();
                    check(!e.halftime() && e.stoppageSeconds() == 0, "A retoma não iniciou o relógio da segunda parte");
                    previousStoppage = 0;
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
            verifyRefereeDecisions(e.events());
            int events = e.events().size();
            int stoppage = e.stoppageSeconds();
            e.events().forEach(event -> decisions.add(event.kind()));
            e.update(20); e.resumeHalf();
            check(e.minute() == 90 && e.stoppageSeconds() == stoppage && e.events().size() == events && e.finished(),
                "Encontro concluído voltou a avançar");
            totalGoals += e.homeGoals() + e.awayGoals(); totalShots += e.homeShots() + e.awayShots();
        }
        check(totalGoals > 0 && totalShots > totalGoals, "Conjunto de encontros sem oportunidades ou golos");
        Set<String> missing = new HashSet<>(Set.of("foul", "offside", "free_kick", "penalty", "penalty_kick", "throw_in",
            "corner", "goal_kick", "restart"));
        missing.removeAll(decisions);
        check(missing.isEmpty(), "Decisões ou reinícios em falta: " + missing);
        System.out.println("  PASSOU · 24 encontros completos, intervalo, posições e marcadores (" + totalGoals + " golos / " + totalShots + " remates)");
    }

    private static void verifyRefereeDecisions(List<MatchEvent> events) {
        for (int i = 0; i < events.size(); i++) {
            String kind = events.get(i).kind();
            if (!kind.equals("foul") && !kind.equals("offside")) continue;
            check(i + 1 < events.size(), "A infração ficou sem decisão");
            MatchEvent decision = events.get(i + 1);
            if (kind.equals("offside")) {
                check(decision.kind().equals("free_kick") && decision.text().startsWith("Livre indireto"),
                    "O fora de jogo não deu origem a um livre indireto");
            } else {
                check(decision.kind().equals("penalty") || decision.text().startsWith("Livre direto"),
                    "A falta não deu origem a um livre direto ou a um penálti");
            }
        }
    }

    private static void offsidePosition() {
        MatchEngine e = new MatchEngine(career(0, 12), 18);
        for (boolean home : new boolean[] {true, false}) {
            MatchEngine.Footballer attacker = e.footballers().get(home ? 8 : 19);
            MatchEngine.Footballer goalkeeper = e.footballers().get(home ? 11 : 0);
            MatchEngine.Footballer lastDefender = e.footballers().get(home ? 12 : 1);
            for (MatchEngine.Footballer player : e.footballers()) {
                if (player.home() != home) setNumber(player, "x", forDirection(.61, home));
            }
            setNumber(goalkeeper, "x", forDirection(.96, home));
            setNumber(lastDefender, "x", forDirection(.78, home));
            setNumber(attacker, "x", forDirection(.84, home));
            check(isOffside(e, attacker, forDirection(.72, home)), "Atacante adiantado sem fora de jogo");
            check(!isOffside(e, attacker, forDirection(.86, home)), "Atacante atrás da bola em fora de jogo");
            setNumber(attacker, "x", forDirection(.78, home));
            check(!isOffside(e, attacker, forDirection(.72, home)), "Atacante em linha com o penúltimo adversário em fora de jogo");
            for (MatchEngine.Footballer player : e.footballers()) {
                if (player.home() != home) setNumber(player, "x", forDirection(.34, home));
            }
            setNumber(goalkeeper, "x", forDirection(.42, home));
            setNumber(attacker, "x", forDirection(.48, home));
            check(!isOffside(e, attacker, forDirection(.40, home)), "Fora de jogo no próprio meio-campo");
        }
        System.out.println("  PASSOU · posição de fora de jogo nos dois sentidos do campo");
    }

    private static boolean isOffside(MatchEngine engine, MatchEngine.Footballer attacker, double ballX) {
        return (boolean) call(engine, "isOffside", new Class<?>[] {MatchEngine.Footballer.class, double.class}, attacker, ballX);
    }

    private static double forDirection(double position, boolean toRight) {
        return toRight ? position : 1 - position;
    }

    private static void dangerousPeriodEnd() {
        MatchEngine quiet = atPeriodDeadline(90);
        quiet.update(.05);
        check(quiet.finished() && quiet.stoppageSeconds() == 0, "Foi dada compensação com a bola no meio-campo");

        MatchEngine attack = atPeriodDeadline(90);
        MatchEngine.Footballer striker = attack.owner();
        putBallWith(attack, striker, forDirection(.91, !striker.home()), .50);
        attack.update(.05);
        check(!attack.finished() && attack.minute() == 90, "O apito interrompeu um atacante com a bola na área");
        putBallWith(attack, striker, .50, .50);
        attack.update(.05);
        check(attack.finished(), "O apito ficou adiado depois de a ameaça terminar");

        for (double deadline : new double[] {45, 90}) {
            MatchEngine e = atPeriodDeadline(deadline);
            int start = e.events().size();
            MatchEngine.Footballer shooter = e.owner();
            boolean toRight = deadline == 45 ? shooter.home() : !shooter.home();
            putBallWith(e, shooter, forDirection(.89, toRight), .50);
            call(e, "shoot", new Class<?>[] {MatchEngine.Footballer.class}, shooter);
            checkPeriodEndsAfterShot(e, deadline, start, "shot");
        }

        MatchEngine e = atPeriodDeadline(90);
        MatchEngine.Footballer attacker = e.footballers().get(8);
        MatchEngine.Footballer defender = e.footballers().get(12);
        setNumber(attacker, "x", .10);
        setNumber(attacker, "y", .50);
        int start = e.events().size();
        call(e, "foul", new Class<?>[] {MatchEngine.Footballer.class, MatchEngine.Footballer.class}, attacker, defender);
        List<MatchEvent> decision = e.events().subList(start, e.events().size());
        check(decision.stream().anyMatch(event -> event.kind().equals("penalty")) && !e.ballInPlay(),
            "Falta na área não deixou um penálti pendente");
        check(e.ball().x() > 0 && e.ball().x() < .16 && e.ball().y() == .50, "Penálti colocado fora da marca");
        checkPeriodEndsAfterShot(e, 90, start, "penalty_kick");
        System.out.println("  PASSOU · remates e penálti resolvidos antes do intervalo ou apito final");
    }

    private static void putBallWith(MatchEngine e, MatchEngine.Footballer footballer, double x, double y) {
        setNumber(footballer, "x", x);
        setNumber(footballer, "y", y);
        setNumber(e, "ballX", x);
        setNumber(e, "ballY", y);
    }

    private static MatchEngine atPeriodDeadline(double deadline) {
        MatchEngine e = new MatchEngine(career(0, 90), 82);
        if (deadline == 90) {
            for (int step = 0; !e.halftime() && step < 100; step++) e.update(5);
            check(e.halftime(), "A primeira parte não terminou");
            e.resumeHalf();
        }
        // Salta apenas o relógio para provocar a colisão entre remate e apito.
        setNumber(e, "minute", deadline - .001);
        return e;
    }

    private static void checkPeriodEndsAfterShot(MatchEngine e, double deadline, int start, String shot) {
        e.update(.05);
        check(e.minute() == deadline && !e.finished() && !e.halftime(), "O árbitro interrompeu uma situação de golo");
        for (int step = 0; !e.finished() && !e.halftime() && step < 200; step++) e.update(.05);
        check((deadline == 45 ? e.halftime() : e.finished()) && e.stoppageSeconds() > 0,
            "A parte não terminou após a compensação");
        List<String> kinds = e.events().subList(start, e.events().size()).stream().map(MatchEvent::kind).toList();
        int attempt = kinds.indexOf(shot);
        boolean resolved = attempt >= 0 && kinds.subList(attempt + 1, kinds.size()).stream()
            .anyMatch(kind -> kind.equals("goal") || kind.equals("save") || kind.equals("miss") || kind.equals("corner"));
        check(kinds.contains("stoppage") && resolved && kinds.get(kinds.size() - 1).equals(deadline == 45 ? "half" : "full"),
            "A jogada não foi resolvida antes do apito: " + kinds);
    }

    private static void setNumber(Object target, String name, double number) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setDouble(target, number);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Object call(MatchEngine engine, String name, Class<?>[] types, Object... arguments) {
        try {
            var method = MatchEngine.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(engine, arguments);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
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
                boolean wasInPlay = e.ballInPlay();
                e.update(1.0 / 60);
                // A colocação da bola para um reinício é uma transição explícita de jogo.
                if (!wasInPlay || !e.ballInPlay()) continue;
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
