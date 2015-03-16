package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class TraditionModel {
	private String id;
	private String name;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
}
