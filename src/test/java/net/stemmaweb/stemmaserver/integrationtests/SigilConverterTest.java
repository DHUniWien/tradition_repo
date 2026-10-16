package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.parser.SigilConverter;
import net.stemmaweb.parser.Util;

import java.util.List;

public class SigilConverterTest extends TestCase {

    public void testValidSigilsPassThrough() {
        SigilConverter sc = new SigilConverter();
        for (String s : List.of("A", "Mk10", "_x", "F.m.VII.6", "Ἀθ-1", "Fr31005_2")) {
            assertEquals(s, sc.convert(s));
            assertFalse(sc.wasConverted(s));
        }
        assertTrue(sc.conversions().isEmpty());
    }

    public void testSpacesAndSlashes() {
        SigilConverter sc = new SigilConverter();
        assertEquals("Lewis_MS_E202", sc.convert("Lewis MS E202"));
        assertEquals("F.m.VII.6._Fr31005_2", sc.convert("F.m.VII.6./Fr31005_2"));
        assertEquals("NAF._20023a", sc.convert("NAF. 20023a"));
        assertTrue(sc.wasConverted("NAF. 20023a"));
        assertEquals(3, sc.conversions().size());
        assertEquals("NAF. 20023a", sc.conversions().get("NAF._20023a"));
    }

    public void testConversionIsStable() {
        SigilConverter sc = new SigilConverter();
        assertEquals("Lewis_MS_E202", sc.convert("Lewis MS E202"));
        assertEquals("Lewis_MS_E202", sc.convert("Lewis MS E202"));
        assertEquals(1, sc.conversions().size());
    }

    public void testRunsOfBadCharactersCollapseAndEdgesTrim() {
        SigilConverter sc = new SigilConverter();
        assertEquals("a_b", sc.convert("a  / (b"));
        assertEquals("c", sc.convert(" (c) "));
    }

    public void testBadFirstCharacter() {
        SigilConverter sc = new SigilConverter();
        assertEquals("_12", sc.convert("12"));
        assertEquals("_-x", sc.convert("-x"));
        assertEquals("_.y", sc.convert(".y"));
    }

    public void testNothingSalvageable() {
        SigilConverter sc = new SigilConverter();
        assertEquals("_", sc.convert("///"));
    }

    public void testOutputAlwaysValid() {
        // A fresh converter each time, since e.g. "a b" and "a\tb" are (correctly) a collision
        for (String s : List.of("a b", "1/2", "(x)", "é ü", "a\tb", "###", "x,y", "[A]", "  ")) {
            String c = new SigilConverter().convert(s);
            try {
                Util.validateSigil(c);
            } catch (IllegalArgumentException e) {
                fail("Converted sigil '" + c + "' (from '" + s + "') is invalid: " + e.getMessage());
            }
        }
    }

    public void testTwoSigilsConvertingToSameThingIsAnError() {
        SigilConverter sc = new SigilConverter();
        sc.convert("A B");
        try {
            sc.convert("A/B");
            fail("Expected a collision error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("A B"));
            assertTrue(e.getMessage(), e.getMessage().contains("A/B"));
            assertTrue(e.getMessage(), e.getMessage().contains("A_B"));
        }
    }

    public void testConvertedSigilCollidingWithExistingValidSigilIsAnError() {
        SigilConverter sc = new SigilConverter();
        sc.convert("A_B");
        try {
            sc.convert("A B");
            fail("Expected a collision error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("A B"));
            assertTrue(e.getMessage(), e.getMessage().contains("A_B"));
        }
    }

    public void testValidSigilSeenAfterCollidingConversionIsAnError() {
        SigilConverter sc = new SigilConverter();
        sc.convert("A B");
        try {
            sc.convert("A_B");
            fail("Expected a collision error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("A B"));
            assertTrue(e.getMessage(), e.getMessage().contains("A_B"));
        }
    }
}
