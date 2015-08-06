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
public class CharacterModel {
    private String character;

    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = character;
    }


}
