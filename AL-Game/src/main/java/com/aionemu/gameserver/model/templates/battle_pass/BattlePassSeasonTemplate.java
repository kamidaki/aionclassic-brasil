package com.aionemu.gameserver.model.templates.battle_pass;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

import org.joda.time.DateTime;

import com.aionemu.gameserver.utils.gametime.DateTimeUtil;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "battlepass_season")
public class BattlePassSeasonTemplate {

    @XmlAttribute(name = "id", required = true)
    protected int id;
    @XmlAttribute(name = "name")
    protected String name;
    @XmlAttribute(name = "max_level", required = true)
    protected int maxLevel;
    @XmlAttribute(name = "unlock_level_start", required = true)
    protected int unlockLevelStart;
    @XmlAttribute(name = "unlock_pass_cost", required = true)
    protected int unlockPassCost;
    @XmlAttribute(name = "unlock_level_cost", required = true)
    protected int unlockLevelCost;

    @XmlAttribute(name = "start", required = true)
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar startDate;

    @XmlAttribute(name = "end", required = true)
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar endDate;

    public int getId() {
        return id;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getUnlockLevelCost() {
        return unlockLevelCost;
    }

    public int getUnlockLevelStart() {
        return unlockLevelStart;
    }

    public int getUnlockPassCost() {
        return unlockPassCost;
    }

    public String getName() {
        return name;
    }

    public DateTime getStartDate() {
        return DateTimeUtil.getDateTime(startDate.toGregorianCalendar());
    }

    public DateTime getEndDate() {
        return DateTimeUtil.getDateTime(endDate.toGregorianCalendar());
    }
}
