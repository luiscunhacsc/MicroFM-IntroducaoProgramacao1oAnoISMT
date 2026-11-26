package microfm.model;

public final class Fixture {
    private final int homeId, awayId, round;
    private int homeGoals = -1, awayGoals = -1;
    Fixture(int homeId, int awayId, int round) {
        if (homeId < 0 || homeId > 17 || awayId < 0 || awayId > 17 || homeId == awayId || round < 0 || round > 33)
            throw new IllegalArgumentException("Encontro inválido.");
        this.homeId = homeId; this.awayId = awayId; this.round = round;
    }
    public int homeId() { return homeId; }
    public int awayId() { return awayId; }
    public int round() { return round; }
    public int homeGoals() { return homeGoals; }
    public int awayGoals() { return awayGoals; }
    public boolean played() { return homeGoals >= 0; }
    void result(int homeGoals, int awayGoals) {
        if (homeGoals < 0 || homeGoals > 99 || awayGoals < 0 || awayGoals > 99 || played())
            throw new IllegalArgumentException("Resultado inválido ou já registado.");
        this.homeGoals = homeGoals; this.awayGoals = awayGoals;
    }
}
