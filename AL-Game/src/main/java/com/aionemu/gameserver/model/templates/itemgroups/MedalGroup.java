package com.aionemu.gameserver.model.templates.itemgroups;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.model.templates.rewards.MedalItem;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MedalGroup")
public class MedalGroup extends BonusItemGroup {

	@XmlElement(name = "item")
	protected List<MedalItem> items;

	public List<MedalItem> getItems() {
		if (items == null) {
			items = new ArrayList<MedalItem>();
		}
		return items;
	}

	@Override
	public ItemRaceEntry[] getRewards() {
		return (ItemRaceEntry[]) getItems().toArray(new ItemRaceEntry[0]);
	}
}
