package com.aionemu.gameserver.network.aion.clientpackets;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Pet;
import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.templates.windstreams.Location2D;
import com.aionemu.gameserver.model.templates.windstreams.WindstreamTemplate;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.network.aion.serverpackets.S_CUSTOM_ANIM;
import com.aionemu.gameserver.network.aion.serverpackets.S_EVENT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.S_WIND_STATE_INFO;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.services.WeatherService;
import com.aionemu.gameserver.services.rift.RiftInformer;
import com.aionemu.gameserver.world.World;


import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.world.geo.nav.NavData;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;
import com.aionemu.gameserver.utils.PacketSendUtility;


public class C_LEVEL_READY extends AionClientPacket
{
	public C_LEVEL_READY(int opcode, State state, State... restStates)
	{
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl()
	{
	}

	@Override
	protected void runImpl()
	{
		Player activePlayer = getConnection().getActivePlayer();
		sendPacket(new SM_PLAYER_INFO(activePlayer, false));
		activePlayer.getController().startProtectionActiveTask();
		sendPacket(new S_CUSTOM_ANIM(activePlayer.getObjectId(), activePlayer.getMotions().getActiveMotions()));
		WindstreamTemplate template = DataManager.WINDSTREAM_DATA.getStreamTemplate(activePlayer.getPosition().getMapId());
		WorldMapTemplate templateMaps = DataManager.WORLD_MAPS_DATA.getTemplate(activePlayer.getWorldId());
		String mapName = templateMaps != null && templateMaps.getName() != null ? templateMaps.getName() : "ID " + activePlayer.getWorldId();

		boolean hasNavMesh = GeoDataConfig.GEO_NAV_ENABLE && NavData.getInstance().getNavMap(activePlayer.getWorldId()) != null;
		String message = hasNavMesh ? String.format("\uE027 Mapa: %s | Navegação NPC: Ativado \uE052", mapName)
				: String.format("\uE027 Mapa: %s | Navegação NPC: Sem dados disponíveis \uE051", mapName);
		PacketSendUtility.sendWhiteMessage(activePlayer, message);


		Location2D location;
		if (template != null) {
			for (int i = 0; i < template.getLocations().getLocation().size(); i++) {
				location = template.getLocations().getLocation().get(i);
				sendPacket(new S_WIND_STATE_INFO(location.getFlyPathType().getId(), template.getMapid(), location.getId(), location.getState()));
			}
		}
		location = null;
		template = null;
		if (activePlayer.isSpawned()) {
			World.getInstance().despawn(activePlayer);
		}
		World.getInstance().spawn(activePlayer);
		activePlayer.getController().refreshZoneImpl();
		if (activePlayer.isInSiegeWorld()) {
			SiegeService.getInstance().onEnterSiegeWorld(activePlayer);
		}
		activePlayer.getController().updateZone();
		activePlayer.getController().updateNearbyQuests();
		WeatherService.getInstance().loadWeather(activePlayer);
		QuestEngine.getInstance().onEnterWorld(new QuestEnv(null, activePlayer, 0, 0));
		activePlayer.getController().onEnterWorld();
		//Rift.
		RiftInformer.sendRiftsInfo(activePlayer);
		activePlayer.getEffectController().updatePlayerEffectIcons();
		sendPacket(S_EVENT.cubeSize(StorageType.CUBE, activePlayer));
		//SerialKillerService.getInstance().onEnterMap(activePlayer);
		//Pet.
		Pet pet = activePlayer.getPet();
		if (pet != null && !pet.isSpawned()) {
			World.getInstance().spawn(pet);
		}
		//Summon.
		Summon summon = activePlayer.getSummon();
		if (summon != null && !summon.isSpawned()) {
			World.getInstance().spawn(summon);
		}
		activePlayer.setPortAnimation(0);
	}
}