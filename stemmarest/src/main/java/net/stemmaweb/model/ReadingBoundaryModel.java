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
    /**
     * The character / criterion that separates readings / words. Defaults to a single space.
     */
    private String character = " ";
    /**
     * Whether readings should be treated as separate words.
     */
    private Boolean separate = true;
    /**
     * Whether the separation criterion (i.e. {@code character}) is by regular expression.
     */
    private Boolean isRegex = false;

    public String getCharacter() {
        return character;
    }
    public Boolean getSeparate() { return separate; }
    public Boolean getIsRegex() { return isRegex; }

    public void setCharacter(String character) {
        this.character = character;
    }
    public void setSeparate(Boolean separate) { this.separate = separate; }
    public void setIsRegex(Boolean isRegex) { this.isRegex = isRegex; }

}
