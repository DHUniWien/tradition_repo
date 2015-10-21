package net.stemmaweb.stemmaserver;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Class for quick and dirty parsing of tradition GraphML files, to
 * get enough information for a sanity check.
 *
 * @author tla
 */
public class TraditionXMLParser extends DefaultHandler {

    public String traditionName;
    private HashMap<String, String> attrKeys = new HashMap<>();
    private HashMap<String, String> attrFor = new HashMap<>();

    public int numNodes;
    public int numEdges;
    public int numRelationships;
    public int numWitnesses;

    private String currGraph = null;
    private Boolean collectName = false;
    private Boolean collectWitness = false;
    private Boolean skipWitness = false;
    private String foundWitness = null;
    private HashSet<String> uniqueEdges = new HashSet<>();
    private HashSet<String> witnesses = new HashSet<>();

    public void startElement(String uri, String localName,
                             String qName, Attributes attributes) throws SAXException {

        // Record the basic information abotu each graph
        if ("graph".equals(qName)) {
            currGraph = attributes.getValue("id");
            if (currGraph.equals("relationships"))
                numRelationships = Integer.parseInt(attributes.getValue("parse.edges"));
            else
            {
                numNodes = Integer.parseInt(attributes.getValue("parse.nodes"));
            }
        }

        // Record the attribute key mappings in the graphml
        if ("key".equals(qName)) {
            attrKeys.put(attributes.getValue("id"), attributes.getValue("attr.name"));
            attrFor.put(attributes.getValue("id"), attributes.getValue("for"));
        }

        if ("data".equals(qName)) {
            String datakey = attributes.getValue("key");
            if (attrFor.get(datakey).equals("graph")
                    && attrKeys.get(datakey).equals("name")) {
                // Look for name of the tradition
                collectName = true;
            } else if (attrFor.get(datakey).equals("edge")
                    && attrKeys.get(datakey).equals("witness")) {
                // Collect the existence of a witness
                collectWitness = true;

            } else if (attrFor.get(datakey).equals("edge")
                    && attrKeys.get(datakey).equals("witness")) {
                skipWitness = true;
            }

        }

        // Collect the number of sequence edges
        if ("edge".equals(qName) && !currGraph.equals("relationships")) {
            String source = attributes.getValue("source");
            String target = attributes.getValue("target");
            uniqueEdges.add(String.format("%s -> %s", source, target));
        }
    }

    public void endElement(String uri, String localName,
                             String qName) throws SAXException {
        collectName = false;
        collectWitness = false;
        if ("graph".equals(qName) && !currGraph.equals("relationships")) {
            numEdges = uniqueEdges.size();
            numWitnesses = witnesses.size();
        } else if ("edge".equals(qName)) {
            if (foundWitness != null && !skipWitness)
                witnesses.add(foundWitness);
            skipWitness = false;
            foundWitness = null;
        }
    }

    public void characters(char ch[], int start, int length)
            throws SAXException {
        String stringValue = new String(ch, start, length);

        if (collectName)
            traditionName = stringValue;
        else if (collectWitness)
            foundWitness = stringValue;
    }
}
