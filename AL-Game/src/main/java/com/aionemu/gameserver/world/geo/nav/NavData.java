package com.aionemu.gameserver.world.geo.nav;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.geoEngine.scene.NavGeometry;
import com.aionemu.gameserver.utils.CityMapUtil;
import com.aionemu.commons.utils.concurrent.PriorityThreadFactory;
import com.aionemu.gameserver.world.WorldMapType;

/**
 * Sistema de gerenciamento de NavMeshes com correção de vazamentos de memória.
 *
 * FUNCIONALIDADES:
 * - Cache LRU inteligente com limite de mapas
 * - Carregamento assíncrono de NavMeshes
 * - Limpeza automática de mapas inativos
 * - Rastreamento de atividade por jogadores
 * - Proteção de mapas críticos
 * - ByteBuffer direto com liberação explícita
 * - Thread-safe com ReadWriteLock
 *
 * @author KAMIDAKI - Versão original OLD CLASS
 * @author KLASIX - Adaptação completa para Classic 2.4 (Java 8)
 */
public class NavData {

	private static final Logger LOG = LoggerFactory.getLogger(NavData.class);

	// Configurações do cache
	private static final int MAX_CACHED_MAPS = 15;
	private static final long INACTIVE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutos
	private static final long CLEANUP_INTERVAL_MS = 2 * 60 * 1000; // 2 minutos

	private static final Path NAV_DIR = Paths.get(GeoDataConfig.GEO_NAV_DIR);

	// Cache LRU thread-safe
	private final LRUCache<Integer, CachedGeoMap> navMaps;
	private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
	private final AtomicLong totalCacheSizeMB = new AtomicLong(0);

	// Rastreamento de atividade
	private final ConcurrentHashMap<Integer, MapActivity> mapActivity = new ConcurrentHashMap<Integer, MapActivity>();

	// Thread pool para operações assíncronas
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new PriorityThreadFactory("NavDataScheduler", Thread.NORM_PRIORITY));

	private NavData() {
		this.navMaps = new LRUCache<Integer, CachedGeoMap>(MAX_CACHED_MAPS);
		startCleanupScheduler();
		LOG.info("NAVMESH: Sistema de gerenciamento inicializado (Max: {} mapas, Timeout: {}min)",
				MAX_CACHED_MAPS, INACTIVE_TIMEOUT_MS / 60000);
	}

	// ===============================
	// PRÉ-CARREGAMENTO
	// ===============================

	/**
	 * Pré-carrega mapas críticos na inicialização do servidor
	 */
	void preloadNavMaps(Collection<Integer> worlds) {
		if (worlds == null || worlds.isEmpty()) {
			return;
		}

		int requested = 0;
		int loaded = 0;
		for (Integer worldId : worlds) {
			if (worldId == null) {
				continue;
			}
			requested++;

			if (CityMapUtil.isDefaultPathfinding(worldId)) {
				LOG.debug("NAVMESH: Ignorando pré-carregamento do mapa {} (usa pathfinding padrão).", worldId);
				continue;
			}

			MapActivity activity = getOrCreateActivity(worldId);
			activity.markAsCritical();

			if (loadNavMapIfAbsent(worldId) != null) {
				loaded++;
			}
		}

		LOG.info("NAVMESH: Pré-carregamento concluído: {}/{} mapas carregados", loaded, requested);
	}

	// ===============================
	// GERENCIAMENTO DE JOGADORES
	// ===============================

	/**
	 * Chamado quando um jogador entra em um mapa
	 */
	public void onPlayerEnterMap(int worldId) {
		if (CityMapUtil.isDefaultPathfinding(worldId)) {
			return;
		}

		MapActivity activity = getOrCreateActivity(worldId);
		activity.incrementPlayerCount();

		cacheLock.readLock().lock();
		try {
			if (!navMaps.containsKey(worldId)) {
				loadNavMapAsync(worldId);
			}
		} finally {
			cacheLock.readLock().unlock();
		}
	}

	/**
	 * Chamado quando um jogador sai de um mapa
	 */
	public void onPlayerLeaveMap(int worldId) {
		if (CityMapUtil.isDefaultPathfinding(worldId)) {
			return;
		}

		MapActivity activity = mapActivity.get(worldId);
		if (activity != null) {
			activity.decrementPlayerCount();
		}
	}

	// ===============================
	// ACESSO AO NAVMESH
	// ===============================

	/**
	 * Retorna o GeoMap (NavMesh) para o worldId especificado
	 */
	public GeoMap getNavMap(int worldId) {
		if (CityMapUtil.isDefaultPathfinding(worldId)) {
			return null;
		}

		MapActivity activity = getOrCreateActivity(worldId);
		activity.updateLastAccess();

		return loadNavMapIfAbsent(worldId);
	}

	/**
	 * Carrega NavMap se não estiver em cache (thread-safe)
	 */
	private GeoMap loadNavMapIfAbsent(int worldId) {
		// Fast path: read lock
		cacheLock.readLock().lock();
		try {
			CachedGeoMap cached = navMaps.get(worldId);
			if (cached != null) {
				cached.updateAccess();
				return cached.geoMap;
			}
		} finally {
			cacheLock.readLock().unlock();
		}

		// Cache miss: write lock
		cacheLock.writeLock().lock();
		try {
			// Double-check
			CachedGeoMap cached = navMaps.get(worldId);
			if (cached != null) {
				cached.updateAccess();
				return cached.geoMap;
			}

			// Carregar do disco
			GeoMap geoMap = loadNavFromDisk(worldId);
			if (geoMap == null) {
				return null;
			}

			// Adicionar ao cache
			CachedGeoMap cachedGeoMap = new CachedGeoMap(geoMap, worldId);
			navMaps.put(worldId, cachedGeoMap);
			totalCacheSizeMB.addAndGet(cachedGeoMap.getEstimatedSizeMB());

			LOG.info("NAVMESH: Mapa {} carregado. Cache: {}",
					formatWorldName(worldId), describeCachedMaps());

			return geoMap;
		} finally {
			cacheLock.writeLock().unlock();
		}
	}

	/**
	 * Carrega NavMap de forma assíncrona
	 */
	private void loadNavMapAsync(final int worldId) {
		scheduler.submit(new Runnable() {
			@Override
			public void run() {
				try {
					loadNavMapIfAbsent(worldId);
				} catch (Exception e) {
					LOG.error("NAVMESH: Erro ao carregar mapa {} assincronamente.", formatWorldName(worldId), e);
				}
			}
		});
	}

	// ===============================
	// CARREGAMENTO DO DISCO
	// ===============================

	/**
	 * Carrega NavMesh do disco
	 */
	private GeoMap loadNavFromDisk(int worldId) {
		Path navFile = NAV_DIR.resolve(worldId + ".nav");

		if (!Files.exists(navFile)) {
			LOG.warn("NAVMESH: Arquivo não encontrado: {}", navFile);
			return null;
		}

		try {
			long startTime = System.currentTimeMillis();
			GeoMap map = loadNavFile(navFile, worldId);
			long loadTime = System.currentTimeMillis() - startTime;

			long fileSizeMB = Files.size(navFile) / (1024 * 1024);
			LOG.debug("NAVMESH: Mapa {} carregado em {}ms ({}MB)",
					formatWorldName(worldId), loadTime, fileSizeMB);

			return map;
		} catch (IOException e) {
			LOG.error("NAVMESH: Erro ao carregar mapa {}.", formatWorldName(worldId), e);
			return null;
		}
	}

	/**
	 * CORREÇÃO CRÍTICA: Usa ByteBuffer direto ao invés de memory-mapped
	 * Permite liberação explícita da memória (Java 8 compatível)
	 */
	private GeoMap loadNavFile(Path file, int worldId) throws IOException {
		RandomAccessFile raf = null;
		FileChannel channel = null;

		try {
			raf = new RandomAccessFile(file.toFile(), "r");
			channel = raf.getChannel();

			long fileSize = channel.size();

			// ByteBuffer direto (pode ser liberado explicitamente)
			ByteBuffer buffer = ByteBuffer.allocateDirect((int) fileSize);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			// Ler arquivo para o buffer
			channel.read(buffer);
			buffer.flip();

			// Processar
			GeoMap result = processNavBuffer(buffer, worldId);

			// IMPORTANTE: Limpar buffer após uso (Java 8 compatível)
			cleanDirectBuffer(buffer);

			return result;
		} finally {
			if (channel != null) {
				try {
					channel.close();
				} catch (IOException e) {
					LOG.warn("Erro ao fechar FileChannel", e);
				}
			}
			if (raf != null) {
				try {
					raf.close();
				} catch (IOException e) {
					LOG.warn("Erro ao fechar RandomAccessFile", e);
				}
			}
		}
	}

	/**
	 * Limpa ByteBuffer direto explicitamente (Java 8 compatível)
	 */
	private void cleanDirectBuffer(ByteBuffer buffer) {
		if (buffer == null || !buffer.isDirect()) {
			return;
		}

		try {
			Method cleanerMethod = buffer.getClass().getMethod("cleaner");
			cleanerMethod.setAccessible(true);
			Object cleaner = cleanerMethod.invoke(buffer);

			if (cleaner != null) {
				Method cleanMethod = cleaner.getClass().getMethod("clean");
				cleanMethod.invoke(cleaner);
			}
		} catch (Exception e) {
			// Fallback: deixar o GC lidar com isso
			LOG.debug("NAVMESH: Não foi possível limpar buffer explicitamente (será coletado pelo GC)");
		}
	}

	/**
	 * Processa o ByteBuffer e cria o GeoMap
	 */
	private GeoMap processNavBuffer(ByteBuffer buffer, int worldId) {
		int worldSize = 0;
		if (DataManager.WORLD_MAPS_DATA != null) {
			WorldMapTemplate template = DataManager.WORLD_MAPS_DATA.getTemplate(worldId);
			if (template != null) {
				worldSize = template.getWorldSize();
			}
		}
		GeoMap map = new GeoMap(String.valueOf(worldId), worldSize);

		// Ler vértices compartilhados
		int floatCount = buffer.getInt();
		float[] sharedVertices = new float[floatCount];
		for (int i = 0; i < floatCount; i++) {
			sharedVertices[i] = buffer.getFloat();
		}

		// Ler triângulos
		int triangleCount = buffer.getInt();
		NavGeometry[] triangles = new NavGeometry[triangleCount];

		int[] edge1Connections = new int[triangleCount];
		int[] edge2Connections = new int[triangleCount];
		int[] edge3Connections = new int[triangleCount];

		for (int i = 0; i < triangleCount; i++) {
			int index0 = buffer.getInt();
			int index1 = buffer.getInt();
			int index2 = buffer.getInt();

			triangles[i] = new NavGeometry(sharedVertices, index0, index1, index2);

			edge1Connections[i] = buffer.getInt();
			edge2Connections[i] = buffer.getInt();
			edge3Connections[i] = buffer.getInt();
		}

		// Conectar arestas
		for (int i = 0; i < triangleCount; i++) {
			if (edge1Connections[i] != -1) {
				triangles[i].setEdge1(triangles[edge1Connections[i]]);
			}
			if (edge2Connections[i] != -1) {
				triangles[i].setEdge2(triangles[edge2Connections[i]]);
			}
			if (edge3Connections[i] != -1) {
				triangles[i].setEdge3(triangles[edge3Connections[i]]);
			}

			triangles[i].updateModelBound();
			map.attachChild(triangles[i]);
		}

		map.updateModelBound();
		return map;
	}

	// ===============================
	// LIMPEZA AUTOMÁTICA
	// ===============================

	/**
	 * Inicia o scheduler de limpeza automática
	 */
	private void startCleanupScheduler() {
		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					cleanupInactiveMaps();
				} catch (Exception e) {
					LOG.error("NAVMESH: Erro durante limpeza automática.", e);
				}
			}
		}, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	/**
	 * Limpeza de mapas inativos
	 */
	private void cleanupInactiveMaps() {
		cacheLock.writeLock().lock();
		try {
			long now = System.currentTimeMillis();
			int removedCount = 0;

			// Iterar sobre uma cópia das entradas (Java 8 safe)
			Map.Entry<Integer, CachedGeoMap>[] entries = navMaps.entrySet().toArray(new Map.Entry[0]);

			for (Map.Entry<Integer, CachedGeoMap> entry : entries) {
				int worldId = entry.getKey();
				CachedGeoMap cached = entry.getValue();

				MapActivity activity = mapActivity.get(worldId);

				// Não remover mapas críticos ou com jogadores
				if (activity != null && (activity.isCritical() || activity.getPlayerCount() > 0)) {
					continue;
				}

				boolean isInactive = activity == null ||
						(activity.getPlayerCount() == 0 &&
								now - activity.getLastAccessTime() > INACTIVE_TIMEOUT_MS);

				if (isInactive && !isCriticalWorld(worldId)) {
					// Limpar referências explicitamente
					totalCacheSizeMB.addAndGet(-cached.getEstimatedSizeMB());
					cached.dispose();
					navMaps.remove(worldId);
					mapActivity.remove(worldId);

					removedCount++;

					LOG.info("NAVMESH: Mapa {} removido (inativo). Cache: {}",
							formatWorldName(worldId), describeCachedMaps());
				}
			}

			if (removedCount > 0) {
				LOG.info("NAVMESH: Limpeza concluída. {} mapas removidos. Cache atual: {}",
						removedCount, describeCachedMaps());
			}
		} finally {
			cacheLock.writeLock().unlock();
		}
	}

	/**
	 * Verifica se um mundo é crítico (não deve ser removido)
	 */
	private boolean isCriticalWorld(int worldId) {
		MapActivity activity = mapActivity.get(worldId);
		if (activity != null && activity.isCritical()) {
			return true;
		}
		return GeoDataConfig.getPreloadWorlds().contains(worldId);
	}

	// ===============================
	// UTILITÁRIOS
	// ===============================

	/**
	 * Descreve os mapas atualmente em cache
	 */
	private String describeCachedMaps() {
		if (navMaps.isEmpty()) {
			return "vazio";
		}

		StringBuilder sb = new StringBuilder();
		int count = 0;
		for (Integer worldId : navMaps.keySet()) {
			if (count > 0) {
				sb.append(", ");
			}
			sb.append(formatWorldName(worldId));
			count++;
		}
		return sb.toString();
	}

	/**
	 * Formata o nome do mundo de forma legível
	 */
	private String formatWorldName(int worldId) {
		WorldMapType world = WorldMapType.getWorld(worldId);
		if (world == null) {
			return String.valueOf(worldId);
		}

		String[] parts = world.name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return builder.toString();
	}

	/**
	 * Obtém ou cria atividade de um mapa
	 */
	private MapActivity getOrCreateActivity(int worldId) {
		MapActivity activity = mapActivity.get(worldId);
		if (activity == null) {
			activity = new MapActivity();
			MapActivity existing = mapActivity.putIfAbsent(worldId, activity);
			if (existing != null) {
				activity = existing;
			}
		}
		return activity;
	}

	/**
	 * Retorna estatísticas do cache
	 */
	public CacheStats getStats() {
		cacheLock.readLock().lock();
		try {
			int totalMaps = navMaps.size();
			int activeMaps = 0;
			int criticalMaps = 0;

			for (MapActivity activity : mapActivity.values()) {
				if (activity.getPlayerCount() > 0) {
					activeMaps++;
				}
				if (activity.isCritical()) {
					criticalMaps++;
				}
			}

			return new CacheStats(totalMaps, MAX_CACHED_MAPS, activeMaps, criticalMaps, totalCacheSizeMB.get());
		} finally {
			cacheLock.readLock().unlock();
		}
	}

	// ===============================
	// CLASSES INTERNAS
	// ===============================

	/**
	 * LRU Cache thread-safe com remoção inteligente
	 */
	private class LRUCache<K, V> extends LinkedHashMap<K, V> {
		private static final long serialVersionUID = 1L;
		private final int maxSize;

		public LRUCache(int maxSize) {
			super(maxSize + 1, 0.75f, true);
			this.maxSize = maxSize;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
			if (size() <= maxSize) {
				return false;
			}

			Integer worldId = (Integer) eldest.getKey();
			MapActivity activity = mapActivity.get(worldId);

			// Não remover mapas críticos ou com jogadores
			if (activity != null && (activity.isCritical() || activity.getPlayerCount() > 0)) {
				return false;
			}

			// Limpar antes de remover
			CachedGeoMap cached = (CachedGeoMap) eldest.getValue();
			totalCacheSizeMB.addAndGet(-cached.getEstimatedSizeMB());
			cached.dispose();
			mapActivity.remove(worldId);

			LOG.info("NAVMESH: LRU removeu mapa {}. Cache: {}",
					formatWorldName(worldId), describeCachedMaps());

			return true;
		}
	}

	/**
	 * Wrapper do GeoMap com informações de cache
	 */
	private static class CachedGeoMap {
		final GeoMap geoMap;
		final int worldId;
		final long creationTime;
		private final long estimatedSizeMB;
		volatile long lastAccessTime;
		private volatile boolean disposed = false;

		CachedGeoMap(GeoMap geoMap, int worldId) {
			this.geoMap = geoMap;
			this.worldId = worldId;
			this.creationTime = System.currentTimeMillis();
			this.lastAccessTime = creationTime;
			this.estimatedSizeMB = estimateSizeMB(geoMap);
		}

		void updateAccess() {
			this.lastAccessTime = System.currentTimeMillis();
		}

		/**
		 * CRÍTICO: Limpa referências explicitamente
		 */
		void dispose() {
			if (disposed) {
				return;
			}
			disposed = true;

			// Limpar referências no GeoMap
			try {
				if (geoMap != null && geoMap.getChildren() != null) {
					geoMap.getChildren().clear();
				}
			} catch (Exception e) {
				// Ignorar erros de limpeza
			}
		}

		long getEstimatedSizeMB() {
			if (disposed) {
				return 0;
			}
			return estimatedSizeMB;
		}

		private static long estimateSizeMB(GeoMap geoMap) {
			if (geoMap == null || geoMap.getChildren() == null) {
				return 1;
			}
			int triangleCount = geoMap.getChildren().size();
			return Math.max(1, (triangleCount * 200L) / (1024 * 1024));
		}
	}

	/**
	 * Rastreamento de atividade de um mapa
	 */
	private static class MapActivity {
		private final AtomicInteger playerCount = new AtomicInteger(0);
		private volatile long lastAccessTime = System.currentTimeMillis();
		private volatile boolean critical = false;

		void incrementPlayerCount() {
			playerCount.incrementAndGet();
			lastAccessTime = System.currentTimeMillis();
		}

		void decrementPlayerCount() {
			int current = playerCount.get();
			if (current > 0) {
				playerCount.compareAndSet(current, current - 1);
			}
			lastAccessTime = System.currentTimeMillis();
		}

		int getPlayerCount() {
			return playerCount.get();
		}

		void updateLastAccess() {
			lastAccessTime = System.currentTimeMillis();
		}

		long getLastAccessTime() {
			return lastAccessTime;
		}

		void markAsCritical() {
			critical = true;
		}

		boolean isCritical() {
			return critical;
		}
	}

	/**
	 * Estatísticas do cache
	 */
	public static class CacheStats {
		public final int cachedMaps;
		public final int maxMaps;
		public final int activeMaps;
		public final int criticalMaps;
		public final long totalSizeMB;

		CacheStats(int cached, int max, int active, int critical, long sizeMB) {
			this.cachedMaps = cached;
			this.maxMaps = max;
			this.activeMaps = active;
			this.criticalMaps = critical;
			this.totalSizeMB = sizeMB;
		}

		@Override
		public String toString() {
			return String.format("NavCache: %d/%d | Ativos: %d | Críticos: %d | Memória: %dMB",
					cachedMaps, maxMaps, activeMaps, criticalMaps, totalSizeMB);
		}
	}

	// ===============================
	// SINGLETON
	// ===============================

	public static NavData getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {
		static final NavData INSTANCE = new NavData();
	}
}