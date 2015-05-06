package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
@XmlRootElement
public class ReadingChangePropertyModel {
	
	private String key;
	private String newProperty;	
	
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getNewProperty() {
		return newProperty;
	}
	public void setNewProperty(String newProperty) {
		this.newProperty = newProperty;
	}
}
