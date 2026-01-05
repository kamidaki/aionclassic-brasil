package com.aionemu.gameserver.world.geo.nav;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;


import com.aionemu.gameserver.geoEngine.scene.NavGeometry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.utils.CityMapUtil;
import gnu.trove.map.hash.TIntObjectHashMap;

/*
 * Similar to {@link com.aionemu.gameserver.world.geo.GeoData GeoData}, this class stores
 * {@link GeoMap GeoMaps} that represent navigable space within a level. These maps are holding
 * Nav Meshes that can be used to pathfind.
 *
 * @author  KAMIDAKI
 */
public class NavData {

	private static final Logger LOG = LoggerFactory.getLogger(NavData.class);
	private final TIntObjectHashMap<GeoMap> navMaps = new TIntObjectHashMap<>();

	private static final Path NAV_DIR = Paths.get(GeoDataConfig.GEO_NAV_DIR);

	private NavData() {}

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
			if (loadNavMapIfAbsent(worldId) != null) {
				loaded++;
			}
		}

		LOG.info("NAVMESH: Pré-carregadas {}/{} malhas de navegação solicitadas.", loaded, requested);
	}

	/**
	 * Returns the GeoMap representing the Nav Mesh for the given Map ID.
	 * <p>
	 * If no such map exists, this method will return null.
	 *
	 * @param worldId -- The ID of the map to find the Nav Mesh for
	 */
	public GeoMap getNavMap(int worldId) {
		return loadNavMapIfAbsent(worldId);
	}

	Iterable<GeoMap> getNavMaps() {
		return navMaps.valueCollection();
	}

	private GeoMap loadNavMapIfAbsent(int worldId) {
		synchronized (navMaps) {
			GeoMap existing = navMaps.get(worldId);
			if (existing != null) {
				return existing;
			}
		}

		if (CityMapUtil.isDefaultPathfinding(worldId)) {
			return null;
		}

		GeoMap geoMap = new GeoMap(worldId);
		try {
			if (!loadNav(worldId, geoMap)) {
				LOG.warn("NAVMESH: Arquivo de navegação não encontrado para o mapa {}.", worldId);
				return null;
			}
		} catch (IOException e) {
			LOG.error("NAVMESH: Erro ao carregar a malha de navegação do mapa {}.", worldId, e);
			return null;
		}

		synchronized (navMaps) {
			GeoMap existing = navMaps.get(worldId);
			if (existing != null) {
				return existing;
			}
			navMaps.put(worldId, geoMap);
			return geoMap;
		}
	}

	private boolean loadNav(int worldId, GeoMap map) throws IOException {
		Path navFile = NAV_DIR.resolve(worldId + ".nav");
		if (!Files.exists(navFile)) {
			return false;
		}
		try (FileChannel roChannel = FileChannel.open(navFile, StandardOpenOption.READ)) {
			ByteBuffer nav = ByteBuffer.allocate((int) roChannel.size());
			roChannel.read(nav);
			nav.order(ByteOrder.LITTLE_ENDIAN);
			nav.flip();

			int floatCount = nav.getInt();
			nav.position((floatCount * 4) + 4);

			NavGeometry[] triangles = new NavGeometry[nav.getInt()];
			EdgeConnectionHolder[] triCon = new EdgeConnectionHolder[triangles.length];
			for (int i = 0; i < triangles.length; i++) {
				final int[] index = new int[3];
				index[0] = nav.getInt();
				index[1] = nav.getInt();
				index[2] = nav.getInt();

				triangles[i] = new NavGeometry(null, getVertices(nav, index));

				triCon[i] = new EdgeConnectionHolder();
				triCon[i].id1 = nav.getInt(); //Edge connection 1
				triCon[i].id2 = nav.getInt(); //Edge connection 2
				triCon[i].id3 = nav.getInt(); //Edge connection 3
			}

			for (int i = 0; i < triangles.length; i++) {
				if (triCon[i].id1 != -1) triangles[i].setEdge1(triangles[triCon[i].id1]);
				if (triCon[i].id2 != -1) triangles[i].setEdge2(triangles[triCon[i].id2]);
				if (triCon[i].id3 != -1) triangles[i].setEdge3(triangles[triCon[i].id3]);
				triangles[i].updateModelBound();
				map.attachChild(triangles[i]);
			}
			map.updateModelBound();
		}
		return true;
	}

	private static float[] getVertices(ByteBuffer nav, int[] indices) {
		float[] ret = new float[indices.length * 3];
		for (int i = 0; i < indices.length; i++) {
			ret[i * 3] = nav.getFloat((indices[i] * 4 * 3) + 4);
			ret[(i * 3) + 1] = nav.getFloat((indices[i] * 4 * 3) + 8);
			ret[(i * 3) + 2] = nav.getFloat((indices[i] * 4 * 3) + 12);
		}
		return ret;
	}

	private static class EdgeConnectionHolder {
		int id1;
		int id2;
		int id3;
	}

	public static NavData getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {
		static final NavData INSTANCE = new NavData();
	}
}