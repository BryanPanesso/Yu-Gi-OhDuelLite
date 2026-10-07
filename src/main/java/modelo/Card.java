package modelo;

/**
 * Modelo de una carta Monster. Solo guarda los datos que usa el duelo:
 * nombre, ATK, DEF y la URL de la imagen oficial.
 */
public class Card {
    private final int id;
    private final String name;
    private final int atk;
    private final int def;
    private final String imageUrl;

    public Card(int id, String name, int atk, int def, String imageUrl) {
        this.id = id;
        this.name = name;
        this.atk = atk;
        this.def = def;
        this.imageUrl = imageUrl;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAtk() {
        return atk;
    }

    public int getDef() {
        return def;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    @Override
    public String toString() {
        return name + " (ATK " + atk + " / DEF " + def + ")";
    }
}
