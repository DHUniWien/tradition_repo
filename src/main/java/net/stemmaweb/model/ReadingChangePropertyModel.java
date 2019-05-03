package net.stemmaweb.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * This model consists of a list of keypropertymodels
 * @author PSE FS 2015 Team2
 */
@XmlRootElement
public class ReadingChangePropertyModel {

    private List<KeyPropertyModel> properties;

    public ReadingChangePropertyModel() { this.properties = new ArrayList<>(); }

    public List<KeyPropertyModel> getProperties() {
        return properties;
    }

    public void setProperties(List<KeyPropertyModel> properties) {
        this.properties = properties;
    }

    public void addProperty(KeyPropertyModel property) { this.properties.add(property); }

}
