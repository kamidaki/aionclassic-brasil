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
package com.aionemu.gameserver.model.templates.item;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "Stigma")
public class Stigma
{
	@XmlElement(name = "require_skill")
	protected List<RequireSkill> requireSkill;
	
	@XmlAttribute
	protected int skillid;
	
	@XmlAttribute
	protected int skilllvl;
	
	@XmlAttribute
	protected int shard;
	
	public int getSkillid() {
		return skillid;
	}
	
	public int getSkilllvl() {
		return skilllvl;
	}
	
	public int getShard() {
		return shard;
	}
	
	public List<RequireSkill> getRequireSkill() {
		if (requireSkill == null) {
			requireSkill = new ArrayList<RequireSkill>();
		}
		return this.requireSkill;
	}
}