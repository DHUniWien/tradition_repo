package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * This model holds a witness. The id is also the witness name, e.g. 'Mk10'
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class WitnessModel {
    private String id;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
}
