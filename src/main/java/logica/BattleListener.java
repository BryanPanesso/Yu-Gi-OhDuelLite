package logica;

/**
 * Eventos que emite {@link Duel}. La interfaz gráfica implementa este listener,
 * así la lógica del duelo no depende de Swing.
 */
public interface BattleListener {

    /**
     * Inicio de una ronda.
     *
     * @param round       número de ronda (1..3)
     * @param starter     quién tiene el turno ("Jugador" o "Máquina")
     * @param aiCardShown si la máquina tiene el turno, la carta que ya jugó; si no, null
     */
    void onRoundStart(int round, String starter, String aiCardShown);

    /** Resultado de una ronda: qué jugó cada uno y quién ganó. */
    void onTurn(String playerCard, String aiCard, String winner);

    void onScoreChanged(int playerScore, int aiScore);

    void onDuelEnded(String winner);
}
