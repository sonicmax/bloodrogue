package com.sonicmax.bloodrogue.generator.factories;

import com.sonicmax.bloodrogue.engine.Entity;
import com.sonicmax.bloodrogue.engine.components.AI;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Blood;
import com.sonicmax.bloodrogue.engine.components.Container;
import com.sonicmax.bloodrogue.engine.components.Damage;
import com.sonicmax.bloodrogue.engine.components.Dexterity;
import com.sonicmax.bloodrogue.engine.components.Dynamic;
import com.sonicmax.bloodrogue.engine.components.Energy;
import com.sonicmax.bloodrogue.engine.components.Experience;
import com.sonicmax.bloodrogue.engine.components.Input;
import com.sonicmax.bloodrogue.engine.components.Knowledge;
import com.sonicmax.bloodrogue.engine.components.Name;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Vitality;
import com.sonicmax.bloodrogue.engine.systems.StatBuilder;

public class PlayerFactory {
    public static final String PLAYER_NAME = "Player";

    public static Component[] getPlayer(int x, int y) {
        Entity entity = new Entity();

        Component[] array = new Component[15];

        Position positionComponent = new Position(entity.id);
        positionComponent.x = x;
        positionComponent.y = y;

        Sprite spriteComponent = new Sprite(entity.id);
        spriteComponent.path = "sprites/dude.png";
        spriteComponent.shader = Sprite.DYNAMIC;

        Name nameComponent = new Name(PLAYER_NAME, "it me", entity.id);

        Dynamic dynamicComponent = new Dynamic(entity.id);

        Input inputComponent = new Input(entity.id);

        AI aiComponent = new AI(entity.id);
        aiComponent.affinity = AI.PLAYER;
        aiComponent.canInteract = true;
        aiComponent.computerControlled = false;

        Experience experienceComponent = new Experience(entity.id);
        StatBuilder.setDefaultLevel(experienceComponent);

        Damage damageComponent = new Damage(entity.id);
        damageComponent.strength = 6;

        Vitality vitalityComponent = new Vitality(entity.id);
        vitalityComponent.endurance = 5;
        vitalityComponent.maxHp = vitalityComponent.BASE_HP + vitalityComponent.endurance;
        vitalityComponent.hp = vitalityComponent.maxHp;

        Physics physicsComponent = new Physics(entity.id);
        physicsComponent.isTraversable = false;
        physicsComponent.activateOnCollide = true;

        Energy energyComponent = new Energy(entity.id);
        energyComponent.agility = 5;

        Blood bloodComponent = new Blood(Blood.RED, entity.id);

        Container containerComponent = new Container(Container.DEFAULT, entity.id);
        containerComponent.capacity = 25;

        Dexterity dexterityComponent = new Dexterity(entity.id);
        dexterityComponent.skill = 1;

        Knowledge knowledgeComponent = new Knowledge(entity.id);

        array[0] = positionComponent;
        array[1] = spriteComponent;
        array[2] = nameComponent;
        array[3] = dynamicComponent;
        array[4] = inputComponent;
        array[5] = experienceComponent;
        array[6] = damageComponent;
        array[7] = vitalityComponent;
        array[8] = physicsComponent;
        array[9] = energyComponent;
        array[10] = aiComponent;
        array[11] = bloodComponent;
        array[12] = containerComponent;
        array[13] = dexterityComponent;
        array[14] = knowledgeComponent;

        return array;
    }
}
