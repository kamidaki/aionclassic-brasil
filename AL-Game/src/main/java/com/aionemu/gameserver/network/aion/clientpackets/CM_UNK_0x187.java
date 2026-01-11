package com.aionemu.gameserver.network.aion.clientpackets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;

public class CM_UNK_0x187 extends AionClientPacket {

    private static final Logger log = LoggerFactory.getLogger(CM_UNK_0x187.class);

    public CM_UNK_0x187(int opcode, State state, State... restStates) {
        super(opcode, state, restStates);
    }

    @Override
	protected void readImpl() {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < 16; i++) { // Sicherheitslimit
			try {
				sb.append(String.format("%02X ", readC() & 0xFF));
			} catch (Exception e) {
				break;
			}
		}

		if (sb.length() == 0) {
			log.info("CM_UNK_0x187: opcode-only packet");
		} else {
			log.info("CM_UNK_0x187 raw (truncated): " + sb);
		}
	}

    @Override
    protected void runImpl() {
    }
}

