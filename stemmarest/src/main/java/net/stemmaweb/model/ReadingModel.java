package net.stemmaweb.model;

import Exceptions.UrlPathException;

public class ReadingModel {
	
	private String text;
	private String id;
	private String language;
	private String rank;
	private String is_common;
	
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getLanguage() {
		return language;
	}
	public void setLanguage(String language) {
		this.language = language;
	}
	public String getRank() {
		return rank;
	}
	
	public int getRankAsInt(){
		try{
		return Integer.parseInt(rank);	
		}
		catch (NumberFormatException e){
			throw new UrlPathException("the rank was not a number");
		}		
	}
	public void setRank(String string) {
		this.rank = string;
	}
	public String isIs_common() {
		return is_common;
	}
	public void setIs_common(String is_common) {
		this.is_common = is_common;
	}

	

}
