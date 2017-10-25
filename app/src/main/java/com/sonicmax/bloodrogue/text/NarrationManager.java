package com.sonicmax.bloodrogue.text;

import com.sonicmax.bloodrogue.renderer.TextObject;

import java.util.ArrayList;
import java.util.Iterator;

/**
 *  Handles queuing and time management of in-game narrations provided by player and enemy actions.
 *  Eg. "x attacked y", "x is looking for blood", etc
 */

public class NarrationManager {
    private final int QUEUE_MAX = 5; // Todo: this should be calculated based on screen size
    private ArrayList<Narration> queue;

    public NarrationManager() {
        queue = new ArrayList<>();
    }

    public void addToQueue(String message) {
        int size = queue.size();

        if (size >= QUEUE_MAX) {
            // Remove oldest + add new message
            queue.remove(0);
            updateRows();
        }

        queue.add(new Narration(message, queue.size()));
    }

    /**
     *  Instantiate new TextObjects for each item in queue
     */

    public ArrayList<TextObject> getTextObjects() {
        ArrayList<TextObject> narrations = new ArrayList<>();
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            Narration narration = queue.get(i);
            narrations.add(new TextObject(narration.text, narration.row));
        }
        return narrations;
    }

    /**
     *  Iterates over queue, removes items that have expired and updates rows.
     *  Should only be called periodically (eg once a second)
     */

    public void checkQueueAndRemove() {
        long currentTime = System.currentTimeMillis();
        Iterator<Narration> it = queue.iterator();
        while (it.hasNext()) {
            Narration narration = it.next();
            if (narration.hasExpired(currentTime)) {
                it.remove();
            }
        }

        updateRows();
    }

    private void updateRows() {
        int newSize = queue.size();

        // Update items so that first item in queue = bottom row
        for (int i = 0; i < newSize; i++) {
            queue.get(i).setRow(i);
        }
    }
}
