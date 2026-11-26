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

    private enum Flight { PASS, SHOT, SAVE, CLEARANCE }
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
    private double ballX = .5, ballY = .5, ballHeight, ballSpin;
    private double flightTime, flightDuration, fromX, fromY, toX, toY, flightArc;
    private Footballer owner, receiver, shooter;
    private Flight flight;
    private boolean shotGoal, shotSaved, halftime, halfResumed, finished, lastGoalHome;
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
            if (celebration == 0) resetKickoff(!lastGoalHome);
            return;
        }
        minute = Math.min(90, minute + dt*.6);
        if (!halfResumed && minute >= 45) {
            minute = 45; halftime = true;
            addEvent("Intervalo. Hora de preparar a segunda parte.", "half"); return;
        }
        if (minute >= 90) {
            finished = true; ballHeight = 0;
            addEvent("Apito final! " + homeClub.shortName() + " " + homeGoals + " — " + awayGoals
                + " " + awayClub.shortName() + ".", "full"); return;
        }
        boolean homePossessing = owner != null ? owner.home : shooter != null ? shooter.home
            : receiver == null || receiver.home;
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
        mood += ((flight == Flight.SHOT ? 1 : .2)-mood)*dt*2;
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
        boolean possessing = owner != null ? owner.home : receiver != null ? receiver.home : true;
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
            f.x = clamp(f.x+f.vx*dt,.025,.975);
            f.y = clamp(f.y+f.vy*dt,.035,.965);
        }
    }

    private void decideAction() {
        Footballer carrier = owner;
        double progress = direction(carrier.home)>0 ? carrier.x : 1-carrier.x;
        double attack = carrier.home == userHome && mentality == Mentality.ATTACKING ? .18 : 0;
        if (progress > .68 && carrier.slot != 0 && random.nextDouble() < .38+attack) {
            shoot(carrier); return;
        }
        // Pressão próxima pode provocar uma recuperação de bola visível.
        Footballer defender = nearestOpponent(carrier);
        if (defender != null && distance(carrier.x,carrier.y,defender.x,defender.y)<.09
                && random.nextDouble()<.22) {
            receiver = defender;
            beginFlight(Flight.PASS,defender.x,defender.y,.23,.004);
            actionWait = 1.0;
            addEvent(surname(defender.name)+" recupera a bola.", "pass"); return;
        }
        Footballer best = null;
        double score = -Double.MAX_VALUE;
        for (Footballer f : footballers) {
            if (f == carrier || f.home != carrier.home || f.slot == 0) continue;
            double forward = (f.x-carrier.x)*direction(carrier.home);
            double d = distance(f.x,f.y,carrier.x,carrier.y);
            double candidate = forward*.9 - Math.abs(d-.25)*.6 + random.nextDouble()*.26;
            if (candidate>score) { score=candidate; best=f; }
        }
        if (best == null) { actionWait = .8; return; }
        receiver = best;
        double targetX = clamp(best.x+direction(best.home)*.025,.05,.95);
        double targetY = best.y;
        if (random.nextDouble()<.14) {
            Footballer interception = nearestOpponent(best);
            if (interception != null) {
                receiver = interception; targetX = interception.x; targetY=interception.y;
                addEvent(surname(interception.name)+" antecipa o passe.","pass");
            }
        }
        beginFlight(Flight.PASS,targetX,targetY, Math.max(.35,distance(ballX,ballY,targetX,targetY)/.64),
            distance(ballX,ballY,targetX,targetY)>.32 ? .065 : .018);
        actionWait = 1.05+random.nextDouble()*.7;
    }

    private void shoot(Footballer f) {
        shooter = f;
        if (f.home) homeShots++; else awayShots++;
        double progress = direction(f.home)>0 ? f.x : 1-f.x;
        double qualityAdvantage = (effectiveQuality(f)-effectiveQuality(goalkeeper(!f.home)))/220.0;
        double angle = Math.abs(f.y-.5);
        double chance = clamp(.15+(progress-.68)*.5-angle*.2+qualityAdvantage,.08,.40);
        shotGoal = random.nextDouble()<chance;
        receiver = goalkeeper(!f.home);
        shotSaved = !shotGoal && random.nextBoolean();
        double targetY = shotGoal ? .5+(random.nextDouble()-.5)*.13
            : shotSaved ? clamp(receiver.y+(random.nextDouble()-.5)*.065,.42,.58)
            : .5+(random.nextBoolean()?1:-1)*(.12+random.nextDouble()*.08);
        toX = shotSaved ? receiver.x : direction(f.home)>0 ? 1.005 : -.005;
        addEvent(surname(f.name)+" remata à baliza!", "shot");
        beginFlight(Flight.SHOT,toX,targetY, .52+random.nextDouble()*.22, .08+random.nextDouble()*.045);
    }

    private void beginFlight(Flight type, double tx, double ty, double duration, double arc) {
        flight = type; fromX=ballX; fromY=ballY; toX=tx; toY=ty;
        flightTime=0; flightDuration=duration; flightArc=arc; owner=null;
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
                    owner=receiver; owner.x=toX;owner.y=toY;
                    ballX=toX;ballY=toY;actionWait=.75;
                } else {
                    addEvent("O remate sai ao lado. Reposição do guarda-redes.", "shot");
                    // Mesmo a reposição tem trajetória: não há um salto de pixels
                    // da linha de fundo para as mãos do guarda-redes.
                    beginFlight(Flight.CLEARANCE,receiver.x,receiver.y,.9,.045);
                    actionWait=.75;shooter=null;return;
                }
            }
            shooter=null;
        } else {
            owner=receiver;
            // A receção acontece no destino da trajetória, sem saltos da bola.
            owner.x=clamp(toX,.025,.975); owner.y=clamp(toY,.035,.965);
            ballX=owner.x; ballY=owner.y;
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
        ballX=.5; ballY=.5; ballHeight=0; flight=null; receiver=null; shooter=null;
        actionWait=.65;
    }

    public void resumeHalf() {
        if (!halftime || finished) return;
        halftime=false; halfResumed=true; resetKickoff(false);
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
    private double effectiveQuality(Footballer f) {
        double confidence = f.home==userHome ? (career.morale()-70)/10.0 : 0;
        return f.quality*(.72+.28*f.condition/100.0)
            - 8*Math.max(0,minute-f.enteredMinute)/90.0 + confidence;
    }
    private double direction(boolean home) { return home != halfResumed ? 1 : -1; }
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
        return halfResumed ? "2.ª PARTE" : "1.ª PARTE";
    }
}
