package com.aionemu.gameserver.utils;

import com.aionemu.gameserver.model.gameobjects.Creature;

/**
 * Utility helpers for identifying Reshanta-specific maps and entities.
 */
public final class ReshantaUtil {

    private static final int RESHANTA_MAP_ID = 400010000;

    private ReshantaUtil() {
    }

    public static boolean isReshantaMap(int mapId) {
        return mapId == RESHANTA_MAP_ID;
    }

    public static boolean isReshanta(Creature creature) {
        return creature != null && isReshantaMap(creature.getWorldId());
    }
}