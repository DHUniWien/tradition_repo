package net.stemmaweb.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class DuplicateModel {
	private List<Long> readings;
	private List<String> witnesses;
	
	public List<Long> getReadings() {
		return readings;
	}

	public void setReadings(List<Long> readings) {
		this.readings = readings;
	}

	public List<String> getWitnesses() {
		return witnesses;
	}

	public void setWitnesses(List<String> witnesses) {
		this.witnesses = witnesses;
	}

}
