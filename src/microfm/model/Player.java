package microfm.model;

/** O jogador protege os seus atributos; só as regras da carreira os alteram. */
public final class Player {
    static final int MIN_AGE = 14, MAX_AGE = 90;
    private final int id;
    private final String name;
    private final Position position;
    private int age, quality, condition, goals, appearances, injuredRounds;
    Player(int id, String name, Position position, int age, int quality, int condition,
           int goals, int appearances, int injuredRounds) {
        if (id < 0 || name == null || name.isBlank() || name.length() > 80 || position == null
            || age < MIN_AGE || age > MAX_AGE || quality < 1 || quality > 99 || condition < 0 || condition > 100
            || goals < 0 || appearances < 0 || injuredRounds < 0 || injuredRounds > 52)
            throw new IllegalArgumentException("Dados de jogador inválidos.");
        this.id = id; this.name = name; this.position = position; this.age = age;
        this.quality = quality; this.condition = condition; this.goals = goals;
        this.appearances = appearances; this.injuredRounds = injuredRounds;
    }
    public int id() { return id; }
    public String name() { return name; }
    public Position position() { return position; }
    public int age() { return age; }
    public int quality() { return quality; }
    public int condition() { return condition; }
    public int goals() { return goals; }
    public int appearances() { return appearances; }
    public int injuredRounds() { return injuredRounds; }
    public boolean available() { return injuredRounds == 0; }
    void train(Training training) {
        if (training == Training.RECOVERY) condition = Math.min(100, condition + 18);
        else if (available()) {
            condition = Math.max(25, condition - (training == Training.INTENSIVE ? 12 : 5));
            quality = Math.min(99, quality + (training == Training.INTENSIVE ? 2 : 1));
        }
    }
    void finishWeek(boolean played, int scored, int fatigue, int injury) {
        if (played) { appearances++; goals += scored; condition = Math.max(20, condition - fatigue); }
        condition = Math.min(100, condition + (played ? 7 : 14));
        injuredRounds = Math.max(0, injuredRounds - 1);
        if (injury > 0) injuredRounds = injury;
    }
    void newSeason() { age++; condition = 100; goals = 0; appearances = 0; injuredRounds = 0; }
    @Override public String toString() { return name + " · " + position.label(); }
}
