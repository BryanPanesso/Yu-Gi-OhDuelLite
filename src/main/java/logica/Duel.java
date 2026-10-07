package logica;

import modelo.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reglas del duelo simplificado. No sabe nada de la interfaz: todo lo que pasa
 * se informa por medio de {@link BattleListener}.
 *
 * Cómo funciona:
 * - Cada uno tiene 3 cartas y cada carta se usa una sola vez.
 * - El turno inicial es aleatorio y luego se alterna en cada ronda.
 *   Quien tiene el turno juega primero; si es la máquina, su carta se muestra
 *   antes de que el jugador elija.
 * - Cada carta se juega en ataque o en defensa.
 * - Gana el duelo el primero que llegue a 2 rondas ganadas.
 */
public class Duel {
    public static final String PLAYER = "Jugador";
    public static final String AI = "Máquina";
    private static final int ROUNDS_TO_WIN = 2;

    private final List<Card> playerCards;
    private final List<Card> aiCards;
    private final boolean[] playerUsed;
    private final boolean[] aiUsed;
    private final BattleListener listener;
    private final Random random = new Random();

    private boolean playerTurn;
    private int round;
    private int playerScore;
    private int aiScore;
    private boolean finished;

    // Carta que la máquina ya jugó en esta ronda (cuando tiene el turno). -1 si todavía no juega.
    private int aiPendingIndex = -1;
    private boolean aiPendingDefense;

    public Duel(List<Card> playerCards, List<Card> aiCards, BattleListener listener) {
        if (playerCards.size() != 3 || aiCards.size() != 3) {
            throw new IllegalArgumentException("Cada jugador necesita exactamente 3 cartas.");
        }
        this.playerCards = new ArrayList<>(playerCards);
        this.aiCards = new ArrayList<>(aiCards);
        this.playerUsed = new boolean[3];
        this.aiUsed = new boolean[3];
        this.listener = listener;
    }

    /** Sortea quién empieza y arranca la primera ronda. */
    public void start() {
        playerTurn = random.nextBoolean();
        listener.onScoreChanged(playerScore, aiScore);
        startRound();
    }

    /**
     * El jugador juega una carta. Si la máquina todavía no jugó en esta ronda,
     * elige la suya al azar en este momento. Luego se resuelve la ronda.
     *
     * @param index   posición de la carta del jugador (0..2)
     * @param defense true si la juega en defensa, false si es en ataque
     */
    public void playCard(int index, boolean defense) {
        if (finished) {
            throw new IllegalStateException("El duelo ya terminó.");
        }
        if (index < 0 || index >= 3 || playerUsed[index]) {
            throw new IllegalArgumentException("Esa carta no está disponible.");
        }

        if (aiPendingIndex == -1) {
            chooseAiCard();
        }
        int aiIndex = aiPendingIndex;
        boolean aiDefense = aiPendingDefense;
        aiPendingIndex = -1;

        Card playerCard = playerCards.get(index);
        Card aiCard = aiCards.get(aiIndex);
        playerUsed[index] = true;
        aiUsed[aiIndex] = true;

        String winner = resolve(playerCard, defense, aiCard, aiDefense);
        if (PLAYER.equals(winner)) {
            playerScore++;
        } else {
            aiScore++;
        }

        listener.onTurn(describe(playerCard, defense), describe(aiCard, aiDefense), winner);
        listener.onScoreChanged(playerScore, aiScore);

        if (playerScore == ROUNDS_TO_WIN || aiScore == ROUNDS_TO_WIN) {
            finished = true;
            listener.onDuelEnded(playerScore > aiScore ? PLAYER : AI);
            return;
        }

        playerTurn = !playerTurn;
        startRound();
    }

    private void startRound() {
        round++;
        if (playerTurn) {
            listener.onRoundStart(round, PLAYER, null);
        } else {
            chooseAiCard();
            listener.onRoundStart(round, AI, describe(aiCards.get(aiPendingIndex), aiPendingDefense));
        }
    }

    /** La máquina elige al azar una de sus cartas libres y una posición. */
    private void chooseAiCard() {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < aiUsed.length; i++) {
            if (!aiUsed[i]) {
                free.add(i);
            }
        }
        aiPendingIndex = free.get(random.nextInt(free.size()));
        aiPendingDefense = random.nextBoolean();
    }

    /**
     * Decide quién gana la ronda. Siempre hay un único ganador:
     * - Ambos en ataque: gana el mayor ATK. Empate: gana quien tiene el turno.
     * - Uno en ataque y otro en defensa: ATK del atacante contra DEF del defensor.
     *   El atacante necesita superar la DEF; si empata, la defensa aguanta.
     * - Ambos en defensa: nadie ataca, gana la mayor DEF. Empate: gana quien tiene el turno.
     */
    private String resolve(Card playerCard, boolean playerDefense, Card aiCard, boolean aiDefense) {
        String turnOwner = playerTurn ? PLAYER : AI;

        if (!playerDefense && !aiDefense) {
            return compare(playerCard.getAtk(), aiCard.getAtk(), turnOwner);
        }
        if (!playerDefense) {
            return playerCard.getAtk() > aiCard.getDef() ? PLAYER : AI;
        }
        if (!aiDefense) {
            return aiCard.getAtk() > playerCard.getDef() ? AI : PLAYER;
        }
        return compare(playerCard.getDef(), aiCard.getDef(), turnOwner);
    }

    private String compare(int playerValue, int aiValue, String tieWinner) {
        if (playerValue > aiValue) {
            return PLAYER;
        }
        if (aiValue > playerValue) {
            return AI;
        }
        return tieWinner;
    }

    private String describe(Card card, boolean defense) {
        return defense
                ? card.getName() + " en defensa (DEF " + card.getDef() + ")"
                : card.getName() + " en ataque (ATK " + card.getAtk() + ")";
    }

    public boolean isPlayerCardUsed(int index) {
        return playerUsed[index];
    }

    public boolean isAiCardUsed(int index) {
        return aiUsed[index];
    }

    /** Índice de la carta que la máquina ya mostró en esta ronda, o -1. */
    public int getAiPendingIndex() {
        return aiPendingIndex;
    }

    public boolean isFinished() {
        return finished;
    }
}
