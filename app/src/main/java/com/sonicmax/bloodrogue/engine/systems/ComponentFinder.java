package com.sonicmax.bloodrogue.engine.systems;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Portal;
import com.sonicmax.bloodrogue.engine.components.Position;

/**
 *  Really bad method of retrieving components. Do not use lol
 */

public class ComponentFinder {
    public static Position getPositionComponent(Component[] components) {
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof Position) {
                return (Position) components[i];
            }
        }

        return null;
    }

    public static Portal getPortalComponent(Component[] components) {
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof Portal) {
                return (Portal) components[i];
            }
        }

        return null;
    }
}
