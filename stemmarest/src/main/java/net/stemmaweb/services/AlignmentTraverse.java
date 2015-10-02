package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by tla on 01/10/15.
 */
public class AlignmentTraverse implements PathExpander {

    private class AlignmentIterable implements Iterable<Relationship> {
        ArrayList<Relationship> relations;

        public AlignmentIterable(ArrayList<Relationship> items) {
            relations = items;
        }
        @Override
        public Iterator<Relationship> iterator() {
            return relations.iterator();
        }
    }

    public Iterable expand(Path path, BranchState state) {
        ArrayList<Relationship> relevantRelations = new ArrayList<Relationship>();
        // Get the sequence relationships
        Iterator<Relationship> sequenceLinks = path.endNode().getRelationships(Direction.OUTGOING, ERelations.SEQUENCE).iterator();
        while (sequenceLinks.hasNext()) {
            relevantRelations.add(sequenceLinks.next());
        }
        // Get the alignment relationships and filter them
        Iterator<Relationship> alignmentLinks = path.endNode().getRelationships(Direction.BOTH, ERelations.RELATED).iterator();
        while (alignmentLinks.hasNext()) {
            Relationship r = alignmentLinks.next();
            if(r.hasProperty("type") &&
                    !r.getProperty("type").equals("transposition") &&
                    !r.getProperty("type").equals("repetition")) {
                relevantRelations.add(r);
            }
        }
        return new AlignmentIterable(relevantRelations);

    }

    public PathExpander reverse() {
        return null;
    }
}
