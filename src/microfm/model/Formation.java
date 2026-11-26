package microfm.model;

public enum Formation {
    FOUR_THREE_THREE("4-3-3", 4, 3), FOUR_FOUR_TWO("4-4-2", 4, 4), THREE_FIVE_TWO("3-5-2", 3, 5);
    private final String label;
    private final int defenders, midfielders;
    Formation(String label, int defenders, int midfielders) {
        this.label = label; this.defenders = defenders; this.midfielders = midfielders;
    }
    public String label() { return label; }
    public Position positionAt(int slot) {
        if (slot < 0 || slot > 10) throw new IllegalArgumentException("Lugar inválido no onze.");
        if (slot == 0) return Position.GOALKEEPER;
        if (slot <= defenders) return Position.DEFENDER;
        if (slot <= defenders + midfielders) return Position.MIDFIELDER;
        return Position.FORWARD;
    }
    /** Coordenadas relativas ao campo; cada chamada devolve uma cópia independente. */
    public double[][] positions() {
        double[][] positions = new double[11][2];
        positions[0] = new double[] { .075, .5 };
        int slot = 1;
        int[] counts = { defenders, midfielders, 10 - defenders - midfielders };
        double[] lines = { .25, .49, .74 };
        for (int line = 0; line < counts.length; line++)
            for (int i = 0; i < counts[line]; i++)
                positions[slot++] = new double[] { lines[line], (i + 1.0) / (counts[line] + 1.0) };
        return positions;
    }
}
