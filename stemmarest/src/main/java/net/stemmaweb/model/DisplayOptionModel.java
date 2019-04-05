package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DisplayOptionModel {

    private boolean includeRelated;
    private boolean showNormalForm;
    private boolean showRank;
    private String normaliseOn;
    private boolean displayAllSigla;

    public DisplayOptionModel(Boolean ir, Boolean snf, Boolean sr, String n, Boolean das) {
        includeRelated = ir;
        showNormalForm = snf;
        showRank = sr;
        normaliseOn = n;
        displayAllSigla = das;
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

    public String getNormaliseOn() {
        return normaliseOn;
    }

    public boolean getDisplayAllSigla() {
        return displayAllSigla;
    }
}
