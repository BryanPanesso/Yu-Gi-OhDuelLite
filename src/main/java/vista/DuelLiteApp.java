package vista;

import logica.BattleListener;
import logica.Duel;
import modelo.Card;
import red.YgoApiClient;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Ventana principal del duelo.
 * - Las cartas se descargan con SwingWorker (una por casilla) para no congelar la UI.
 * - La lógica está en {@link Duel}; esta clase solo reacciona a sus eventos
 *   a través de {@link BattleListener}.
 */
public class DuelLiteApp extends JFrame implements BattleListener {
    private static final int IMG_WIDTH = 130;
    private static final int IMG_HEIGHT = 190;

    private final YgoApiClient apiClient = new YgoApiClient();

    private final JToggleButton[] playerButtons = new JToggleButton[3];
    private final ButtonGroup playerGroup = new ButtonGroup();
    private final JLabel[] aiLabels = new JLabel[3];
    private final Card[] playerCards = new Card[3];
    private final Card[] aiCards = new Card[3];

    private final JButton dealButton = new JButton("Repartir cartas");
    private final JButton startButton = new JButton("Iniciar duelo");
    private final JButton chooseButton = new JButton("Elegir carta");
    private final JRadioButton attackRadio = new JRadioButton("Ataque", true);
    private final JRadioButton defenseRadio = new JRadioButton("Defensa");

    private final JLabel scoreLabel = new JLabel("Jugador 0 - 0 Máquina");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea battleLog = new JTextArea();

    private Duel duel;

    // Control de la carga de cartas. loadId sirve para ignorar resultados
    // de una carga anterior si el usuario vuelve a repartir antes de que termine.
    private int loadId;
    private int pendingLoads;
    private int loadedCount;
    private String firstError;

    public DuelLiteApp() {
        super("Yu-Gi-Oh! Duel Lite");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(buildTopPanel(), BorderLayout.NORTH);
        content.add(buildBoardPanel(), BorderLayout.CENTER);
        content.add(buildLogPanel(), BorderLayout.EAST);
        content.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(content);

        registerListeners();
        startButton.setEnabled(false);
        chooseButton.setEnabled(false);

        pack();
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------
    // Construcción de la interfaz
    // ---------------------------------------------------------------

    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(dealButton);
        top.add(startButton);
        top.add(Box.createHorizontalStrut(30));
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 16f));
        top.add(scoreLabel);
        return top;
    }

    private JPanel buildBoardPanel() {
        JPanel aiPanel = new JPanel(new GridLayout(1, 3, 8, 0));
        aiPanel.setBorder(BorderFactory.createTitledBorder("Cartas de la máquina"));
        for (int i = 0; i < 3; i++) {
            aiLabels[i] = new JLabel("", SwingConstants.CENTER);
            aiLabels[i].setVerticalTextPosition(SwingConstants.BOTTOM);
            aiLabels[i].setHorizontalTextPosition(SwingConstants.CENTER);
            aiLabels[i].setPreferredSize(new Dimension(160, 250));
            aiPanel.add(aiLabels[i]);
        }

        JPanel playerPanel = new JPanel(new GridLayout(1, 3, 8, 0));
        for (int i = 0; i < 3; i++) {
            playerButtons[i] = new JToggleButton();
            playerButtons[i].setVerticalTextPosition(SwingConstants.BOTTOM);
            playerButtons[i].setHorizontalTextPosition(SwingConstants.CENTER);
            playerButtons[i].setPreferredSize(new Dimension(160, 250));
            playerButtons[i].setFocusPainted(false);
            playerButtons[i].setMargin(new Insets(2, 2, 2, 2));
            playerGroup.add(playerButtons[i]);
            playerPanel.add(playerButtons[i]);
        }

        ButtonGroup positionGroup = new ButtonGroup();
        positionGroup.add(attackRadio);
        positionGroup.add(defenseRadio);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controls.add(new JLabel("Posición:"));
        controls.add(attackRadio);
        controls.add(defenseRadio);
        controls.add(chooseButton);

        JPanel playerSection = new JPanel(new BorderLayout());
        playerSection.setBorder(BorderFactory.createTitledBorder("Tus cartas"));
        playerSection.add(playerPanel, BorderLayout.CENTER);
        playerSection.add(controls, BorderLayout.SOUTH);

        JPanel board = new JPanel(new BorderLayout(0, 8));
        board.add(aiPanel, BorderLayout.NORTH);
        board.add(playerSection, BorderLayout.CENTER);
        return board;
    }

    private JComponent buildLogPanel() {
        battleLog.setEditable(false);
        battleLog.setLineWrap(true);
        battleLog.setWrapStyleWord(true);
        battleLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(battleLog);
        scroll.setBorder(BorderFactory.createTitledBorder("Log de batalla"));
        scroll.setPreferredSize(new Dimension(340, 0));
        return scroll;
    }

    private void registerListeners() {
        dealButton.addActionListener(e -> dealCards());
        startButton.addActionListener(e -> startDuel());
        chooseButton.addActionListener(e -> chooseCard());
        // Al hacer clic en una carta solo se marca; se juega con "Elegir carta".
        for (JToggleButton button : playerButtons) {
            button.addActionListener(e -> updateSelectionBorders());
        }
    }

    // ---------------------------------------------------------------
    // Carga de cartas (en segundo plano)
    // ---------------------------------------------------------------

    /** Pide 3 cartas para cada jugador. Cada casilla se carga en su propio SwingWorker. */
    private void dealCards() {
        loadId++;
        duel = null;
        pendingLoads = 6;
        loadedCount = 0;
        firstError = null;
        Arrays.fill(playerCards, null);
        Arrays.fill(aiCards, null);

        dealButton.setEnabled(false);
        startButton.setEnabled(false);
        chooseButton.setEnabled(false);
        scoreLabel.setText("Jugador 0 - 0 Máquina");
        statusLabel.setText("Cargando cartas desde YGOProDeck...");
        playerGroup.clearSelection();

        for (int i = 0; i < 3; i++) {
            showLoading(playerButtons[i]);
            showLoading(aiLabels[i]);
            loadCard(true, i, loadId);
            loadCard(false, i, loadId);
        }
        updateSelectionBorders();
        refreshCards();
    }

    private void loadCard(boolean forPlayer, int index, int id) {
        SwingWorker<LoadedCard, Void> worker = new SwingWorker<LoadedCard, Void>() {
            @Override
            protected LoadedCard doInBackground() throws Exception {
                Card card = apiClient.fetchRandomMonster();
                BufferedImage image = apiClient.downloadImage(card.getImageUrl());
                ImageIcon icon = null;
                if (image != null) {
                    icon = new ImageIcon(image.getScaledInstance(IMG_WIDTH, IMG_HEIGHT, Image.SCALE_SMOOTH));
                }
                return new LoadedCard(card, icon);
            }

            @Override
            protected void done() {
                if (id != loadId) {
                    return; // carga vieja, ya se volvió a repartir
                }
                JComponent slot = forPlayer ? playerButtons[index] : aiLabels[index];
                try {
                    LoadedCard loaded = get();
                    if (forPlayer) {
                        playerCards[index] = loaded.card;
                    } else {
                        aiCards[index] = loaded.card;
                    }
                    loadedCount++;
                    showCard(slot, loaded.card, loaded.icon);
                    log((forPlayer ? "Jugador" : "Máquina") + " recibe: " + loaded.card);
                } catch (InterruptedException | ExecutionException ex) {
                    String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    if (firstError == null) {
                        firstError = message;
                    }
                    showError(slot);
                    log("ERROR: " + message);
                }
                pendingLoads--;
                if (pendingLoads == 0) {
                    onAllCardsLoaded();
                }
            }
        };
        worker.execute();
    }

    private void onAllCardsLoaded() {
        dealButton.setEnabled(true);
        refreshCards();
        if (loadedCount == 6) {
            startButton.setEnabled(true);
            statusLabel.setText("Cartas listas. Pulsa \"Iniciar duelo\".");
        } else {
            statusLabel.setText("Faltan cartas por cargar. Vuelve a repartir.");
            JOptionPane.showMessageDialog(this,
                    "No se pudieron cargar " + (6 - loadedCount) + " carta(s).\n"
                            + firstError + "\n\nPulsa \"Repartir cartas\" para intentarlo de nuevo.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------
    // Duelo
    // ---------------------------------------------------------------

    private void startDuel() {
        // Validación: no se puede jugar sin las 6 cartas cargadas.
        if (loadedCount < 6) {
            JOptionPane.showMessageDialog(this, "Espera a que ambos jugadores tengan sus 3 cartas.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        duel = new Duel(Arrays.asList(playerCards), Arrays.asList(aiCards), this);
        playerGroup.clearSelection();
        updateSelectionBorders();
        startButton.setEnabled(false);
        chooseButton.setEnabled(true);
        log("");
        log("======= NUEVO DUELO =======");
        duel.start();
    }

    private void chooseCard() {
        if (duel == null || duel.isFinished()) {
            return;
        }
        int selected = -1;
        for (int i = 0; i < 3; i++) {
            if (playerButtons[i].isSelected()) {
                selected = i;
            }
        }
        if (selected == -1) {
            statusLabel.setText("Primero selecciona una de tus cartas.");
            return;
        }
        // Se limpia antes de jugar porque si el duelo termina sale un diálogo modal.
        playerGroup.clearSelection();
        updateSelectionBorders();
        duel.playCard(selected, defenseRadio.isSelected());
    }

    // ---------------------------------------------------------------
    // Eventos del duelo (BattleListener)
    // Duel se ejecuta en el EDT porque sus cálculos son instantáneos,
    // así que aquí se puede tocar la UI directamente.
    // ---------------------------------------------------------------

    @Override
    public void onRoundStart(int round, String starter, String aiCardShown) {
        log("");
        log("--- Ronda " + round + " | turno de: " + starter + " ---");
        if (aiCardShown != null) {
            log("Máquina juega primero: " + aiCardShown);
            statusLabel.setText("Ronda " + round + ": la máquina ya jugó. Elige tu carta y posición.");
        } else {
            statusLabel.setText("Ronda " + round + ": te toca. Elige tu carta y posición.");
        }
        refreshCards();
    }

    @Override
    public void onTurn(String playerCard, String aiCard, String winner) {
        log("Jugador: " + playerCard);
        log("Máquina: " + aiCard);
        log(">> Gana la ronda: " + winner);
        refreshCards();
    }

    @Override
    public void onScoreChanged(int playerScore, int aiScore) {
        scoreLabel.setText("Jugador " + playerScore + " - " + aiScore + " Máquina");
        if (playerScore + aiScore > 0) {
            log("Marcador: Jugador " + playerScore + " - " + aiScore + " Máquina");
        }
    }

    @Override
    public void onDuelEnded(String winner) {
        log("");
        log("*** GANADOR DEL DUELO: " + winner.toUpperCase() + " ***");
        chooseButton.setEnabled(false);
        startButton.setEnabled(true);
        refreshCards();

        String message = Duel.PLAYER.equals(winner) ? "¡Ganaste el duelo!" : "La máquina ganó el duelo.";
        statusLabel.setText(message + " Puedes repetir con las mismas cartas o repartir de nuevo.");
        JOptionPane.showMessageDialog(this, message, "Fin del duelo", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------------------------------------------------------------
    // Utilidades de la vista
    // ---------------------------------------------------------------

    /** Actualiza qué cartas están disponibles según el estado del duelo. */
    private void refreshCards() {
        boolean playing = duel != null && !duel.isFinished();
        for (int i = 0; i < 3; i++) {
            boolean playerUsed = duel != null && duel.isPlayerCardUsed(i);
            playerButtons[i].setEnabled(playerCards[i] != null && !playerUsed);

            boolean aiUsed = duel != null && duel.isAiCardUsed(i);
            aiLabels[i].setEnabled(aiCards[i] == null || !aiUsed);
            boolean revealed = playing && duel.getAiPendingIndex() == i;
            aiLabels[i].setBorder(revealed ? BorderFactory.createLineBorder(Color.RED, 3) : null);
        }
    }

    private void updateSelectionBorders() {
        for (JToggleButton button : playerButtons) {
            button.setBorder(button.isSelected()
                    ? BorderFactory.createLineBorder(new Color(30, 90, 200), 3)
                    : UIManager.getBorder("ToggleButton.border"));
        }
    }

    private void showLoading(JComponent slot) {
        setSlot(slot, null, "Cargando...");
    }

    private void showError(JComponent slot) {
        setSlot(slot, null, "<html><center><font color='red'>No se pudo<br>cargar la carta</font></center></html>");
    }

    private void showCard(JComponent slot, Card card, ImageIcon icon) {
        String imageNote = icon == null ? "<i>(sin imagen)</i><br>" : "";
        String text = "<html><div style='width:125px;text-align:center'>" + imageNote
                + "<b>" + card.getName() + "</b><br>ATK " + card.getAtk() + " / DEF " + card.getDef()
                + "</div></html>";
        setSlot(slot, icon, text);
    }

    private void setSlot(JComponent slot, Icon icon, String text) {
        if (slot instanceof JLabel) {
            ((JLabel) slot).setIcon(icon);
            ((JLabel) slot).setText(text);
        } else if (slot instanceof AbstractButton) {
            ((AbstractButton) slot).setIcon(icon);
            ((AbstractButton) slot).setText(text);
        }
    }

    private void log(String line) {
        battleLog.append(line + "\n");
        battleLog.setCaretPosition(battleLog.getDocument().getLength());
    }

    /** Resultado de la carga en segundo plano: la carta y su imagen ya escalada. */
    private static class LoadedCard {
        private final Card card;
        private final ImageIcon icon;

        private LoadedCard(Card card, ImageIcon icon) {
            this.card = card;
            this.icon = icon;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DuelLiteApp app = new DuelLiteApp();
            app.setVisible(true);
            app.dealCards();
        });
    }
}
