package it.pz8.lsc.plugins.connectors.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.pz8.lsc.plugins.connectors.scim.bean.ScimSelector;

/**
 * @author Giuseppe Amato
 *
 */
class ScimSelectorTest {

    @Test
    void parse_emptyOrNull_yieldsEmptySelector() {
        assertThat(ScimSelector.parse(null).isEmpty()).isTrue();
        assertThat(ScimSelector.parse("").isEmpty()).isTrue();
        assertThat(ScimSelector.parse("   ").isEmpty()).isTrue();
    }

    @Test
    void parse_singleClauseQuoted() {
        ScimSelector s = ScimSelector.parse("type eq \"home\"");
        assertThat(s.get(ScimSelector.TYPE)).isEqualTo("home");
        assertThat(s.has(ScimSelector.PRIMARY)).isFalse();
    }

    @Test
    void parse_singleClauseUnquotedEquivalentToQuoted() {
        assertThat(ScimSelector.parse("type eq home"))
                .isEqualTo(ScimSelector.parse("type eq \"home\""));
    }

    @Test
    void parse_compoundClause_typeAndPrimary() {
        ScimSelector s = ScimSelector.parse("type eq \"home\" and primary eq true");
        assertThat(s.get(ScimSelector.TYPE)).isEqualTo("home");
        assertThat(s.get(ScimSelector.PRIMARY)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void parse_caseInsensitiveOperators() {
        ScimSelector s = ScimSelector.parse("type EQ \"home\" AND primary Eq false");
        assertThat(s.get(ScimSelector.TYPE)).isEqualTo("home");
        assertThat(s.get(ScimSelector.PRIMARY)).isEqualTo(Boolean.FALSE);
    }

    @Test
    void parse_unsupportedAttribute_throws() {
        assertThatThrownBy(() -> ScimSelector.parse("value eq \"x\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void parse_invalidClauseShape_throws() {
        assertThatThrownBy(() -> ScimSelector.parse("type"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_primaryWithNonBoolean_throws() {
        assertThatThrownBy(() -> ScimSelector.parse("primary eq yes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primary");
    }

    @Test
    void toScimFilter_canonicalForm() {
        ScimSelector s = ScimSelector.parse("primary eq true and type eq home");
        // Canonical order: type before primary
        assertThat(s.toScimFilter()).isEqualTo("type eq \"home\" and primary eq true");
    }

    @Test
    void parse_roundTrip_isStable() {
        String canonical = "type eq \"work\" and display eq \"Work\" and primary eq true";
        assertThat(ScimSelector.parse(canonical).toScimFilter()).isEqualTo(canonical);
    }

    @Test
    void fromFlatElement_picksOnlySelectorAttributes() {
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("value", "pippo@acme.com");
        sub.put("type", "home");
        sub.put("primary", Boolean.TRUE);
        sub.put("$ref", "ignored");

        ScimSelector s = ScimSelector.fromFlatElement(sub);
        assertThat(s.toScimFilter()).isEqualTo("type eq \"home\" and primary eq true");
        assertThat(s.has("value")).isFalse();
    }

    @Test
    void fromFlatElement_orderIsCanonical() {
        // Insertion order has primary before type/display: output must still be canonical
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("primary", Boolean.FALSE);
        sub.put("display", "Home");
        sub.put("type", "home");

        ScimSelector s = ScimSelector.fromFlatElement(sub);
        assertThat(s.toScimFilter())
                .isEqualTo("type eq \"home\" and display eq \"Home\" and primary eq false");
    }

    @Test
    void fromFlatElement_onlyValue_isEmpty() {
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("value", "+39421454651");
        assertThat(ScimSelector.fromFlatElement(sub).isEmpty()).isTrue();
    }

    @Test
    void fromFlatElement_primaryAsString_normalizesToBoolean() {
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("type", "home");
        sub.put("primary", "true");
        ScimSelector s = ScimSelector.fromFlatElement(sub);
        assertThat(s.get(ScimSelector.PRIMARY)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void toElementMap_returnsTypedValues() {
        ScimSelector s = ScimSelector.parse("type eq \"work\" and primary eq true");
        Map<String, Object> map = s.toElementMap();
        assertThat(map).containsEntry("type", "work").containsEntry("primary", Boolean.TRUE);
    }

    @Test
    void toElementMap_isDefensiveCopy() {
        ScimSelector s = ScimSelector.parse("type eq home");
        Map<String, Object> map = s.toElementMap();
        map.put("primary", true);
        assertThat(s.has(ScimSelector.PRIMARY)).isFalse();
    }

    @Test
    void extractBody_simpleAndCompound() {
        assertThat(ScimSelector.extractBody("emails")).isNull();
        assertThat(ScimSelector.extractBody("emails[]")).isEqualTo("");
        assertThat(ScimSelector.extractBody("emails[type eq home]")).isEqualTo("type eq home");
        assertThat(ScimSelector.extractBody("emails[type eq \"home\" and primary eq true]"))
                .isEqualTo("type eq \"home\" and primary eq true");
    }

    @Test
    void emptySelector_filterIsEmpty() {
        assertThat(ScimSelector.empty().toScimFilter()).isEmpty();
        assertThat(ScimSelector.empty().toElementMap()).isEmpty();
    }
}
