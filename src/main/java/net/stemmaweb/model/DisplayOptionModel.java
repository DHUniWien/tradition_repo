package net.stemmaweb.model;

import java.util.List;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DisplayOptionModel {

    private final boolean includeRelated;
    private final boolean showNormalForm;
    private final boolean rankInLabel;
    private final boolean displayAllSigla;
    private final boolean rankAsAttribute;
    // private boolean showEmendations;
    private final String normaliseOn;
    private final List<String> excludeWitnesses;

    public DisplayOptionModel(Boolean ir, Boolean snf, Boolean sr, Boolean rs, Boolean das, String n, List<String> ew) {
        includeRelated = ir;
        showNormalForm = snf;
        rankInLabel = sr;
        rankAsAttribute = rs;
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

    public boolean getRankInLabel() {
        return rankInLabel;
    }

    public boolean getRankAsAttribute() { return rankAsAttribute; }

    public boolean getDisplayAllSigla() {
        return displayAllSigla;
    }

    // public boolean getShowEmendations() { return showEmendations; }

    public String getNormaliseOn() { return normaliseOn; }

    public List<String> getExcludeWitnesses() { return excludeWitnesses; }
}
