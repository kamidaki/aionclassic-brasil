package com.aionemu.gameserver.skillengine.action;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Actions", propOrder = {"actions"})
public class Actions
{
	@XmlElements({
	@XmlElement(name = "itemuse", type = ItemUseAction.class), 
	@XmlElement(name = "mpuse", type = MpUseAction.class),
	@XmlElement(name = "hpuse", type = HpUseAction.class), 
	@XmlElement(name = "dpuse", type = DpUseAction.class),
	@XmlElement(name = "spuse", type = SpUseAction.class)})
	
	protected List<Action> actions;
	
	public List<Action> getActions() {
		if (actions == null) {
			actions = new ArrayList<Action>();
		}
		return this.actions;
	}
}