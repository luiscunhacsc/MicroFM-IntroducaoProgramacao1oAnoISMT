package microfm.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import microfm.model.Career;
import microfm.model.Club;
import microfm.model.Formation;
import microfm.model.Mentality;
import microfm.model.Player;
import microfm.model.Position;

/**
 * Simulação independente da pintura. Todas as distâncias são frações do campo.
 * A aplicação chama update com tempo decorrido, nunca com número de fotogramas.
 * Uma mesma semente e os mesmos passos de tempo reproduzem o encontro.
 */
public final class MatchEngine {
    private static final double MATCH_MINUTES_PER_SECOND = .6;
    private static final double MAX_ATTACK_STOPPAGE_SECONDS = 25;
    private static final double PENALTY_AREA_START = .842;

    public static final class Footballer {
        private final boolean home;
        private final int slot;
        private int playerId;
        private String name;
        private int quality;
        private int condition;
        private double enteredMinute;
        private double x, y, vx, vy, baseX, baseY;
        private Footballer(boolean home, int slot, int id, String name, int quality, int condition) {
            this.home = home; this.slot = slot; this.playerId = id;
            this.name = name; this.quality = quality; this.condition = condition;
        }
        public boolean home() { return home; }
        public int slot() { return slot; }
        public int number() { return slot + 1; }
        public int playerId() { return playerId; }
        public String name() { return name; }
        public double x() { return x; }
        public double y() { return y; }
        public double vx() { return vx; }
        public double vy() { return vy; }
        public boolean goalkeeper() { return slot == 0; }
    }

    public record Ball(double x, double y, double height, double spin) { }

    private enum Flight { PASS, SHOT, DEFLECTION }
    private enum RestartType {
        DIRECT_FREE_KICK("Livre direto", "free_kick"),
        INDIRECT_FREE_KICK("Livre indireto", "free_kick"),
        PENALTY("Penálti", "penalty"),
        THROW_IN("Lançamento lateral", "throw_in"),
        CORNER("Canto", "corner"),
        GOAL_KICK("Pontapé de baliza", "goal_kick");

        private final String label;
        private final String event;

        RestartType(String label, String event) {
            this.label = label;
            this.event = event;
        }
    }
    private record Restart(RestartType type, boolean home, double x, double y, Footballer taker) { }

    private final Career career;
    private final Club homeClub, awayClub;
    private final boolean userHome;
    private final Random random;
    private final List<Footballer> footballers = new ArrayList<>();
    private final List<Footballer> footballerView = Collections.unmodifiableList(footballers);
    private final List<MatchEvent> events = new ArrayList<>();
    private final Map<Integer,Integer> scorerGoals = new HashMap<>();
    private final Set<Integer> departed = new HashSet<>();
    private final Player[] players = new Player[11];
    private Formation formation;
    private Mentality mentality;
    private double minute, elapsed, actionWait = .6, celebration, homeTime, awayTime;
    private double stoppage, restartWait, finishingOpportunityUntil;
    private double ballX = .5, ballY = .5, ballHeight, ballSpin;
    private double flightTime, flightDuration, fromX, fromY, toX, toY, flightArc;
    private Footballer owner, receiver, shooter;
    private Flight flight;
    private Restart restart;
    private boolean shotGoal, shotSaved, shotDeflected, flightTouchHome, offsideReception;
    private boolean halftime, halfResumed, finished, lastGoalHome;
    private int homeGoals, awayGoals, homeShots, awayShots, substitutions, goalSerial;
    private double mood;

    public MatchEngine(Career career, long seed) {
        if (career == null || career.nextFixture() == null)
            throw new IllegalArgumentException("Não existe um encontro por disputar.");
        this.career = career;
        this.homeClub = career.clubs().get(career.nextFixture().homeId());
        this.awayClub = career.clubs().get(career.nextFixture().awayId());
        this.userHome = homeClub.id() == career.club().id();
        this.random = new Random(seed);
        this.formation = career.formation();
        this.mentality = career.mentality();
        if (career.lineup().size() != 11)
            throw new IllegalArgumentException("É necessário escolher onze jogadores.");
        for (int i = 0; i < 11; i++) players[i] = career.lineup().get(i);
        List<Player> opponents = career.opponentLineup();
        for (int t = 0; t < 2; t++) {
            boolean home = t == 0;
            boolean user = home == userHome;
            Club club = home ? homeClub : awayClub;
            for (int slot = 0; slot < 11; slot++) {
                Player p = user ? players[slot] : opponents.isEmpty() ? null : opponents.get(slot);
                footballers.add(new Footballer(home, slot, p == null ? -100-t*11-slot : p.id(),
                    p == null ? opponentName(slot) : p.name(),
                    p == null ? club.strength() + random.nextInt(9) - 4 : p.quality(),
                    p == null ? 100 : p.condition()));
            }
        }
        updateBases();
        resetKickoff(true);
        addEvent("Começa o encontro. A bola está a rolar!", "kickoff");
    }

    public void update(double dtSeconds) {
        if (!Double.isFinite(dtSeconds) || dtSeconds <= 0 || finished || halftime) return;
        // Subpassos limitam erros de colisão, mesmo com o jogo em velocidade alta.
        double remaining = Math.min(dtSeconds, 20);
        while (remaining > .000001 && !finished && !halftime) {
            double dt = Math.min(remaining, 1.0/60);
            step(dt); remaining -= dt;
        }
    }

    private void step(double dt) {
        elapsed += dt;
        if (celebration > 0) {
            celebration = Math.max(0, celebration-dt);
            movePlayers(dt, true);
            if (celebration == 0) {
                if (minute == periodEnd()) endPeriod();
                else {
                    resetKickoff(!lastGoalHome);
                    addEvent("A bola volta ao centro. Recomeça o jogo!", "kickoff");
                }
            }
            return;
        }
        minute = Math.min(periodEnd(), minute + dt * MATCH_MINUTES_PER_SECOND);
        if (minute == periodEnd()) {
            if (!allowsStoppagePlay()) {
                endPeriod();
                return;
            }
            if (stoppage == 0) {
                addEvent("O árbitro dá mais alguns segundos para concluir a jogada.", "stoppage");
                actionWait = Math.min(actionWait, .30);
            }
            stoppage += dt * MATCH_MINUTES_PER_SECOND * 60;
        }

        if (restart != null) {
            double restartMood = switch (restart.type()) {
                case PENALTY -> .85;
                case CORNER -> .60;
                case DIRECT_FREE_KICK -> shootableFreeKick(restart) ? .70 : .35;
                default -> .30;
            };
            movePlayers(dt, false);
            restartWait -= dt;
            if (restartWait <= 0) takeRestart();
            mood += (restartMood - mood) * dt * 2;
            return;
        }
        boolean homePossessing = owner != null ? owner.home : flightTouchHome;
        if (homePossessing) homeTime += dt; else awayTime += dt;
        movePlayers(dt, false);
        if (flight != null) {
            flightTime += dt;
            double p = Math.min(1, flightTime/flightDuration);
            ballX = fromX + (toX-fromX)*p;
            ballY = fromY + (toY-fromY)*p;
            ballHeight = Math.sin(p*Math.PI)*flightArc;
            ballSpin += dt*16;
            if (p >= 1) completeFlight();
        } else if (owner != null) {
            ballX = owner.x + direction(owner.home)*.011;
            ballY = owner.y+.009;
            ballHeight = Math.abs(Math.sin(elapsed*12))*.003;
            ballSpin += Math.hypot(owner.vx, owner.vy)*dt*180;
            actionWait -= dt;
            if (actionWait <= 0) decideAction();
        }
        double openPlayMood = flight == Flight.SHOT || flight == Flight.DEFLECTION ? 1
            : goalThreat(owner != null ? owner.home : flightTouchHome, ballX, ballY) ? .60 : .20;
        mood += (openPlayMood - mood) * dt * 2;
    }

    private double periodEnd() { return halfResumed ? 90 : 45; }

    private boolean allowsStoppagePlay() {
        if (restart != null) return restart.type() == RestartType.PENALTY || shootableFreeKick(restart);
        if (flight == Flight.SHOT || flight == Flight.DEFLECTION) return true;
        if (flight == Flight.PASS) {
            return receiver != null && receiver.home == flightTouchHome && !offsideReception
                && goalThreat(receiver.home, toX, toY);
        }
        return stoppage < Math.max(MAX_ATTACK_STOPPAGE_SECONDS, finishingOpportunityUntil)
            && owner != null && !owner.goalkeeper()
            && goalThreat(owner.home, owner.x, owner.y);
    }

    private boolean goalThreat(boolean home, double x, double y) {
        return attackProgress(home, x) > .75 && Math.abs(y - .5) < .31;
    }

    private void endPeriod() {
        ballHeight = 0;
        restart = null;
        flight = null;
        owner = receiver = shooter = null;
        if (!halfResumed) {
            halftime = true;
            addEvent("Intervalo. Hora de preparar a segunda parte.", "half");
        } else {
            finished = true;
            addEvent("Apito final! " + homeClub.shortName() + " " + homeGoals + " — " + awayGoals
                + " " + awayClub.shortName() + ".", "full");
        }
    }

    private void updateBases() {
        double[][] own = formation.positions();
        double[][] other = Formation.FOUR_THREE_THREE.positions();
        for (Footballer f : footballers) {
            double[][] layout = f.home == userHome ? own : other;
            double bx = layout[f.slot][0], by = layout[f.slot][1];
            f.baseX = direction(f.home)>0 ? bx : 1-bx;
            f.baseY = by;
        }
    }

    private void movePlayers(double dt, boolean celebrating) {
        boolean possessing = owner != null ? owner.home : restart != null ? restart.home() : flightTouchHome;
        Footballer nearest = null;
        double nearDistance = Double.MAX_VALUE;
        for (Footballer f : footballers) {
            if (f.home != possessing && f.slot != 0) {
                double d = distance(f.x, f.y, ballX, ballY);
                if (d < nearDistance) { nearest = f; nearDistance = d; }
            }
        }
        for (Footballer f : footballers) {
            double dir = direction(f.home);
            boolean keeperOnGoalLine = false;
            double shift = (ballX-.5)*.37;
            double mentalityShift = f.home == userHome
                ? (mentality == Mentality.ATTACKING ? .055 : mentality == Mentality.DEFENSIVE ? -.055 : 0)*dir : 0;
            double tx = clamp(f.baseX+shift+mentalityShift, .055, .945);
            double ty = clamp(f.baseY+(ballY-.5)*.22, .07,.93);
            tx += Math.sin(elapsed*.7+f.slot*2.4)*.009;
            ty += Math.cos(elapsed*.8+f.slot*1.7)*.012;
            if (f.slot == 0) { tx = dir>0 ? .045 : .955; ty = clamp(.5+(ballY-.5)*.28,.37,.63); }
            if (f == owner) {
                tx = clamp(f.x+dir*.09, .06,.94);
                ty = clamp(f.y+Math.sin(elapsed*.9)*.025,.12,.88);
            }
            if (f == receiver && flight != null && (flight != Flight.SHOT || shotSaved)) { tx = toX; ty = toY; }
            if (f == nearest && owner != null && nearDistance < .33) {
                tx = ballX-direction(owner.home)*.022; ty = ballY+.018;
            }
            if (restart != null) {
                if (f == restart.taker()) {
                    tx = restart.x(); ty = restart.y();
                } else if (restart.type() == RestartType.PENALTY) {
                    double attackingDirection = direction(restart.home());
                    if (f.goalkeeper() && f.home != restart.home()) {
                        keeperOnGoalLine = true;
                        tx = attackingDirection > 0 ? 1 : 0;
                        ty = .5;
                    } else {
                        // Os outros jogadores aguardam atrás da marca e do arco.
                        tx = attackingDirection > 0 ? Math.min(.80, tx) : Math.max(.20, tx);
                    }
                }
            }
            if (celebrating) {
                if (f.home == lastGoalHome && f.slot != 0) {
                    tx = direction(f.home)>0 ? .87 : .13;
                    ty = .22+f.slot*.034;
                }
            }
            double dx = tx-f.x, dy = ty-f.y;
            double d = Math.hypot(dx,dy);
            double speed = (f == owner ? .062 : .105) * (.88+f.quality/500.0);
            // Condição trazida da semana e minutos em campo contam: um suplente
            // fresco recupera velocidade sem modificar os atributos da carreira.
            speed *= .74 + .26*f.condition/100.0 - .10*Math.max(0,minute-f.enteredMinute)/90.0;
            if (f == receiver || f == nearest) speed *= 1.12;
            double factor = d<.0001 ? 0 : Math.min(speed, d*3.2)/d;
            double wantedX = dx*factor, wantedY = dy*factor;
            double smooth = 1-Math.exp(-dt*7);
            f.vx += (wantedX-f.vx)*smooth;
            f.vy += (wantedY-f.vy)*smooth;
            f.x = keeperOnGoalLine ? tx : clamp(f.x+f.vx*dt,.025,.975);
            f.y = clamp(f.y+f.vy*dt,.035,.965);
        }
    }

    private void decideAction() {
        Footballer carrier = owner;
        Footballer defender = nearestOpponent(carrier);
        boolean underPressure = defender != null && distance(carrier.x, carrier.y, defender.x, defender.y) < .09;
        if (underPressure && random.nextDouble() < (inPenaltyArea(carrier) ? .012 : .08)) {
            foul(carrier, defender);
            return;
        }

        double progress = attackProgress(carrier.home, carrier.x);
        double attack = carrier.home == userHome && mentality == Mentality.ATTACKING ? .18 : 0;
        if (progress > .68 && !carrier.goalkeeper()
                && (stoppage > 0 || random.nextDouble() < .38 + attack)) {
            shoot(carrier); return;
        }
        // Pressão próxima pode provocar uma recuperação de bola visível.
        if (underPressure && random.nextDouble() < .22) {
            receiver = defender;
            beginFlight(Flight.PASS, carrier.home, defender.x, defender.y, .23, .004);
            actionWait = 1.0;
            addEvent(surname(defender.name)+" recupera a bola.", "pass"); return;
        }
        pass(carrier, null);
    }

    private void pass(Footballer carrier, RestartType fromRestart) {
        Footballer best = null;
        double score = -Double.MAX_VALUE;
        for (Footballer f : footballers) {
            if (f == carrier || f.home != carrier.home || f.slot == 0) continue;
            double forward = (f.x-carrier.x)*direction(carrier.home);
            double d = distance(f.x,f.y,carrier.x,carrier.y);
            double candidate = forward*.9 - Math.abs(d-.25)*.6 + random.nextDouble()*.26;
            if (candidate>score) { score=candidate; best=f; }
        }
        receiver = best;
        double targetX = clamp(best.x + direction(best.home) * .025, .05, .95);
        double targetY = best.y;
        if (fromRestart == null && random.nextDouble() < .04 + .14 * Math.abs(ballY - .5)) {
            // Um passe impreciso sai pela lateral antes de qualquer nova receção.
            targetY = targetY < .5 ? -.005 : 1.005;
            receiver = null;
        } else if (random.nextDouble() < .14) {
            Footballer interception = nearestOpponent(best);
            if (interception != null) {
                receiver = interception; targetX = interception.x; targetY=interception.y;
                addEvent(surname(interception.name)+" antecipa o passe.","pass");
            }
        }
        double travel = distance(ballX, ballY, targetX, targetY);
        double arc = fromRestart == RestartType.CORNER || fromRestart == RestartType.THROW_IN || travel > .32
            ? .065 : .018;
        beginFlight(Flight.PASS, carrier.home, targetX, targetY, Math.max(.35, travel / .64), arc);
        boolean exemptFromOffside = fromRestart == RestartType.CORNER || fromRestart == RestartType.THROW_IN
            || fromRestart == RestartType.GOAL_KICK;
        offsideReception = receiver == best && !exemptFromOffside && isOffside(best, ballX);
        actionWait = 1.05+random.nextDouble()*.7;
    }

    private boolean isOffside(Footballer player, double ballPositionX) {
        double position = attackProgress(player.home, player.x);
        if (position <= .5 || position <= attackProgress(player.home, ballPositionX)) return false;
        double last = 0, secondLast = 0;
        for (Footballer opponent : footballers) {
            if (opponent.home == player.home) continue;
            double progress = attackProgress(player.home, opponent.x);
            if (progress > last) {
                secondLast = last;
                last = progress;
            } else if (progress > secondLast) {
                secondLast = progress;
            }
        }
        return position > secondLast;
    }

    private void foul(Footballer carrier, Footballer defender) {
        addEvent(surname(defender.name) + " faz falta sobre " + surname(carrier.name) + ".", "foul");
        // Os limites coincidem com a área marcada no relvado.
        if (inPenaltyArea(carrier)) {
            double markX = direction(carrier.home) > 0 ? .895 : .105;
            awardRestart(RestartType.PENALTY, carrier.home, markX, .5);
        } else {
            awardRestart(RestartType.DIRECT_FREE_KICK, carrier.home, carrier.x, carrier.y);
        }
    }

    private boolean inPenaltyArea(Footballer attacker) {
        return attackProgress(attacker.home, attacker.x) >= PENALTY_AREA_START
            && attacker.y >= .20 && attacker.y <= .80;
    }

    private void ballOut() {
        if (ballY < 0 || ballY > 1) {
            awardRestart(RestartType.THROW_IN, !flightTouchHome, clamp(ballX, 0, 1), ballY < 0 ? 0 : 1);
            return;
        }
        boolean rightGoal = ballX > 1;
        boolean attackingHome = (direction(true) > 0) == rightGoal;
        if (flightTouchHome == attackingHome) {
            awardRestart(RestartType.GOAL_KICK, !attackingHome, rightGoal ? .948 : .052, .5);
        } else {
            awardRestart(RestartType.CORNER, attackingHome, rightGoal ? 1 : 0, ballY < .5 ? 0 : 1);
        }
    }

    private void awardRestart(RestartType type, boolean home, double x, double y) {
        Footballer taker = goalkeeper(home);
        if (type != RestartType.GOAL_KICK) {
            double nearest = Double.POSITIVE_INFINITY;
            for (Footballer f : footballers) {
                if (f.home != home || f.goalkeeper()) continue;
                double d = distance(x, y, f.x, f.y);
                if (d < nearest) { nearest = d; taker = f; }
            }
        }
        restart = new Restart(type, home, x, y, taker);
        restartWait = type == RestartType.PENALTY ? .9 : .6;
        ballX = x; ballY = y; ballHeight = 0;
        owner = receiver = shooter = null;
        flight = null;
        if (type == RestartType.PENALTY) lineUpPenalty();
        addEvent(type.label + " para " + club(home).name() + ".", type.event);
    }

    private void lineUpPenalty() {
        double attackDirection = direction(restart.home());
        Footballer keeper = goalkeeper(!restart.home());
        keeper.x = attackDirection > 0 ? 1 : 0;
        keeper.y = .5;
        keeper.vx = keeper.vy = 0;

        Footballer taker = restart.taker();
        taker.x = restart.x() - attackDirection * .025;
        taker.y = restart.y();
        taker.vx = taker.vy = 0;
        for (Footballer f : footballers) {
            if (f == taker || f == keeper) continue;
            f.x = attackDirection > 0 ? Math.min(.80, f.x) : Math.max(.20, f.x);
            f.vx = 0;
        }
    }

    private boolean shootableFreeKick(Restart deadBall) {
        return deadBall.type() == RestartType.DIRECT_FREE_KICK
            && goalThreat(deadBall.home(), deadBall.x(), deadBall.y());
    }

    private void takeRestart() {
        Restart deadBall = restart;
        Footballer taker = deadBall.taker();
        RestartType type = deadBall.type();
        restart = null;
        taker.x = clamp(deadBall.x(), .025, .975);
        taker.y = clamp(deadBall.y(), .035, .965);
        taker.vx = 0; taker.vy = 0;
        if (type == RestartType.PENALTY) {
            shoot(taker, true);
            return;
        }
        addEvent(surname(taker.name) + " repõe a bola em jogo. " + type.label + ".", "restart");
        if (shootableFreeKick(deadBall)) shoot(taker);
        else pass(taker, type);
    }

    private void shoot(Footballer f) {
        shoot(f, false);
    }

    private void shoot(Footballer f, boolean penalty) {
        shooter = f;
        if (f.home) homeShots++; else awayShots++;
        double progress = attackProgress(f.home, f.x);
        receiver = goalkeeper(!f.home);
        double qualityAdvantage = (effectiveQuality(f) - effectiveQuality(receiver)) / 220.0;
        double angle = Math.abs(f.y-.5);
        double chance = penalty ? clamp(.76 + qualityAdvantage * .35, .60, .88)
            : clamp(.15 + (progress - .68) * .5 - angle * .2 + qualityAdvantage, .08, .40);
        shotGoal = random.nextDouble()<chance;
        shotSaved = !shotGoal && random.nextBoolean();
        shotDeflected = shotSaved && !penalty && random.nextDouble() < .38;
        double targetY = shotGoal ? .5+(random.nextDouble()-.5)*.13
            : shotSaved ? clamp(receiver.y+(random.nextDouble()-.5)*.065,.42,.58)
            : .5+(random.nextBoolean()?1:-1)*(.12+random.nextDouble()*.08);
        toX = shotSaved ? receiver.x : direction(f.home)>0 ? 1.005 : -.005;
        if (penalty) addEvent(surname(f.name) + " bate o penálti!", "penalty_kick");
        else addEvent(surname(f.name) + " remata à baliza!", "shot");
        beginFlight(Flight.SHOT, f.home, toX, targetY, .52 + random.nextDouble() * .22,
            .08 + random.nextDouble() * .045);
    }

    private void beginFlight(Flight type, boolean touchHome, double tx, double ty, double duration, double arc) {
        flight = type; fromX=ballX; fromY=ballY; toX=tx; toY=ty;
        flightTime=0; flightDuration=duration; flightArc=arc; owner=null;
        flightTouchHome = touchHome;
        offsideReception = false;
    }

    private void completeFlight() {
        Flight completed = flight;
        flight=null; ballHeight=0;
        if (completed == Flight.SHOT) {
            if (shotGoal) {
                lastGoalHome=shooter.home;
                if (lastGoalHome) homeGoals++; else awayGoals++;
                if (shooter.home==userHome) scorerGoals.merge(shooter.playerId,1,Integer::sum);
                goalSerial++; celebration=3.3;
                addEvent("GOLO! "+surname(shooter.name)+" marca para "+(shooter.home?homeClub.name():awayClub.name())+"!", "goal");
                owner=null;
            } else {
                if (shotSaved) {
                    addEvent("Grande defesa de "+surname(receiver.name)+"!", "save");
                    if (shotDeflected) {
                        beginFlight(Flight.DEFLECTION, receiver.home, direction(shooter.home) > 0 ? 1.005 : -.005,
                            ballY < .5 ? .33 : .67, .38, .035);
                        receiver = shooter = null;
                        return;
                    }
                    owner=receiver; owner.x=toX;owner.y=toY;
                    ballX=toX;ballY=toY;actionWait=.75;
                } else {
                    addEvent("O remate sai ao lado.", "miss");
                    ballOut();
                    return;
                }
            }
            shooter=null;
        } else if (ballX < 0 || ballX > 1 || ballY < 0 || ballY > 1) {
            ballOut();
            return;
        } else if (offsideReception) {
            addEvent(surname(receiver.name) + " recebe em fora de jogo.", "offside");
            awardRestart(RestartType.INDIRECT_FREE_KICK, !receiver.home, ballX, ballY);
            return;
        } else {
            owner=receiver;
            // A receção acontece no destino da trajetória, sem saltos da bola.
            owner.x=clamp(toX,.025,.975); owner.y=clamp(toY,.035,.965);
            ballX=owner.x; ballY=owner.y;
            if (stoppage > 0 && owner.home == flightTouchHome && goalThreat(owner.home, owner.x, owner.y)) {
                finishingOpportunityUntil = stoppage + 11;
                actionWait = Math.min(actionWait, .25);
            }
        }
        receiver=null;
    }

    private void resetKickoff(boolean home) {
        updateBases();
        for (Footballer f : footballers) {
            f.x=f.baseX; f.y=f.baseY; f.vx=0; f.vy=0;
        }
        owner=footballers.get((home?0:11)+8);
        owner.x=.5-direction(home)*.018; owner.y=.5;
        ballX=.5; ballY=.5; ballHeight=0; flight=null; receiver=null; shooter=null; restart=null;
        actionWait=.65;
    }

    public void resumeHalf() {
        if (!halftime || finished) return;
        halftime=false; halfResumed=true; stoppage=0; finishingOpportunityUntil=0; resetKickoff(false);
        addEvent("Recomeça o encontro. As equipas trocam de campo.","kickoff");
    }

    public void setMentality(Mentality mentality) {
        if (mentality==null) throw new IllegalArgumentException("Escolhe uma mentalidade.");
        this.mentality=mentality;
        addEvent("O treinador pede uma mentalidade "+mentality.label().toLowerCase()+".","tactic");
    }

    public void setFormation(Formation formation) {
        if (formation==null) throw new IllegalArgumentException("Escolhe uma formação.");
        this.formation=formation; updateBases();
        addEvent("A equipa passa a jogar em "+formation.label()+".","tactic");
    }

    public void substitute(int slot, Player reserve) {
        if (finished) throw new IllegalStateException("O encontro já terminou.");
        if (slot<0 || slot>=11 || reserve==null) throw new IllegalArgumentException("Escolhe os dois jogadores.");
        if (substitutions>=5) throw new IllegalStateException("Já utilizaste as cinco substituições.");
        if (!reserve.available()) throw new IllegalArgumentException("Esse jogador não está disponível.");
        if (career.squad().stream().noneMatch(p->p.id()==reserve.id()))
            throw new IllegalArgumentException("Esse jogador não pertence ao plantel.");
        if (departed.contains(reserve.id())) throw new IllegalArgumentException("Um jogador substituído não pode voltar a entrar.");
        for (Player p : players) if (p.id()==reserve.id()) throw new IllegalArgumentException("Esse jogador já está em campo.");
        if ((slot==0)!=(reserve.position()==Position.GOALKEEPER))
            throw new IllegalArgumentException("Troca o guarda-redes por outro guarda-redes.");
        Player leaving=players[slot]; departed.add(leaving.id()); players[slot]=reserve;
        Footballer f=footballers.get((userHome?0:11)+slot);
        f.playerId=reserve.id(); f.name=reserve.name(); f.quality=reserve.quality();
        f.condition=reserve.condition(); f.enteredMinute=minute; substitutions++;
        addEvent("Substituição: entra "+surname(reserve.name())+", sai "+surname(leaving.name())+".","substitution");
    }

    private Footballer nearestOpponent(Footballer player) {
        Footballer best=null; double dist=Double.MAX_VALUE;
        for (Footballer f:footballers) if (f.home!=player.home && f.slot!=0) {
            double d=distance(f.x,f.y,player.x,player.y);
            if (d<dist) { best=f; dist=d; }
        }
        return best;
    }
    private Footballer goalkeeper(boolean home) { return footballers.get(home?0:11); }
    private Club club(boolean home) { return home ? homeClub : awayClub; }
    private double effectiveQuality(Footballer f) {
        double confidence = f.home==userHome ? (career.morale()-70)/10.0 : 0;
        return f.quality*(.72+.28*f.condition/100.0)
            - 8*Math.max(0,minute-f.enteredMinute)/90.0 + confidence;
    }
    private double direction(boolean home) { return home != halfResumed ? 1 : -1; }
    private double attackProgress(boolean home, double x) { return direction(home) > 0 ? x : 1 - x; }
    private static double distance(double x,double y,double a,double b) { return Math.hypot(x-a,(y-b)*.65); }
    private static double clamp(double value,double low,double high) { return Math.max(low,Math.min(high,value)); }
    private static String surname(String name) { int space=name.lastIndexOf(' '); return name.substring(space+1); }
    private static String opponentName(int slot) {
        return new String[]{"D. Carvalho","R. Martins","L. Fonseca","T. Esteves","F. Duarte","M. Sousa",
            "A. Ferreira","G. Rocha","D. Pinto","R. Santos","J. Almeida"}[slot];
    }
    private void addEvent(String text,String kind) { events.add(new MatchEvent(minute,text,kind)); }
    public Club homeClub() { return homeClub; }
    public Club awayClub() { return awayClub; }
    public boolean userHome() { return userHome; }
    public boolean finished() { return finished; }
    public boolean halftime() { return halftime; }
    public double minute() { return minute; }
    public int stoppageSeconds() { return (int) Math.ceil(stoppage); }
    public boolean ballInPlay() { return !halftime && !finished && celebration == 0 && restart == null; }
    public int homeGoals() { return homeGoals; }
    public int awayGoals() { return awayGoals; }
    public int homeShots() { return homeShots; }
    public int awayShots() { return awayShots; }
    public double homePossession() { return homeTime+awayTime==0 ? .5 : homeTime/(homeTime+awayTime); }
    public List<MatchEvent> events() { return Collections.unmodifiableList(events); }
    public Map<Integer,Integer> scorerGoals() { return Map.copyOf(scorerGoals); }
    public List<Footballer> footballers() { return footballerView; }
    public Ball ball() { return new Ball(ballX,ballY,ballHeight,ballSpin); }
    public Footballer owner() { return owner; }
    public int substitutions() { return substitutions; }
    public Player playerAtSlot(int slot) { return players[slot]; }
    public int goalSerial() { return goalSerial; }
    public double celebrationRemaining() { return celebration; }
    public boolean lastGoalHome() { return lastGoalHome; }
    public double excitement() { return mood; }
    public String phaseLabel() {
        if (finished) return "APITO FINAL";
        if (halftime) return "INTERVALO";
        if (celebration>0) return "GOLO!";
        if (restart != null) return restart.type().label.toUpperCase(java.util.Locale.ROOT);
        return halfResumed ? "2.ª PARTE" : "1.ª PARTE";
    }
}
