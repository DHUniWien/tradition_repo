package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DisplayOptionModel {

    private boolean includeRelated;
    private boolean showNormalForm;
    private boolean showRank;
    private boolean displayAllSigla;
    private boolean showEmendations;
    private String normaliseOn;

    public DisplayOptionModel(Boolean ir, Boolean snf, Boolean sr, Boolean das, Boolean se, String n) {
        includeRelated = ir;
        showNormalForm = snf;
        showRank = sr;
        showEmendations = se;
        displayAllSigla = das;
        normaliseOn = n;
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

    public boolean getShowEmendations() { return showEmendations; }

    public String getNormaliseOn() { return normaliseOn; }
}
