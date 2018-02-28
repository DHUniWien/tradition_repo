package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DisplayOptionModel {

    private boolean includeRelated;
    private boolean showNormalForm;
    private boolean normalise;
    private boolean displayAllSigla;

    public DisplayOptionModel(Boolean ir, Boolean snf, Boolean n, Boolean das) {
        includeRelated = ir;
        showNormalForm = snf;
        normalise = n;
        displayAllSigla = das;
    }

    public boolean getIncludeRelated() {
        return includeRelated;
    }

    public boolean getShowNormalForm() {
        return showNormalForm;
    }

    public boolean getNormalise() {
        return normalise;
    }

    public boolean getDisplayAllSigla() {
        return displayAllSigla;
    }
}
