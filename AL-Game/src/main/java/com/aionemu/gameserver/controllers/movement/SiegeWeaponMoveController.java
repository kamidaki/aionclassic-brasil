package com.aionemu.gameserver.controllers.movement;

import com.aionemu.gameserver.ai2.AISubState;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.network.aion.serverpackets.S_MOVE_NEW;
import com.aionemu.gameserver.taskmanager.tasks.MoveTaskManager;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.geo.nav.NavService;

public class SiegeWeaponMoveController extends SummonMoveController {

	private float pointX;
	private float pointY;
	private float pointZ;
	private float offset = 0.1f;
	private boolean cachedPathValid;
	private float[][] cachedPath;
	public static final float MOVE_CHECK_OFFSET = 0.1f;

	public SiegeWeaponMoveController(Summon owner) {
		super(owner);
	}
	
	@Override
	public void moveToDestination() {
		if (!owner.canPerformMove() || (owner.getAi2().getSubState() == AISubState.CAST)) {
			if (started.compareAndSet(true, false)) {
				setAndSendStopMove(owner);
			}
			updateLastMove();
			return;
		}
		else if (started.compareAndSet(false, true)) {
			movementMask = -32;
			PacketSendUtility.broadcastPacket(owner, new S_MOVE_NEW(owner));
		}

		if (MathUtil.getDistance(owner.getTarget(), pointX, pointY, pointZ) > MOVE_CHECK_OFFSET) {
			pointX = owner.getTarget().getX();
			pointY = owner.getTarget().getY();
			pointZ = owner.getTarget().getZ();
			cachedPathValid = false;
		}
		
		// SISTEMA NAV - Adicionado pathfinding
		if ((!cachedPathValid || cachedPath == null) && owner.getTarget() instanceof Creature) {
			cachedPath = NavService.getInstance().navigateToTarget(owner, (Creature) owner.getTarget());
			cachedPathValid = true;
		}
		
		if (cachedPath != null && cachedPath.length > 0 && owner.getTarget() instanceof Creature) {
			float[] p1 = cachedPath[0];
			// Verifica se o array tem pelo menos 3 elementos (x, y, z)
			if (p1.length >= 3) {
				moveToLocation(p1[0], p1[1], getTargetZ(owner, p1[0], p1[1], p1[2]), offset);
			} else {
				// Fallback para posição original se o array não tiver coordenadas completas
				moveToLocation(pointX, pointY, pointZ, offset);
			}
		} else {
			if (cachedPath != null) cachedPath = null;
			moveToLocation(pointX, pointY, pointZ, offset);
		}
		
		updateLastMove();
	}

	@Override
	public void moveToTargetObject() {
		updateLastMove();
		MoveTaskManager.getInstance().addCreature(owner);
	}

	protected void moveToLocation(float targetX, float targetY, float targetZ, float offset) {
		boolean directionChanged;
		float ownerX = owner.getX();
		float ownerY = owner.getY();
		float ownerZ = owner.getZ();

		directionChanged = targetX != targetDestX || targetY != targetDestY || targetZ != targetDestZ;

		if (directionChanged) {
			heading = (byte) (Math.toDegrees(Math.atan2(targetY - ownerY, targetX - ownerX)) / 3);
		}

		targetDestX = targetX;
		targetDestY = targetY;
		targetDestZ = targetZ;

		float currentSpeed = owner.getGameStats().getMovementSpeedFloat();
		float futureDistPassed = currentSpeed * (System.currentTimeMillis() - lastMoveUpdate) / 1000f;

		float dist = (float) MathUtil.getDistance(ownerX, ownerY, ownerZ, targetX, targetY, targetZ);

		if (dist == 0) {
			return;
		}

		if (futureDistPassed > dist) {
			futureDistPassed = dist;
		}
		
		// Processa caminho em cache quando destino é alcançado
		if (futureDistPassed == dist) {
			if (cachedPath != null && cachedPath.length > 0) {
				float[][] tempCache = new float[cachedPath.length - 1][];
				if (tempCache.length > 0) {
					System.arraycopy(cachedPath, 1, tempCache, 0, cachedPath.length - 1);
					cachedPath = tempCache;
				} else {
					cachedPath = null;
					cachedPathValid = false;
				}
			}
		}

		float distFraction = futureDistPassed / dist;
		float newX = (targetDestX - ownerX) * distFraction + ownerX;
		float newY = (targetDestY - ownerY) * distFraction + ownerY;
		float newZ = (targetDestZ - ownerZ) * distFraction + ownerZ;
		
		World.getInstance().updatePosition(owner, newX, newY, newZ, heading, false);
		
		if (directionChanged) {
			movementMask = -32;
			PacketSendUtility.broadcastPacket(owner, new S_MOVE_NEW(owner));
		}
	}
	
	/**
	 * Ajusta o valor Z do alvo para melhor aproximar a posição onde o dono deve se mover.
	 * Se {@link GeoDataConfig#GEO_NPC_MOVE} estiver habilitado e o npc não estiver voando,
	 * o GeoService será usado para determinar o valor Z verdadeiro.
	 * 
	 * @param owner -- o dono (arma de cerco)
	 * @param x -- Posição x do alvo
	 * @param y -- Posição y do alvo
	 * @param z -- Posição z do alvo (fornecida pelo NavService)
	 * @return O valor Z ajustado para este destino
	 */
	private float getTargetZ(Summon owner, float x, float y, float z) {
		float targetZ = z;
		if (GeoDataConfig.GEO_NPC_MOVE && !owner.isFlying()) {
			// Usa o método checkGeoNeedUpdate para otimizar as chamadas ao GeoService
			if (owner.getGameStats().checkGeoNeedUpdate()) {
				targetZ = GeoService.getInstance().getZ(owner.getWorldId(), x, y, z, 1.1F, owner.getInstanceId());
			}
		}
		return targetZ;
	}
}