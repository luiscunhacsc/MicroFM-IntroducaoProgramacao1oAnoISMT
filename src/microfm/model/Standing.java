package microfm.model;

public final class Standing {
    private final int clubId;
    private int played, wins, draws, losses, goalsFor, goalsAgainst;
    Standing(int clubId) { this.clubId = clubId; }
    public int clubId() { return clubId; }
    public int played() { return played; }
    public int wins() { return wins; }
    public int draws() { return draws; }
    public int losses() { return losses; }
    public int goalsFor() { return goalsFor; }
    public int goalsAgainst() { return goalsAgainst; }
    public int points() { return wins * 3 + draws; }
    void add(int scored, int conceded) {
        played++; goalsFor += scored; goalsAgainst += conceded;
        if (scored > conceded) wins++; else if (scored == conceded) draws++; else losses++;
    }
}
