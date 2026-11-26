package microfm.match;

/** Um acontecimento do encontro; a interface decide como o apresentar. */
public record MatchEvent(double minute, String text, String kind) { }
