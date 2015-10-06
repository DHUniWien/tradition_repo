package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Provides a custom PathExpansion class that traverses the reading graph in a
 * logical order (taking into account both sequences and alignment relationships.)
 *
 * @author tla
 */
public class AlignmentTraverse implements PathExpander {

    @Override
    public Iterable<Relationship> expand(Path path, BranchState state) {
        return expansion(path, Direction.OUTGOING);
    }

    @Override
    public PathExpander reverse() {
        return new PathExpander() {
            @Override
            public Iterable<Relationship> expand(Path path, BranchState branchState) {
                return expansion(path, Direction.INCOMING);
            }

            @Override
            public PathExpander reverse() {
                return null;
            }
        };
    }

    private Iterable<Relationship> expansion(Path path, Direction dir) {
        ArrayList<Relationship> relevantRelations = new ArrayList<>();
        // Get the sequence relationships
        Iterator<Relationship> sequenceLinks = path.endNode().getRelationships(dir, ERelations.SEQUENCE).iterator();
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
        return relevantRelations;
    }

}
