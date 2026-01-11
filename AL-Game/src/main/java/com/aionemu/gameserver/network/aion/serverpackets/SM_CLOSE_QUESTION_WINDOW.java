package com.aionemu.gameserver.network.aion.serverpackets;

import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;

public class SM_CLOSE_QUESTION_WINDOW extends AionServerPacket {

	public static SM_CLOSE_QUESTION_WINDOW STR_DUEL_REQUESTER_WITHDRAW_REQUEST(String value0) {
		return new SM_CLOSE_QUESTION_WINDOW(1300134, value0);
	}

	public static SM_CLOSE_QUESTION_WINDOW STR_DUEL_HE_REJECT_DUEL(String value0) {
		return new SM_CLOSE_QUESTION_WINDOW(1300097, value0);
	}

	public static SM_CLOSE_QUESTION_WINDOW CLOSE_QUESTION_WINDOW() {
		return new SM_CLOSE_QUESTION_WINDOW(0);
	}

	private static final int MAX_PARAM_COUNT = 3;

	private final int msgId;
	private final Object[] params;

	public SM_CLOSE_QUESTION_WINDOW(int msgId, Object... params) {
		this.msgId = msgId;
		this.params = params;
	}

	@Override
	protected void writeImpl(AionConnection con) {
		writeD(0);
		writeD(msgId);
		for (int i = 0; i < MAX_PARAM_COUNT; i++)
			writeS(i < params.length ? String.valueOf(params[i]) : null);
	}
}
