package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.client.model.PlayerModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerCache {
    private static List<PlayerModel> players = Collections.emptyList();

    public static void setPlayers(List<PlayerModel> list) {
        players = Collections.unmodifiableList(new ArrayList<>(list));
    }

    public static List<PlayerModel> getPlayers() {
        return players;
    }

    public static void updatePlayer(PlayerModel pm) {
        List<PlayerModel> tmp = new ArrayList<>(players);
        tmp.removeIf(p -> p.id_minecraft.equals(pm.id_minecraft));
        tmp.add(pm);
        players = Collections.unmodifiableList(tmp);
    }
    public static PlayerModel getPlayer(String mcId) {
        return players.stream()
                .filter(p -> p.id_minecraft.equals(mcId))
                .findFirst()
                .orElse(null);
    }
}
