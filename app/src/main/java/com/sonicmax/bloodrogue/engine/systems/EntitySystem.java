package com.sonicmax.bloodrogue.engine.systems;

import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.ai.EnemyState;
import com.sonicmax.bloodrogue.engine.components.AI;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;

/**
 *  Provides some generic helper methods for entities in game.
 */

public class EntitySystem {

    /**
     * To hide an entity, we need to tell the renderer to ignore the sprite component, move the entity to [0, 0]
     * and disable the AI component. To reactivate we just have to reverse these changes
     */

    public static void hide(ComponentManager componentManager, long entity) {
        Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
        sprite.shader = Sprite.NONE;

        Position position = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());
        position.x = 0;
        position.y = 0;

        AI ai = (AI) componentManager.getEntityComponent(entity, AI.class.getSimpleName());
        if (ai != null) {
            ai.state = EnemyState.INACTIVE;
        }
    }
}
