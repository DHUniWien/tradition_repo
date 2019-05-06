package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement
public class DisplayOptionModel {

    private boolean includeRelated;
    private boolean showNormalForm;
    private boolean showRank;
    private boolean displayAllSigla;
    // private boolean showEmendations;
    private String normaliseOn;
    private List<String> excludeWitnesses;

    public DisplayOptionModel(Boolean ir, Boolean snf, Boolean sr, Boolean das, String n, List<String> ew) {
        includeRelated = ir;
        showNormalForm = snf;
        showRank = sr;
        // showEmendations = se;
        displayAllSigla = das;
        normaliseOn = n;
        excludeWitnesses = ew;
    }

    public boolean getIncludeRelated() {
        return includeRelated;
    }

    public boolean getShowNormalForm() {
        return showNormalForm;
    }

    public boolean getShowRank() {
        return showRank;
    }

    public boolean getDisplayAllSigla() {
        return displayAllSigla;
    }

    // public boolean getShowEmendations() { return showEmendations; }

    public String getNormaliseOn() { return normaliseOn; }

    public List<String> getExcludeWitnesses() { return excludeWitnesses; }
}
