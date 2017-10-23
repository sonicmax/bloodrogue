package com.sonicmax.bloodrogue.objects;

public class LightSource extends Decoration {

    public LightSource(int x, int y, int x2, int y2, String type) {
        super(x, y, x2, y2, type);
        this.setDijkstra(0);
        this.setBlocking(false);
        this.setTraversable(true);
    }
}
