package net.stemmaweb.rest;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ChangePropertyModel {
	
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
