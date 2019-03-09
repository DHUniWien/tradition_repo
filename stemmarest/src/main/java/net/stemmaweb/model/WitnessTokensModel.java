package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WitnessTokensModel {
    private String witness;
    private String base;
    private ArrayList<ReadingModel> tokens;

    // public WitnessTokensModel() {};

    public void setWitness(String witness) {this.witness = witness;}
    public String getWitness() {return this.witness;}
    public void setBase(String base) {this.base = base;}
    public String getBase() {return this.base;}
    public Boolean hasBase() {return this.base != null;}
    public void setTokens (ArrayList<ReadingModel> tokens) {this.tokens = tokens;}
    public ArrayList<ReadingModel> getTokens() {return this.tokens;}

}
