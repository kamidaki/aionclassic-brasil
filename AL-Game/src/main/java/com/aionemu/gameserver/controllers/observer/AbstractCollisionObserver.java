
package com.aionemu.gameserver.controllers.observer;

import com.aionemu.gameserver.geoEngine.collision.CollisionResults;
import com.aionemu.gameserver.geoEngine.math.Ray;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.geoEngine.scene.Spatial;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author MrPoke, KAMIDAKI
 */
public abstract class AbstractCollisionObserver extends ActionObserver {

	protected Creature creature;
	protected Vector3f oldPos;
	protected Spatial geometry;
	protected byte intentions;
	private final AtomicBoolean isRunning = new AtomicBoolean();

	public AbstractCollisionObserver(Creature creature, Spatial geometry, byte intentions) {
		super(ObserverType.MOVE_OR_DIE);
		this.creature = creature;
		this.geometry = geometry;
		this.oldPos = new Vector3f(creature.getX(), creature.getY(), creature.getZ());
		this.intentions = intentions;
	}

	@Override
	public void moved() {
		if (!isRunning.getAndSet(true)) {
			ThreadPoolManager.getInstance().execute(() -> {
                try {
                    Vector3f pos = new Vector3f(creature.getX(), creature.getY(), creature.getZ());
                    Vector3f dir = oldPos.clone();
                    float limit = pos.distance(dir);
                    Objects.requireNonNull(dir.subtractLocal(pos)).normalizeLocal();
                    Ray r = new Ray(pos, dir);
                    r.setLimit(limit);
                    CollisionResults results = new CollisionResults(intentions, creature.getInstanceId(), true);
                    geometry.collideWith(r, results);
                    onMoved(results);
                    oldPos = pos;
                }
                finally {
                    isRunning.set(false);
                }
            });
		}
	}

	public abstract void onMoved(CollisionResults result);
}
