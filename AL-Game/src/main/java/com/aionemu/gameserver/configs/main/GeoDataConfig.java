/*
 *  Aion Classic Emu based on Aion Encom Source Files
 *
 *  ENCOM Team based on Aion-Lighting Open Source
 *  All Copyrights : "Data/Copyrights/AEmu-Copyrights.text
 *
 *  iMPERIVM.FUN - AION DEVELOPMENT FORUM
 *  Forum: <http://https://imperivm.fun/>
 *
 */
package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

import java.util.Set;

public class GeoDataConfig {

	/**
	 * Geodata enable
	 */
	@Property(key = "gameserver.geodata.enable", defaultValue = "false")
	public static boolean GEO_ENABLE;

	/**
	 * Enable canSee checks using geodata.
	 */
	@Property(key = "gameserver.geodata.cansee.enable", defaultValue = "true")
	public static boolean CANSEE_ENABLE;

	/**
	 * Enable Geo checks during npc movement (prevent flying mobs)
	 */
	@Property(key = "gameserver.geo.npc.move", defaultValue = "false")
	public static boolean GEO_NPC_MOVE;

	/**
	 * Enable geo materials using skills
	 */
	@Property(key = "gameserver.geo.materials.enable", defaultValue = "false")
	public static boolean GEO_MATERIALS_ENABLE;

	/**
	 * Enable geo shields
	 */
	@Property(key = "gameserver.geo.shields.enable", defaultValue = "true")
	public static boolean GEO_SHIELDS_ENABLE;
	
	/**
	 * Enable geo doors
	 */
	@Property(key = "gameserver.geo.doors.enable", defaultValue = "false")
	public static boolean GEO_DOORS_ENABLE;
	
	/**
	 * Object factory for geodata primitives enabled
	 */
	@Property(key = "gameserver.geodata.objectfactory.enabled", defaultValue = "true")
	public static boolean GEO_OBJECT_FACTORY_ENABLE;

	/**
	 * Directory containing navigation mesh files
	 */
	@Property(key = "gameserver.geodata.nav.dir", defaultValue = "data/nav/")
	public static String GEO_NAV_DIR;


	/**
	 * Enables using ./data/nav to load nav mesh for each map and pathfind with the data
	 */
	@Property(key = "gameserver.geo.nav.pathfinding.enable", defaultValue = "false")
	public static boolean GEO_NAV_ENABLE;

	/**
	 * List of world IDs to preload navigation meshes for
	 */
	@Property(key = "gameserver.geodata.nav.warmup.worlds", defaultValue = "")
	public static Set<Integer> GEO_NAV_PRELOAD_WORLDS;
}
