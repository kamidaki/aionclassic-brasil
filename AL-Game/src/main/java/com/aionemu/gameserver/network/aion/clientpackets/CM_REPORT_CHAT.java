package com.aionemu.gameserver.network.aion.clientpackets;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.SystemMessageId;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;

public class CM_REPORT_CHAT extends AionClientPacket
{

	private String name;

	public CM_REPORT_CHAT(int opcode, AionConnection.State state, AionConnection.State... restStates)
	{
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl()
	{
		this.name = readS();
	}

	@Override
	protected void runImpl()
	{
		Player player = getConnection().getActivePlayer();
		PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(SystemMessageId.REPORT_CHAT, this.name));
	}
}