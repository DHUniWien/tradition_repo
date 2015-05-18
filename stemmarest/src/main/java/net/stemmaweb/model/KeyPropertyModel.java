package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * This model consists a node property key and value
 * @author PSE FS 2015 Team2
 */
@XmlRootElement
public class KeyPropertyModel {
	private String key;
	private String property;
	
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getProperty() {
		return property;
	}
	public void setProperty(String property) {
		this.property = property;
	}
	
	
}