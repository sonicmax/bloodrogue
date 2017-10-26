package com.sonicmax.bloodrogue.engine.objects;

public class PlayerFactory {

    public static GameObject getPlayer(int x, int y) {
        Actor player = new Actor(x, y, 1);
        player.setTile("sprites/dude.png");
        player.setName("Player");
        player.setInteractive(true);
        player.setPlayerControl(true);

        // Todo: more playtesting so we have a formula for stat points
        player.setStrength(6);
        player.setEndurance(3);
        player.setAgility(3);
        player.setMaxHp(player.BASE_HP + (player.getEndurance()));

        return player;
    }
}
