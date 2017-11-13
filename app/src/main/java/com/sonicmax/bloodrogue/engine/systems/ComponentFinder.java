package com.sonicmax.bloodrogue.engine.systems;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Stationary;

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

    public static Sprite getSpriteComponent(Component[] components) {
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof Sprite) {
                return (Sprite) components[i];
            }
        }

        return null;
    }

    public static Stationary getStaticComponent(Component[] components) {
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof Stationary) {
                return (Stationary) components[i];
            }
        }

        return null;
    }

    public static Physics getPhysicsComponent(Component[] components) {
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof Physics) {
                return (Physics) components[i];
            }
        }

        return null;
    }
}
