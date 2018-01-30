package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Provides a model for single character that can be parsed into JSON
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class ReadingBoundaryModel {
    private String character = " ";
    private Boolean separate = true;

    public String getCharacter() {
        return character;
    }
    public Boolean getSeparate() { return separate; }

    public void setCharacter(String character) {
        this.character = character;
    }
    public void setSeparate(Boolean separate) { this.separate = separate; }

}
