package microfm.model;

import java.util.List;

public enum League {
    FICTIONAL("Liga MicroFM", Club.all(), 32),
    PRIMEIRA_LIGA_2026_27("Primeira Liga 2026/27", PrimeiraLiga.CLUBS, 60);

    private final String label;
    private final List<Club> clubs;
    private final int squadLimit;

    League(String label, List<Club> clubs, int squadLimit) {
        this.label = label;
        this.clubs = clubs;
        this.squadLimit = squadLimit;
    }

    public String label() { return label; }
    public List<Club> clubs() { return clubs; }
    public int roundCount() { return 2 * (clubs.size() - 1); }
    public int fixtureCount() { return roundCount() * clubs.size() / 2; }
    public int squadLimit() { return squadLimit; }
}
