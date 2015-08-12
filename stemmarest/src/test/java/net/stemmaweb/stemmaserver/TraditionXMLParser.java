package net.stemmaweb.stemmaserver;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;

/**
 * Class for quick and dirty parsing of tradition GraphML files.
 * Created by tla on 12/08/15.
 */
public class TraditionXMLParser extends DefaultHandler {

    public String traditionName;
    private HashMap<String, String> attrKeys = new HashMap<>();
    private HashMap<String, String> attrFor = new HashMap<>();

    private Boolean foundName = false;

    public void startElement(String uri, String localName,
                             String qName, Attributes attributes) throws SAXException {

        // Record the attribute key mappings in the graphml
        if ("key".equals(qName)) {
            attrKeys.put(attributes.getValue("id"), attributes.getValue("attr.name"));
            attrFor.put(attributes.getValue("id"), attributes.getValue("for"));
        }

        // Look for the information we are after
        if ("data".equals(qName)) {
            String datakey = attributes.getValue("key");
            if (attrFor.get(datakey).equals("graph")) {
                if (attrKeys.get(datakey).equals("name"))
                    foundName = true;
            }
        }
    }

    public void endElement(String uri, String localName,
                             String qName) throws SAXException {
        foundName = false;
    }

    public void characters(char ch[], int start, int length)
            throws SAXException {
        String stringValue = new String(ch, start, length);

        if (foundName) {
            traditionName = stringValue;
        }
    }
}
