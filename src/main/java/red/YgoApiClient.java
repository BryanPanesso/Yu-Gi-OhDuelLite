package red;

import modelo.Card;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Cliente para la API de YGOProDeck.
 * Todos los métodos son bloqueantes, así que se deben llamar desde un hilo
 * de fondo (SwingWorker) y nunca desde el Event Dispatch Thread.
 */
public class YgoApiClient {
    private static final String RANDOM_CARD_URL = "https://db.ygoprodeck.com/api/v7/randomcard.php";
    // Si salen muchas Spell/Trap seguidas no queremos quedarnos pidiendo para siempre.
    private static final int MAX_ATTEMPTS = 15;

    private final HttpClient client;

    public YgoApiClient() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Pide cartas al azar hasta obtener una Monster con ATK y DEF.
     * Las Spell, Trap y Link (no tienen DEF) se descartan y se vuelve a pedir.
     */
    public Card fetchRandomMonster() throws IOException, InterruptedException {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            JSONObject json = getJson(RANDOM_CARD_URL);
            JSONObject cardJson = extractCard(json);
            if (isUsableMonster(cardJson)) {
                return parseCard(cardJson);
            }
        }
        throw new IOException("No se pudo cargar la carta: no salió ningún Monster después de "
                + MAX_ATTEMPTS + " intentos.");
    }

    /** Descarga la imagen de la carta. Devuelve null si no se pudo leer. */
    public BufferedImage downloadImage(String url) throws InterruptedException {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            return ImageIO.read(new ByteArrayInputStream(response.body()));
        } catch (IOException e) {
            // Sin imagen la carta igual se puede jugar, la UI muestra un texto en su lugar.
            return null;
        }
    }

    private JSONObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IOException("Error de red: la API no respondió a tiempo.", e);
        } catch (IOException e) {
            throw new IOException("Error de red: no se pudo conectar con YGOProDeck. Revisa tu conexión.", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("No se pudo cargar la carta (código HTTP " + response.statusCode() + ").");
        }
        try {
            return new JSONObject(response.body());
        } catch (JSONException e) {
            throw new IOException("No se pudo cargar la carta: respuesta inválida de la API.", e);
        }
    }

    /**
     * La API actual responde {"data":[{...}]}, pero versiones anteriores
     * devolvían la carta directamente. Se soportan los dos formatos.
     */
    private JSONObject extractCard(JSONObject json) throws IOException {
        JSONArray data = json.optJSONArray("data");
        if (data != null) {
            if (data.isEmpty()) {
                throw new IOException("No se pudo cargar la carta: la API no devolvió datos.");
            }
            return data.getJSONObject(0);
        }
        return json;
    }

    private boolean isUsableMonster(JSONObject cardJson) {
        String type = cardJson.optString("type", "");
        return type.contains("Monster")
                && cardJson.optInt("atk", -1) >= 0
                && cardJson.optInt("def", -1) >= 0;
    }

    private Card parseCard(JSONObject cardJson) throws IOException {
        try {
            String imageUrl = "";
            JSONArray images = cardJson.optJSONArray("card_images");
            if (images != null && !images.isEmpty()) {
                // La versión pequeña (168x246) pesa poco y basta para la ventana.
                imageUrl = images.getJSONObject(0).optString("image_url_small", "");
            }
            return new Card(
                    cardJson.getInt("id"),
                    cardJson.getString("name"),
                    cardJson.getInt("atk"),
                    cardJson.getInt("def"),
                    imageUrl);
        } catch (JSONException e) {
            throw new IOException("No se pudo cargar la carta: faltan datos en la respuesta.", e);
        }
    }
}
