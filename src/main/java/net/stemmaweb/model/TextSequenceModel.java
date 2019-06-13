package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class TextSequenceModel {
    private String text;

    public TextSequenceModel(String theText) {
        this.text = theText;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
