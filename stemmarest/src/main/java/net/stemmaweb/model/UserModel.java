package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class UserModel {
	private String id;
	private String isAdmin;

	public String getIsAdmin() {
		return isAdmin;
	}
	public void setIsAdmin(String isAdmin) {
		this.isAdmin = isAdmin;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
}
