package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.parser.TEIParallelSegParser;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Response;
import java.util.ArrayList;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Test TEI parallel segmentation input.
 *
 * @author tla
 */
public class TEIParallelSegInputTest {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private TEIParallelSegParser importResource;

    @Before
    public void setUp() throws Exception {
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseService.createRootNode(db);
        try(Transaction tx = db.beginTx())
        {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
            tx.success();
        }

        importResource = new TEIParallelSegParser();
        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    @Ignore
    @Test
    public void testTEIPSInput() throws Exception {
        String qText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνῃ ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον καταλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. νείλου τοῦ νύσσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String eText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Ἰσιδώρου Πηλουσίου Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String tText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθι ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνεται φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς μέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἐστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";

        Response result = importResource.parseTEIParallelSeg("src/TestFiles/florilegium_tei_ps.xml", "1", "Auch hier");
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());

        String tradId = Util.getValueFromJson(result, "tradId");
        Tradition tradition = new Tradition(tradId);

        result = tradition.getAllWitnesses();
        @SuppressWarnings("unchecked")
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(13, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "T");
        String witnessText = Util.getValueFromJson(witness.getWitnessAsText(), "text");
        assertEquals(tText, witnessText);

        result = tradition.getAllReadings();
        @SuppressWarnings("unchecked")
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(311, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("βλασφημίας"))
                foundReading = true;
        assertTrue(foundReading);

    }
}
