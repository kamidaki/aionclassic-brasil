package com.aionemu.gameserver.model.drop;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CommonDropItemTemplate")
public class CommonDropItemTemplate {

    @XmlAttribute(name = "id")
    protected int id;

    @XmlElement(name = "common_item")
    private List<CommonItemTemplate> commonItems;

    public int getId() {
        return id;
    }

    public List<CommonItemTemplate> getCommonItems() {
        return commonItems;
    }
}
