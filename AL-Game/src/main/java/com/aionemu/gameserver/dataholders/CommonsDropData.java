package com.aionemu.gameserver.dataholders;

import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.aionemu.gameserver.model.drop.CommonDropItemTemplate;

import javolution.util.FastMap;

/**
 * @author Krz
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "commons_drop_items")
public class CommonsDropData {

    @XmlElement(name = "commons_drop_item")
    protected List<CommonDropItemTemplate> commonDropItems;


    @XmlTransient
    private FastMap<Integer, CommonDropItemTemplate> templates = new FastMap<Integer, CommonDropItemTemplate>();

    void afterUnmarshal(Unmarshaller u, Object parent) {
        for (CommonDropItemTemplate template : commonDropItems) {
            templates.put(template.getId(), template);
        }
    }

    public int size() {
        return templates.size();
    }

    public CommonDropItemTemplate getTemplate(int id) {
        return templates.get(id);
    }

}
