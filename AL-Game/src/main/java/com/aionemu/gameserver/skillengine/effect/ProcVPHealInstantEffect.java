package com.aionemu.gameserver.skillengine.effect;

import javax.xml.bind.annotation.XmlAttribute;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.network.aion.serverpackets.SM_STATS_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_STATUPDATE_EXP;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.utils.PacketSendUtility;

public class ProcVPHealInstantEffect extends EffectTemplate
{
	@XmlAttribute(required = true)
	protected int points;
	
	public void applyEffect(Effect effect) {
		if ((effect.getEffected() instanceof Player)) {
			final Player player = (Player) effect.getEffected();
			PlayerCommonData pcd = player.getCommonData();
			if (pcd.getCurrentReposteEnergy() < pcd.getMaxReposteEnergy()) {
				pcd.addReposteEnergy(points); //15% = 3750000 Pts.
				PacketSendUtility.sendPacket(player, new SM_STATS_INFO(player));
				PacketSendUtility.sendPacket(player, new SM_STATUPDATE_EXP(pcd.getExpShown(), pcd.getExpRecoverable(), pcd.getExpNeed(), pcd.getCurrentReposteEnergy(), pcd.getMaxReposteEnergy()));
			} else if (pcd.getCurrentReposteEnergy() >= pcd.getMaxReposteEnergy()) {
				PacketSendUtility.sendPacket(player, new SM_STATS_INFO(player));
			    //The Energy of Repose is ineffective in your current Restriction Phase.
				PacketSendUtility.playerSendPacketTime(player, SM_SYSTEM_MESSAGE.STR_MSG_BOT_CANNOT_RECEIVE_VITAL_BONUS, 0);
				PacketSendUtility.sendPacket(player, new SM_STATUPDATE_EXP(pcd.getExpShown(), pcd.getExpRecoverable(), pcd.getExpNeed(), pcd.getCurrentReposteEnergy(), pcd.getMaxReposteEnergy()));
				return;				
			}
		}
	}
}