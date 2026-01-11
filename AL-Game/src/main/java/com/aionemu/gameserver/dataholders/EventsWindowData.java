package com.aionemu.gameserver.dataholders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.aionemu.gameserver.model.templates.event.EventsWindowTemplate;

import gnu.trove.map.hash.TIntObjectHashMap;

@XmlRootElement(name = "events_window")
@XmlAccessorType(value = XmlAccessType.FIELD)
public class EventsWindowData {

    @XmlElement(name = "event_window")
    private List<EventsWindowTemplate> events_window;

    @XmlTransient
    private TIntObjectHashMap<EventsWindowTemplate> eventData = new TIntObjectHashMap<EventsWindowTemplate>();

    @XmlTransient
    private Map<Integer, EventsWindowTemplate> eventDataMap = new HashMap<>(1);

    void afterUnmarshal(Unmarshaller unmarshaller, Object object) {
        for (EventsWindowTemplate eventsWindow : events_window) {
            eventData.put(eventsWindow.getId(), eventsWindow);
            eventDataMap.put(eventsWindow.getId(), eventsWindow);
        }
    }

    public int size() {
        return eventData.size();
    }

    public EventsWindowTemplate getEventWindowId(int EventData) {
        return eventData.get(EventData);
    }

    public Map<Integer, EventsWindowTemplate> getAllEvents() {
        return eventDataMap;
    }
}
