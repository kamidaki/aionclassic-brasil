package com.aionemu.gameserver.model.templates.npc;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NpcDropGroup")
public class NpcDropGroup {

    @XmlAttribute(name = "ids")
    protected List<Integer> ids;

    public List<Integer> getIds() {
        return ids;
    }
}
