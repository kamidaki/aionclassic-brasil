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
package com.aionemu.gameserver.dataholders;

import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.aionemu.gameserver.model.instancerift.InstanceRiftLocation;
import com.aionemu.gameserver.model.templates.instancerift.InstanceRiftTemplate;

import javolution.util.FastMap;

/**
 * @author Rinzler (Encom)
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "instance_rift")
public class InstanceRiftData
{
	@XmlElement(name = "instance_location")
	private List<InstanceRiftTemplate> instanceRiftTemplates;
	
	@XmlTransient
	private FastMap<Integer, InstanceRiftLocation> instanceRift = new FastMap<Integer, InstanceRiftLocation>();
	
	void afterUnmarshal(Unmarshaller u, Object parent) {
		for (InstanceRiftTemplate template : instanceRiftTemplates) {
			instanceRift.put(template.getId(), new InstanceRiftLocation(template));
		}
	}
	
	public int size() {
		return instanceRift.size();
	}
	
	public FastMap<Integer, InstanceRiftLocation> getInstanceRiftLocations() {
		return instanceRift;
	}
}