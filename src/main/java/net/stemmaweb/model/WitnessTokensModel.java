package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WitnessTokensModel {
    private String witness = null;
    private String layer = null;
    private ArrayList<ReadingModel> tokens = null;

    // public WitnessTokensModel() {};

    public void setWitness(String witness) {this.witness = witness;}
    public String getWitness() {return this.witness;}
    public void setLayer(String layer) {this.layer = layer;}
    public String getLayer() {return this.layer;}
    public Boolean hasLayer() {return this.layer != null;}
    public void setTokens (ArrayList<ReadingModel> tokens) {this.tokens = tokens;}
    public ArrayList<ReadingModel> getTokens() {return this.tokens;}

    public String constructSigil() {
        return layer == null ? witness : String.format("%s (%s)", witness, layer);
    }

    public static String[] parseSigil(String sigil) {
        int t = sigil.indexOf('(');
        String layer = null;
        String witness = sigil;
        if (t > -1) {
            layer = sigil.substring(t + 1, sigil.indexOf(')'));
            witness = sigil.substring(0, t).trim();
        }
        return new String[] {witness, layer};
    }

}
