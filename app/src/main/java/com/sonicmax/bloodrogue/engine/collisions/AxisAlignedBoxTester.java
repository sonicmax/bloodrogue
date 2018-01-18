package com.sonicmax.bloodrogue.engine.collisions;

import com.sonicmax.bloodrogue.generator.Chunk;

public class AxisAlignedBoxTester {

    public static boolean test(Chunk a, Chunk b) {
        return !(b.x() > a.x() + a.width()
                || b.x() + b.width() < a.x()
                || b.y() > a.y() + a.height()
                || b.y() + b.height() < a.y());
    }
}
