package net.stemmaweb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;

import net.stemmaweb.rest.Root;

/**
 * Generates an OpenAPI 3 spec (docs/openapi.json) by reflecting over the
 * {@code @Operation}-annotated JAX-RS resource classes, starting from the
 * {@link Root} resource and following its sub-resource locators. Run at
 * build time (see the exec-maven-plugin binding in pom.xml) rather than
 * against a running server, so it needs no deployed application.
 */
public class OpenApiGenerator {

    private static final String DESCRIPTION = """
            Stemmarest is a Neo4j-based repository for variant text traditions (i.e. texts that \
            have been transmitted in multiple manuscripts). For an introduction to its conceptual \
            structure, read on; to dive into specific API calls, see the navigation menu, which is \
            organised by the sort of object each call concerns.

            ### Definition of terms

            What follows is a definition of the concepts that occur within Stemmarest; \
            understanding these terms will be helpful for understanding the API documentation \
            (each is also described again, in the same words, at the top of its own section in the \
            navigation menu). Note that most of these entities are defined with respect to \
            (therefore, under) the **/tradition/{tradId}** heading in the API; readings, although \
            defined as root objects, always belong to one and only one tradition section.
            """;

    // One entry per @Operation "tags" value used across the REST resource classes. Order here
    // controls the order of the sections in the generated navigation menu.
    private static final List<Tag> TAGS = List.of(
            new Tag().name("Tradition").description("""
                    This is the central object in the database. A *tradition* represents the \
                    structure of our information about the text, from its witnesses to its \
                    sections to its readings and relations."""),
            new Tag().name("User").description("""
                    A *user* is an account that can own, create, and manage traditions."""),
            new Tag().name("Witness").description("""
                    A *witness* represents a document that carries some version of the text. \
                    Witnesses generally have *sigla* to identify them, and they can be assembled \
                    into one or more *stemma* hypotheses. The witness sigla are used to identify \
                    individual textual versions within the sections; there, witnesses may also \
                    have *layers* to represent changes made to the text in the document."""),
            new Tag().name("Stemma").description("""
                    A *stemma* (in this context more properly a *stemma hypothesis*) is a graph \
                    that asserts a particular set of copying relationships between *witnesses*. \
                    The witnesses in a stemma can be extant (meaning that their text appears in \
                    the tradition) or hypothetical (meaning that their text does not appear, and \
                    their existence is only conjectured based on the evidence of the copying \
                    history). The stemma objects in the database can have a *root* (or archetype), \
                    or not. The identity of the archetype can be changed by the user."""),
            new Tag().name("Section").description("""
                    The text of a tradition is divided into one or more *sections*, largely for \
                    manageability of the textual data. A section includes the set of *readings* \
                    from each *witness*, usually knitted together into a collation graph. Every \
                    section has a start node and an end node, and every witness (or, more \
                    precisely, every layer of every witness) takes a single path from the start to \
                    the end through a subset of the section's *readings*."""),
            new Tag().name("Reading").description("""
                    A *reading* is the base unit of the text. Most often this is equivalent to a \
                    word, but depending on context and on the needs of the editor, it may be a \
                    partial word, or it may be several words joined together. Readings are the \
                    nodes that are joined together by witness paths (a.k.a. sequences), and can be \
                    correlated by named *relations*. Two or more readings that are *colocated* in \
                    the collation (that is, they occupy the same place in the text, in different \
                    witnesses) are also known as *variants*."""),
            new Tag().name("Relation").description("""
                    It is often useful for an editor to be able to classify the types (or even the \
                    existence) of variation that occurs across witness texts. This is done via the \
                    *relation*, which (depending on its type; see below) usually also signals that \
                    the readings are *colocated*. They can also indicate correspondences such as \
                    *transposition* (when the same reading occurs in different places across \
                    different witnesses)."""),
            new Tag().name("Relation Type").description("""
                    The user can define a set of *relation types* per tradition; these can be given \
                    unique names and arranged in a loose hierarchy."""),
            new Tag().name("Annotation").description("""
                    Arbitrary structured information can be added to any node in the tradition via \
                    the *annotation* framework. An annotation is a node that has user-defined \
                    properties, and user-defined outbound links (i.e. relationships) to specific \
                    sorts of nodes. An annotation framework is defined by means of *annotation \
                    labels* on the tradition."""),
            new Tag().name("Annotation Label").description("""
                    The *annotation label* defines a sort of annotation that may appear on the \
                    tradition, the properties it may contain, and the types of entities (i.e. \
                    nodes) that it may link to.""")
    );

    public static void main(String[] args) throws IOException {
        String outputPath = args.length > 0 ? args[0] : "docs/openapi.json";

        OpenAPI openAPI = new OpenAPI();
        openAPI.info(new Info()
                .title("Stemmarest REST API")
                .description(DESCRIPTION)
                .version("1.1"));
        openAPI.tags(TAGS);

        Reader reader = new Reader(openAPI);
        OpenAPI result = reader.read(Root.class);

        Path output = Path.of(outputPath);
        if (output.getParent() != null)
            Files.createDirectories(output.getParent());
        Files.writeString(output, Json.pretty(result), StandardCharsets.UTF_8);

        System.out.println("Wrote OpenAPI spec to " + output.toAbsolutePath());
    }
}
