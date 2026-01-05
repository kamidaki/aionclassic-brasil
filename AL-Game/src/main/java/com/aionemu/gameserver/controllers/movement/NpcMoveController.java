package com.aionemu.gameserver.controllers.movement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import com.aionemu.gameserver.ai2.AI2Logger;
import com.aionemu.gameserver.ai2.AIState;
import com.aionemu.gameserver.ai2.AISubState;
import com.aionemu.gameserver.ai2.NpcAI2;
import com.aionemu.gameserver.ai2.handler.TargetEventHandler;
import com.aionemu.gameserver.ai2.manager.WalkManager;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.model.actions.NpcActions;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.geometry.Point3D;
import com.aionemu.gameserver.model.stats.calc.Stat2;
import com.aionemu.gameserver.model.templates.walker.RouteStep;
import com.aionemu.gameserver.model.templates.zone.Point2D;
import com.aionemu.gameserver.network.aion.serverpackets.S_MOVE_NEW;
import com.aionemu.gameserver.spawnengine.WalkerGroup;
import com.aionemu.gameserver.taskmanager.tasks.MoveTaskManager;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.collections.LastUsedCache;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.geo.nav.NavService;
import com.aionemu.commons.utils.Rnd;

/**
 * @author ATracer
 * @author KAMIDAKI - Versão corrigida com melhorias no sistema NAV e calculo de altura (Z)
 * @author KLASIX - Adptação/Melhorias para AionClassic 2.4
 */
public class NpcMoveController extends CreatureMoveController<Npc> {

    private static final Logger log = LoggerFactory.getLogger(NpcMoveController.class);
    public static final float MOVE_CHECK_OFFSET = 0.1f;
    private static final float MOVE_OFFSET = 0.05f;
    private static final int MAX_GEO_POINT_DISTANCE = 5;
    private static final int MAX_NAV_FAILURES = 3;
    private static final long NAV_FAILURE_RESET_MS = 5000L;
    private static final float NAV_FAILURE_DISTANCE_EPSILON = 1.0f;
    
    private int returnAttempts;
    private Destination destination = Destination.TARGET_OBJECT;
    private float pointX;
    private float pointY;
    private float pointZ;
    private LastUsedCache<Byte, Point3D> lastSteps = null;
    private byte stepSequenceNr = 0;
    private float offset = 0.1f;
    private boolean cachedPathValid;
    private float[][] cachedPath;
    // walk related
    List<RouteStep> currentRoute;
    int currentPoint;
    int walkPause;
    private float cachedTargetZ;
    private boolean nextPointFromGeo;
    
    // NAV failure tracking
    private int navFailureCount;
    private long lastNavFailureTime;
    
    // Flee tracking
    private float lastTargetX, lastTargetY;
    private float lastOwnerX, lastOwnerY;
    private long lastMovementTime = System.currentTimeMillis();

    public NpcMoveController(Npc owner) {
        super(owner);
    }

    private static enum Destination {
        TARGET_OBJECT, 
        POINT,
        HOME,
        FLEE_FROM_FLYING_TARGET
    }

    /**
     * Move to current target
     */
    public void moveToTargetObject() {
        if (started.compareAndSet(false, true)) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "MC: moveToTarget started");
            }
            destination = Destination.TARGET_OBJECT;
            updateLastMove();
            MoveTaskManager.getInstance().addCreature(owner);
            resetNavFailureTracker();
        }
    }

    public void moveToPoint(float x, float y, float z) {
        if (started.compareAndSet(false, true)) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "MC: moveToPoint started");
            }
            destination = Destination.POINT;
            pointX = x;
            pointY = y;
            pointZ = z;
            updateLastMove();
            MoveTaskManager.getInstance().addCreature(owner);
        }
    }

    public void moveToHome() {
        if (started.compareAndSet(false, true)) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "MC: moveToHome started");
            }
            cachedPathValid = false;
            float x = owner.getSpawn().getX(), y = owner.getSpawn().getY(), z = owner.getSpawn().getZ();
            destination = Destination.HOME;
            pointX = x;
            pointY = y;
            pointZ = z;
            updateLastMove();
            MoveTaskManager.getInstance().addCreature(owner);
        }
    }

    public void moveToNextPoint() {
        if (started.compareAndSet(false, true)) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "MC: moveToNextPoint started");
            }
            destination = Destination.POINT;
            updateLastMove();
            MoveTaskManager.getInstance().addCreature(owner);
        }
    }

    /**
     * Novo método para iniciar fuga de alvo voador
     */
    public void fleeFromFlyingTarget() {
        if (started.compareAndSet(false, true)) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "MC: fleeFromFlyingTarget started");
            }
            destination = Destination.FLEE_FROM_FLYING_TARGET;
            calculateFleePoint();
            updateLastMove();
            MoveTaskManager.getInstance().addCreature(owner);
        }
    }

    /**
     * Calcula um ponto de fuga inteligente baseado na posição do alvo voador
     */
    private void calculateFleePoint() {
        VisibleObject target = owner.getTarget();
        if (target == null) return;

        float ownerX = owner.getX();
        float ownerY = owner.getY();
        float ownerZ = owner.getZ();
        float targetX = target.getX();
        float targetY = target.getY();

        // Calcula direção oposta ao alvo
        double baseFleeAngle = Math.atan2(ownerY - targetY, ownerX - targetX);

        // Array de ângulos para testar
        double[] testAngles = {
            baseFleeAngle,
            baseFleeAngle + Math.toRadians(30),
            baseFleeAngle - Math.toRadians(30),
            baseFleeAngle + Math.toRadians(60),
            baseFleeAngle - Math.toRadians(60)
        };

        float fleeDistance = 15.0f;
        float[] testDistances = {fleeDistance, fleeDistance * 0.75f, fleeDistance * 0.5f};

        // Encontra o melhor ponto de fuga
        for (double angle : testAngles) {
            for (float distance : testDistances) {
                float candidateX = ownerX + (float)(Math.cos(angle) * distance);
                float candidateY = ownerY + (float)(Math.sin(angle) * distance);
                float candidateZ = getValidTargetZ(candidateX, candidateY, ownerZ, null);

                if (isValidFleePoint(ownerX, ownerY, ownerZ, candidateX, candidateY, candidateZ)) {
                    pointX = candidateX;
                    pointY = candidateY;
                    pointZ = candidateZ;
                    return;
                }
            }
        }

        // Fallback: fica na posição atual
        pointX = ownerX;
        pointY = ownerY;
        pointZ = ownerZ;
    }

    /**
     * Verifica se um ponto de fuga é válido
     */
    private boolean isValidFleePoint(float startX, float startY, float startZ,
        float endX, float endY, float endZ) {

        if (Float.isNaN(endZ)) {
            return false;
        }

        float heightDiff = Math.abs(endZ - startZ);
        if (heightDiff > 10.0f) {
            return false;
        }

        if (GeoDataConfig.GEO_ENABLE && !GeoService.getInstance().canSee(
            owner.getWorldId(), startX, startY, startZ + 1.0f,
            endX, endY, endZ + 1.0f, 0, owner.getInstanceId())) {
            return false;
        }

        return true;
    }

    /**
     * @return if destination reached
     */
    @Override
    public void moveToDestination() {
        if (owner.getAi2().isLogging()) {
            AI2Logger.moveinfo(owner, "moveToDestination destination: " + destination);
        }
        if (NpcActions.isAlreadyDead(owner)) {
            abortMove();
            return;
        }
        if (!owner.canPerformMove() || (owner.getAi2().getSubState() == AISubState.CAST)) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "moveToDestination can't perform move");
            }
            if (started.compareAndSet(true, false)) {
                setAndSendStopMove(owner);
            }
            updateLastMove();
            return;
        } else if (started.compareAndSet(false, true)) {
            movementMask = MovementMask.NPC_STARTMOVE;
            PacketSendUtility.broadcastPacket(owner, new S_MOVE_NEW(owner));
        }

        if (!started.get()) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "moveToDestination not started");
            }
        }

        switch (destination) {
            case TARGET_OBJECT:
                returnAttempts = 0;
                Npc npc = owner;
                VisibleObject target = owner.getTarget();
                if (target == null) {
                    return;
                }
                if (!(target instanceof Creature)) {
                    return;
                }
                
                Creature creatureTarget = (Creature) target;
                
                // VERIFICAÇÃO: NPC terrestre vs alvo voador
                if (creatureTarget.isFlying() && !npc.isFlying()) {
                    destination = Destination.FLEE_FROM_FLYING_TARGET;
                    calculateFleePoint();
                    break;
                }

                if (MathUtil.getDistance(target, pointX, pointY, pointZ) > MOVE_CHECK_OFFSET) {
                    offset = npc.getController().getAttackDistanceToTarget();
                    pointX = target.getX();
                    pointY = target.getY();
                    pointZ = getTargetZ(npc, creatureTarget);
                    cachedPathValid = false;
                }
                
                // SISTEMA NAV MELHORADO
                if (!cachedPathValid || cachedPath == null) {
                    cachedPath = NavService.getInstance().navigateToTarget(owner, (Creature) target);
                    
                    // Verifica se é falha de navegação
                    if (isNavFailurePath(cachedPath)) {
                        cachedPath = null;
                        cachedPathValid = true;
                        if (registerNavFailureAndCheckGiveup(creatureTarget)) {
                            return;
                        }
                    } else {
                        resetNavFailureTracker();
                        
                        // Processa caminho se válido
                        if (cachedPath != null && cachedPath.length > 0) {
                            // Valida Z de todos os pontos
                            for (int i = 0; i < cachedPath.length; i++) {
                                float[] point = cachedPath[i];
                                if (point.length >= 3) {
                                    point[2] = getValidTargetZ(point[0], point[1], point[2], creatureTarget);
                                }
                            }
                            
                            // Adiciona aleatoriedade no ponto final
                            if (cachedPath.length > 1) {
                                float[] lastPoint = cachedPath[cachedPath.length - 1];
                                float radius = owner.getObjectTemplate().getBoundRadius().getSide();

                                if (Rnd.nextBoolean()) {
                                    lastPoint[0] += (float) Rnd.nextDouble() * radius;
                                } else {
                                    lastPoint[0] -= (float) Rnd.nextDouble() * radius;
                                }
                                if (Rnd.nextBoolean()) {
                                    lastPoint[1] += (float) Rnd.nextDouble() * radius;
                                } else {
                                    lastPoint[1] -= (float) Rnd.nextDouble() * radius;
                                }

                                // Recalcula Z após mudança X,Y
                                lastPoint[2] = getValidTargetZ(lastPoint[0], lastPoint[1],
                                    lastPoint[2], creatureTarget);
                            }
                        }
                        cachedPathValid = true;
                    }
                }
                
                if (cachedPath != null && cachedPath.length > 0) {
                    float[] p1 = cachedPath[0];
                    moveToLocation(p1[0], p1[1], getValidTargetZ(p1[0], p1[1], p1[2], creatureTarget), offset);
                } else {
                    if (cachedPath != null) cachedPath = null;
                    moveToLocation(pointX, pointY, pointZ, offset);
                }
                break;
                
            case FLEE_FROM_FLYING_TARGET:
                Creature fleeTarget = owner.getTarget() instanceof Creature ? (Creature) owner.getTarget() : null;
                if (fleeTarget == null) {
                    return;
                }

                // Verifica se alvo desceu do voo
                if (!fleeTarget.isFlying()) {
                    destination = Destination.TARGET_OBJECT;
                    moveToTargetObject();
                    break;
                }

                moveToLocation(pointX, pointY, pointZ, 0.0f);
                if (MathUtil.getDistance(owner.getX(), owner.getY(), owner.getZ(), pointX, pointY, pointZ) < MOVE_OFFSET) {
                    calculateFleePoint();
                }
                break;
                
            case POINT:
                offset = 0.1f;
                moveToLocation(pointX, pointY, pointZ, offset);
                break;
                
            case HOME:
                if ((!cachedPathValid || cachedPath == null) && (returnAttempts < 3)) {
                    cachedPath = NavService.getInstance().navigateToLocation(owner, pointX, pointY, pointZ);
                    returnAttempts++;
                    cachedPathValid = true;
                }
                if ((cachedPath != null) && (cachedPath.length > 0) && (returnAttempts < 3)) {
                    float[] p1 = cachedPath[0];
                    moveToLocation(p1[0], p1[1], getValidTargetZ(p1[0], p1[1], p1[2], null), offset);
                } else {
                    moveToLocation(pointX, pointY, pointZ, offset);
                }
                break;
        }
        updateLastMove();
    }

    /**
     * Método unificado para calcular Z válido
     */
    private float getValidTargetZ(float targetX, float targetY, float originalZ, Creature target) {
        if (!GeoDataConfig.GEO_NPC_MOVE || !GeoDataConfig.GEO_ENABLE) {
            return originalZ;
        }

        int worldId = owner.getWorldId();
        int instanceId = owner.getInstanceId();
        float searchMargin = Math.max(2.0f, MOVE_CHECK_OFFSET);

        // Tenta encontrar Z na superfície
        float geoZ = GeoService.getInstance().getZ(worldId, targetX, targetY,
            originalZ + searchMargin, originalZ - searchMargin, instanceId);

        if (!Float.isNaN(geoZ)) {
            return geoZ;
        }

        float ownerZ = owner.getZ();
        float distanceXY = (float) MathUtil.getDistance(owner.getX(), owner.getY(), targetX, targetY);
        float fallbackRange = Math.max(searchMargin, calculateMaxZDiff(distanceXY, target));

        // Fallback: busca em torno da altura atual do NPC
        geoZ = GeoService.getInstance().getZ(worldId, targetX, targetY,
            ownerZ + fallbackRange, ownerZ - fallbackRange, instanceId);

        if (!Float.isNaN(geoZ)) {
            return geoZ;
        }

        // Tentativa final: busca mais ampla
        float extendedRange = Math.max(fallbackRange * 1.5f, searchMargin * 2f);
        geoZ = GeoService.getInstance().getZ(worldId, targetX, targetY,
            originalZ + extendedRange, originalZ - extendedRange, instanceId);

        return !Float.isNaN(geoZ) ? geoZ : originalZ;
    }

    /**
     * Calcula a diferença máxima de Z permitida
     */
    private float calculateMaxZDiff(float distanceXY, Creature target) {
        if (distanceXY < 1.0f) {
            return 2.0f;
        } else if (distanceXY < 3.0f) {
            return 4.0f;
        } else if (distanceXY < 10.0f) {
            return 8.0f;
        } else {
            return 20.0f;
        }
    }

    /**
     * @param npc
     * @param creature
     * @return
     */
    private float getTargetZ(Npc npc, Creature creature) {
        float targetZ = creature.getZ();
        if (GeoDataConfig.GEO_NPC_MOVE && creature.isFlying() && !npc.isFlying()) {
            if (npc.getGameStats().checkGeoNeedUpdate()) {
                cachedTargetZ = GeoService.getInstance().getZ(creature);
            }
            targetZ = cachedTargetZ;
        }
        return targetZ;
    }

    /**
     * @param targetX
     * @param targetY
     * @param targetZ
     * @param offset
     * @return
     */
    protected void moveToLocation(float targetX, float targetY, float targetZ, float offset) {
        boolean directionChanged;
        float ownerX = owner.getX();
        float ownerY = owner.getY();
        float ownerZ = owner.getZ();
        directionChanged = targetX != targetDestX || targetY != targetDestY || targetZ != targetDestZ;

        if (directionChanged) {
            heading = (byte) (Math.toDegrees(Math.atan2(targetY - ownerY, targetX - ownerX)) / 3.0);
        }

        if (owner.getAi2().isLogging()) {
            AI2Logger.moveinfo(owner, "OLD targetDestX: " + targetDestX + " targetDestY: " + targetDestY + " targetDestZ " + targetDestZ);
        }

        // to prevent broken walkers in case of activating/deactivating zones
        if (targetX == 0.0f && targetY == 0.0f) {
            targetX = owner.getSpawn().getX();
            targetY = owner.getSpawn().getY();
            targetZ = owner.getSpawn().getZ();
        }

        targetDestX = targetX;
        targetDestY = targetY;
        targetDestZ = targetZ;

        if (owner.getAi2().isLogging()) {
            AI2Logger.moveinfo(owner, "ownerX=" + ownerX + " ownerY=" + ownerY + " ownerZ=" + ownerZ);
            AI2Logger.moveinfo(owner, "targetDestX: " + targetDestX + " targetDestY: " + targetDestY + " targetDestZ " + targetDestZ);
        }

        float currentSpeed = owner.getGameStats().getMovementSpeedFloat();
        float futureDistPassed = currentSpeed * (System.currentTimeMillis() - lastMoveUpdate) / 1000.0f;
        float dist = (float) MathUtil.getDistance(ownerX, ownerY, ownerZ, targetX, targetY, targetZ);

        if (owner.getAi2().isLogging()) {
            AI2Logger.moveinfo(owner, "futureDist: " + futureDistPassed + " dist: " + dist);
        }

        if (dist == 0.0f) {
            if (owner.getAi2().getState() == AIState.RETURNING) {
                if (owner.getAi2().isLogging()) {
                    AI2Logger.moveinfo(owner, "State RETURNING: abort move");
                }
                TargetEventHandler.onTargetReached((NpcAI2) owner.getAi2());
            }
            return;
        }

        if (futureDistPassed > dist) {
            futureDistPassed = dist;
        }
        
        // Código para processamento de caminhos em cache
        if (futureDistPassed == dist
                && (destination == Destination.TARGET_OBJECT || destination == Destination.HOME)) {
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
        if (ownerX == newX && ownerY == newY && owner.getSpawn().getRandomWalk() > 0) {
            return;
        }
        
        // CORREÇÃO ESCADAS: VALIDAÇÃO DE Z ADAPTATIVA
        boolean walkerMovement = destination == Destination.POINT && currentRoute != null;

        if (GeoDataConfig.GEO_NPC_MOVE && GeoDataConfig.GEO_ENABLE &&
            !walkerMovement &&
            owner.getAi2().getSubState() != AISubState.WALK_PATH &&
            owner.getAi2().getState() != AIState.RETURNING) {

            // Validação de Z melhorada
            if (!owner.isFlying()) {
                long now = System.currentTimeMillis();
                if (owner.getSpawn().getX() != targetDestX ||
                    owner.getSpawn().getY() != targetDestY ||
                    owner.getSpawn().getZ() != targetDestZ) {

                    float geoZ = GeoService.getInstance().getZ(owner.getWorldId(), newX, newY,
                        newZ + 2, Math.min(newZ, ownerZ) - 2, owner.getInstanceId());

                    if (!Float.isNaN(geoZ)) {
                        if (Math.abs(newZ - geoZ) > 1) {
                            directionChanged = true;
                        }
                        newZ = geoZ;

                        boolean isXYDestinationReached = MathUtil.getDistance(newX, newY, pointX, pointY) < MOVE_OFFSET;
                        if (isXYDestinationReached && MathUtil.getDistance(newX, newY, newZ, pointX, pointY, pointZ) > MOVE_OFFSET) {
                            pointZ = newZ;
                        }
                    }
                }
            }
        }

        if (owner.getAi2().isLogging()) {
            AI2Logger.moveinfo(owner, "newX=" + newX + " newY=" + newY + " newZ=" + newZ + " mask=" + movementMask);
        }

        World.getInstance().updatePosition(owner, newX, newY, newZ, heading, false);

        byte newMask = getMoveMask(directionChanged);
        if (movementMask != newMask) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "oldMask=" + movementMask + " newMask=" + newMask);
            }
            movementMask = newMask;
            PacketSendUtility.broadcastPacket(owner, new S_MOVE_NEW(owner));
        }
    }

    private byte getMoveMask(boolean directionChanged) {
        if (directionChanged) {
            return MovementMask.NPC_STARTMOVE;
        } else if (owner.getAi2().getState() == AIState.RETURNING) {
            return MovementMask.NPC_RUN_FAST;
        } else if (owner.getAi2().getState() == AIState.FOLLOWING) {
            return MovementMask.NPC_WALK_SLOW;
        } else if (destination == Destination.FLEE_FROM_FLYING_TARGET) {
            return MovementMask.NPC_RUN_FAST;
        }

        byte mask = MovementMask.IMMEDIATE;
        final Stat2 stat = owner.getGameStats().getMovementSpeed();
        if (owner.isInState(CreatureState.WEAPON_EQUIPPED)) {
            mask = stat.getBonus() < 0 ? MovementMask.NPC_RUN_FAST : MovementMask.NPC_RUN_SLOW;
        } else if (owner.isInState(CreatureState.WALKING) || owner.isInState(CreatureState.ACTIVE)) {
            mask = stat.getBonus() < 0 ? MovementMask.NPC_WALK_FAST : MovementMask.NPC_WALK_SLOW;
        }
        if (owner.isFlying()) {
            mask |= MovementMask.GLIDE;
        }
        return mask;
    }

    @Override
    public void abortMove() {
        if (!started.get()) {
            return;
        }
        resetMove();
        setAndSendStopMove(owner);
    }

    /**
     * Initialize values to default ones
     */
    public void resetMove() {
        if (owner.getAi2().isLogging()) {
            AI2Logger.moveinfo(owner, "MC perform stop");
        }
        started.set(false);
        targetDestX = 0.0f;
        targetDestY = 0.0f;
        targetDestZ = 0.0f;
        pointX = 0.0f;
        pointY = 0.0f;
        pointZ = 0.0f;
        nextPointFromGeo = false;
        
        // Reset NAV
        resetNavFailureTracker();
        cachedPathValid = false;
        cachedPath = null;
        
        // Reset flee tracking
        lastTargetX = 0;
        lastTargetY = 0;
        lastOwnerX = 0;
        lastOwnerY = 0;
        lastMovementTime = System.currentTimeMillis();
        
        // Reset destination para evitar fuga infinita
        if (destination == Destination.FLEE_FROM_FLYING_TARGET) {
            destination = Destination.TARGET_OBJECT;
        }
    }

    // NAV Failure Tracking Methods
    private void resetNavFailureTracker() {
        navFailureCount = 0;
        lastNavFailureTime = 0;
    }

    private boolean registerNavFailureAndCheckGiveup(Creature target) {
        long now = System.currentTimeMillis();
        if (now - lastNavFailureTime > NAV_FAILURE_RESET_MS) {
            navFailureCount = 0;
        }
        lastNavFailureTime = now;
        if (++navFailureCount >= MAX_NAV_FAILURES) {
            resetNavFailureTracker();
            // Aqui você pode adicionar lógica para o NPC desistir do alvo se necessário
            return true;
        }
        return false;
    }

    private boolean isNavFailurePath(float[][] path) {
        if (path == null) {
            return false;
        }
        if (path.length == 0) {
            return true;
        }
        if (path.length == 1) {
            float[] point = path[0];
            if (point.length >= 3) {
                double distance = MathUtil.getDistance(owner.getX(), owner.getY(), owner.getZ(),
                    point[0], point[1], point[2]);
                return distance <= NAV_FAILURE_DISTANCE_EPSILON;
            }
        }
        return false;
    }

    /**
     * Walker
     *
     * @param currentRoute
     */
    public void setCurrentRoute(List<RouteStep> currentRoute) {
        if (currentRoute == null) {
            AI2Logger.info(owner.getAi2(), String.format("MC: setCurrentRoute is setting route to null (NPC id: {})!!!", owner.getNpcId()));
        } else {
            this.currentRoute = currentRoute;
        }
        this.currentPoint = 0;
    }

    public void setRouteStep(RouteStep step, RouteStep prevStep) {
        Point2D dest = null;
        if (owner.getWalkerGroup() != null) {
            dest = WalkerGroup.getLinePoint(new Point2D(prevStep.getX(), prevStep.getY()), new Point2D(step.getX(), step.getY()), owner.getWalkerGroupShift());
            this.pointZ = prevStep.getZ();
            if (GeoDataConfig.GEO_ENABLE && GeoDataConfig.GEO_NPC_MOVE) {
                // TODO: fix Z
            }
            owner.getWalkerGroup().setStep(owner, step.getRouteStep());
        } else {
            this.pointZ = step.getZ();
        }
        this.currentPoint = step.getRouteStep() - 1;
        this.pointX = dest == null ? step.getX() : dest.getX();
        this.pointY = dest == null ? step.getY() : dest.getY();
        this.destination = Destination.POINT;
        this.walkPause = step.getRestTime();
    }

    public int getCurrentPoint() {
        return currentPoint;
    }

    public boolean isReachedPoint() {
        return MathUtil.getDistance(owner.getX(), owner.getY(), owner.getZ(), pointX, pointY, pointZ) < MOVE_OFFSET;
    }

    public void chooseNextStep() {
        int oldPoint = currentPoint;
        if (currentRoute == null) {
            WalkManager.stopWalking((NpcAI2) owner.getAi2());
            // log.warn("Bad Walker Id: " + owner.getNpcId() + " - point: " +
            // oldPoint);
            return;
        }
        if (currentPoint < (currentRoute.size() - 1)) {
            currentPoint++;
        } else {
            currentPoint = 0;
        }
        setRouteStep(currentRoute.get(currentPoint), currentRoute.get(oldPoint));
    }

    public int getWalkPause() {
        return walkPause;
    }

    public boolean isChangingDirection() {
        return currentPoint == 0;
    }

    @Override
    public final float getTargetX2() {
        return started.get() ? targetDestX : owner.getX();
    }

    @Override
    public final float getTargetY2() {
        return started.get() ? targetDestY : owner.getY();
    }

    @Override
    public final float getTargetZ2() {
        return started.get() ? targetDestZ : owner.getZ();
    }

    /**
     * @return
     */
    public boolean isFollowingTarget() {
        return destination == Destination.TARGET_OBJECT;
    }
    
    /**
     * Verifica se o NPC está fugindo de um alvo voador
     */
    public boolean isFleeingFromFlyingTarget() {
        return destination == Destination.FLEE_FROM_FLYING_TARGET;
    }

    public void storeStep() {
        if (owner.getAi2().getState() == AIState.RETURNING) {
            return;
        }
        if (lastSteps == null) {
            lastSteps = new LastUsedCache<Byte, Point3D>(10);
        }
        Point3D currentStep = new Point3D(owner.getX(), owner.getY(), owner.getZ());
        if (owner.getAi2().isLogging()) {
            AI2Logger.moveinfo(owner, "store back step: X=" + owner.getX() + " Y=" + owner.getY() + " Z=" + owner.getZ());
        }
        if (stepSequenceNr == 0 || MathUtil.getDistance(lastSteps.get(stepSequenceNr), currentStep) >= 10) {
            lastSteps.put(++stepSequenceNr, currentStep);
        }
    }

    public Point3D recallPreviousStep() {
        if (lastSteps == null) {
            lastSteps = new LastUsedCache<Byte, Point3D>(10);
        }

        Point3D result = stepSequenceNr == 0 ? null : lastSteps.get(stepSequenceNr--);

        if (result == null) {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "recall back step: spawn point");
            }
            targetDestX = owner.getSpawn().getX();
            targetDestY = owner.getSpawn().getY();
            targetDestZ = owner.getSpawn().getZ();
            result = new Point3D(targetDestX, targetDestY, targetDestZ);
        } else {
            if (owner.getAi2().isLogging()) {
                AI2Logger.moveinfo(owner, "recall back step: X=" + result.getX() + " Y=" + result.getY() + " Z=" + result.getZ());
            }
            targetDestX = result.getX();
            targetDestY = result.getY();
            targetDestZ = result.getZ();
        }

        return result;
    }

    public void clearBackSteps() {
        stepSequenceNr = 0;
        lastSteps = null;
        movementMask = MovementMask.IMMEDIATE;
    }
}