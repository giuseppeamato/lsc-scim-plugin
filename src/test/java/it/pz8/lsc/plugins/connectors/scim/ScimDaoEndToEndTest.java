package it.pz8.lsc.plugins.connectors.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.ADD_VALUES;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.DELETE_VALUES;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.REPLACE_VALUES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lsc.LscDatasetModification;
import org.lsc.LscDatasets;
import org.lsc.LscModificationType;
import org.lsc.LscModifications;
import org.lsc.beans.IBean;
import org.lsc.beans.SimpleBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.ValuesType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * End-to-end tests covering the SCIM plugin against an embedded HTTP server. The mock
 * captures every inbound request body so the assertions can verify the EXACT wire
 * payload produced by ScimDao. This exercises the full path
 * (ScimDao → Jersey HTTP client → mock server) without depending on a live SCIM provider.
 */
class ScimDaoEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private CapturingHandler usersRoot;
    private CapturingHandler userById;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        usersRoot = new CapturingHandler();
        userById = new CapturingHandler();
        server.createContext("/scim2/Users", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/scim2/Users") || path.equals("/scim2/Users/")) {
                usersRoot.handle(exchange);
            } else {
                userById.handle(exchange);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ScimDao newDao(List<String> writableAttrs) throws Exception {
        PluginConnectionType conn = mock(PluginConnectionType.class);
        when(conn.getUrl()).thenReturn("http://127.0.0.1:" + port + "/scim2");
        when(conn.getUsername()).thenReturn("u");
        when(conn.getPassword()).thenReturn("p");
        ScimServiceSettings settings = mock(ScimServiceSettings.class);
        when(settings.getEntity()).thenReturn(ScimDao.USERS);
        when(settings.getPivot()).thenReturn(null); // defaults to "id" — bypasses pivot lookup
        when(settings.getCacheConnection()).thenReturn(null);
        if (writableAttrs != null) {
            ValuesType vt = mock(ValuesType.class);
            when(vt.getString()).thenReturn(writableAttrs);
            when(settings.getWritableAttributes()).thenReturn(vt);
        }
        return new ScimDao(conn, settings);
    }

    /** End-to-end: ADD_VALUES with selector + matching dst element produces a wire-level
     *  PATCH that is an SCIM Add (NOT Replace). Direct verification of the JSON sent over HTTP. */
    @Test
    void e2e_addValuesWithSelector_existingMatch_sendsScimAddOverWire() throws Exception {
        ScimDao dao = newDao(List.of("emails[type eq \"work\"]"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippoadd");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> existing = new LinkedHashSet<>();
        existing.add("first@acme.com");
        ds.put("emails[type eq \"work\"]", existing);
        ds.put("id", "abc-123");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("abc-123");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(ADD_VALUES, "emails[type eq \"work\"]", List.of("second@acme.com"))));

        boolean ok = dao.update(lm);

        assertThat(ok).isTrue();
        assertThat(userById.requests).hasSize(1);
        CapturedRequest req = userById.requests.get(0);
        assertThat(req.method).isEqualTo("PATCH");
        assertThat(req.path).isEqualTo("/scim2/Users/abc-123");

        JsonNode body = MAPPER.readTree(req.body);
        JsonNode ops = body.get("Operations");
        assertThat(ops).hasSize(1);
        JsonNode op0 = ops.get(0);
        assertThat(op0.get("op").asText())
                .as("ADD_VALUES with selector must produce 'add', not 'replace'")
                .isEqualTo("add");
        assertThat(op0.get("path").asText()).isEqualTo("emails");
        JsonNode value = op0.get("value");
        assertThat(value.isArray()).isTrue();
        assertThat(value).hasSize(1);
        assertThat(value.get(0).get("type").asText()).isEqualTo("work");
        assertThat(value.get(0).get("value").asText()).isEqualTo("second@acme.com");
    }

    /** End-to-end: REPLACE_VALUES with selector + matching dst + N values produces
     *  a Remove + Add pair on the wire. */
    @Test
    void e2e_replaceMultipleValuesWithSelector_existingMatch_sendsRemovePlusAdd() throws Exception {
        ScimDao dao = newDao(List.of("phoneNumbers[type eq work]"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> existing = new LinkedHashSet<>();
        existing.add("+39111");
        ds.put("phoneNumbers[type eq \"work\"]", existing);
        ds.put("id", "uid-1");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-1");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(REPLACE_VALUES, "phoneNumbers[type eq work]",
                        List.of("+39222", "+39333", "+39444"))));

        assertThat(dao.update(lm)).isTrue();
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode ops = body.get("Operations");
        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).get("op").asText()).isEqualTo("remove");
        assertThat(ops.get(0).get("path").asText()).isEqualTo("phoneNumbers[type eq \"work\"]");
        assertThat(ops.get(1).get("op").asText()).isEqualTo("add");
        assertThat(ops.get(1).get("path").asText()).isEqualTo("phoneNumbers");
        assertThat(ops.get(1).get("value")).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(ops.get(1).get("value").get(i).get("type").asText()).isEqualTo("work");
        }
    }

    /** Regression for the 2→1 phoneNumbers bug: when LSC's BeanComparator turns a
     *  delete-one-of-two diff into a single REPLACE_VALUES (because
     *  {@code missing+extra >= toSet.size()}) and the dst exposes TWO elements matching
     *  the same selector, the plugin must NOT take either fast path:
     *   1. NOT a {@code Replace} on .value — would rewrite every matching record (RFC 7644 §3.5.2.3).
     *   2. NOT a wholesale {@code Remove canonical + Add} — Keycloak fails to evict every
     *      match before the add runs, leaving the surviving value duplicated.
     *  Expected wire: a single PRECISE Remove targeting the surplus value
     *  ({@code phoneNumbers[type eq "mobile" and value eq "+39222"]}); no Add is sent
     *  because the survivor (+39111) is already present in dst. */
    @Test
    void e2e_replaceSingleValueWithSelector_multipleDstMatches_sendsPreciseRemoveOnly() throws Exception {
        ScimDao dao = newDao(List.of("phoneNumbers[type eq mobile]"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> existing = new LinkedHashSet<>();
        existing.add("+39111");
        existing.add("+39222");
        ds.put("phoneNumbers[type eq \"mobile\"]", existing);
        ds.put("id", "uid-21");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-21");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(REPLACE_VALUES, "phoneNumbers[type eq mobile]",
                        List.of("+39111"))));

        assertThat(dao.update(lm)).isTrue();
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode ops = body.get("Operations");
        assertThat(ops)
                .as("Must emit a single precise Remove for the surplus value, no Add")
                .hasSize(1);
        assertThat(ops.get(0).get("op").asText()).isEqualTo("remove");
        assertThat(ops.get(0).get("path").asText())
                .isEqualTo("phoneNumbers[type eq \"mobile\" and value eq \"+39222\"]");
        assertThat(ops.get(0).has("value")).isFalse();
    }

    /** Multi-match REPLACE where every dst value is being swapped out: dst={+39111, +39222}
     *  and new={+39333}. Element-level diff yields TWO precise removes (one per surplus
     *  value) plus a single Add carrying the net-new element. */
    @Test
    void e2e_replaceWithSelector_multipleDstMatches_swapAllValues_emitsPreciseRemovesPlusAdd() throws Exception {
        ScimDao dao = newDao(List.of("phoneNumbers[type eq mobile]"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> existing = new LinkedHashSet<>();
        existing.add("+39111");
        existing.add("+39222");
        ds.put("phoneNumbers[type eq \"mobile\"]", existing);
        ds.put("id", "uid-22");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-22");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(REPLACE_VALUES, "phoneNumbers[type eq mobile]",
                        List.of("+39333"))));

        assertThat(dao.update(lm)).isTrue();
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode ops = body.get("Operations");
        assertThat(ops).hasSize(3);

        // Two precise removes for +39111 and +39222 (order follows dst-iteration order).
        assertThat(ops.get(0).get("op").asText()).isEqualTo("remove");
        assertThat(ops.get(0).get("path").asText())
                .isEqualTo("phoneNumbers[type eq \"mobile\" and value eq \"+39111\"]");
        assertThat(ops.get(1).get("op").asText()).isEqualTo("remove");
        assertThat(ops.get(1).get("path").asText())
                .isEqualTo("phoneNumbers[type eq \"mobile\" and value eq \"+39222\"]");

        // Single Add for the net-new value.
        assertThat(ops.get(2).get("op").asText()).isEqualTo("add");
        assertThat(ops.get(2).get("path").asText()).isEqualTo("phoneNumbers");
        JsonNode added = ops.get(2).get("value");
        assertThat(added).hasSize(1);
        assertThat(added.get(0).get("type").asText()).isEqualTo("mobile");
        assertThat(added.get(0).get("value").asText()).isEqualTo("+39333");
    }

    /** End-to-end: REPLACE_VALUES with selector + single value + matching dst still
     *  emits a single Replace on .value (regression: must not break the simple case). */
    @Test
    void e2e_replaceSingleValueWithSelector_existingMatch_sendsScimReplace() throws Exception {
        ScimDao dao = newDao(List.of("emails[type eq \"work\"]"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> existing = new LinkedHashSet<>();
        existing.add("old@acme.com");
        ds.put("emails[type eq \"work\"]", existing);
        ds.put("id", "uid-2");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-2");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(REPLACE_VALUES, "emails[type eq \"work\"]", List.of("new@acme.com"))));

        assertThat(dao.update(lm)).isTrue();
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode ops = body.get("Operations");
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).get("op").asText()).isEqualTo("replace");
        assertThat(ops.get(0).get("path").asText()).isEqualTo("emails[type eq \"work\"].value");
        assertThat(ops.get(0).get("value").asText()).isEqualTo("new@acme.com");
    }

    /** End-to-end: DELETE_VALUES on a compound selector with `primary eq true` emits a
     *  single Remove on the canonical selector path. Reproducer for the user's report
     *  that deletion of a primary-selector attribute wasn't being propagated. */
    @Test
    void e2e_deleteValuesWithPrimarySelector_sendsRemoveOnCanonicalPath() throws Exception {
        ScimDao dao = newDao(List.of("emails[type eq \"work\" and primary eq true]"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> existing = new LinkedHashSet<>();
        existing.add("user@acme.com");
        ds.put("emails[type eq \"work\" and primary eq true]", existing);
        ds.put("id", "uid-3");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-3");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(DELETE_VALUES,
                        "emails[type eq \"work\" and primary eq true]",
                        new ArrayList<>())));

        assertThat(dao.update(lm)).isTrue();
        assertThat(userById.requests).hasSize(1);
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode ops = body.get("Operations");
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).get("op").asText()).isEqualTo("remove");
        assertThat(ops.get(0).get("path").asText())
                .isEqualTo("emails[type eq \"work\" and primary eq true]");
        assertThat(ops.get(0).has("value")).isFalse();
    }

    /** End-to-end: re-fetching a SCIM resource that contains an email with primary=true
     *  produces a bean key that BeanComparator can find using the writableAttribute string
     *  (canonical or non-canonical user form). Without this alias, BeanComparator's lookup
     *  of dstBean.getDatasetById misses, dstAttrValues is empty, the toSet=empty AND
     *  current=empty branch is UNKNOWN, and DELETE_VALUES is never emitted. */
    @Test
    void e2e_flattenScimResponseWithPrimaryTrue_aliasesUserFormForLookup() throws Exception {
        ScimDao dao = newDao(List.of("emails[type eq \"work\" and primary eq true]"));
        // SCIM response with primary explicitly true
        usersRoot.respondWith(200,
                "{\"totalResults\":1,\"Resources\":[{\"id\":\"uid-4\",\"userName\":\"pippo\","
                + "\"emails\":[{\"type\":\"work\",\"primary\":true,\"value\":\"user@acme.com\"}]}]}");

        java.util.Map<String, Object> details = dao.getDetailsByPivot("pippo", null);
        // The bean must expose the value under the writableAttribute string LSC will use
        // when computing diffs against the source.
        assertThat(details).containsKey("emails[type eq \"work\" and primary eq true]");
        assertThat(details.get("emails[type eq \"work\" and primary eq true]"))
                .isEqualTo("user@acme.com");
    }

    /** Documented behavior: the bean key {@code emails[]} contains only values from
     *  elements without a {@code type/display/primary} sub-attribute. Typed elements live
     *  exclusively under their selector key. Mixing {@code emails[]} and {@code emails[type eq X]}
     *  in writableAttributes only works correctly with SCIM providers that preserve the
     *  typed structure on response — see complex-multivalued-selectors.md "Limiti". */
    @Test
    void e2e_flatten_typedElement_isolatedFromBaseArrayKey() throws Exception {
        ScimDao dao = newDao(List.of("emails[]", "emails[type eq \"work\"]"));
        usersRoot.respondWith(200,
                "{\"totalResults\":1,\"Resources\":[{\"id\":\"uid-x\",\"userName\":\"pippo\","
                + "\"emails\":[{\"type\":\"work\",\"value\":\"work@acme.com\"}]}]}");

        java.util.Map<String, Object> details = dao.getDetailsByPivot("pippo", null);

        assertThat(details).containsKey("emails[type eq \"work\"]");
        assertThat(details.get("emails[type eq \"work\"]")).isEqualTo("work@acme.com");
        assertThat(details).doesNotContainKey("emails[]");
    }

    /** End-to-end: when the SCIM provider does NOT explicitly return the `primary` field
     *  (some providers omit it), but the writableAttribute requires `primary eq true`,
     *  the bean key emitted by the plugin is `emails[type eq "work"]` (without primary),
     *  and lookup by `emails[type eq "work" and primary eq true]` MISSES.
     *
     *  This test documents that mismatch — if it fails it means we changed semantics. */
    @Test
    void e2e_flattenScimResponseWithoutPrimaryField_doesNotAliasPrimaryClause() throws Exception {
        ScimDao dao = newDao(List.of("emails[type eq \"work\" and primary eq true]"));
        // SCIM response WITHOUT a `primary` field on the email
        usersRoot.respondWith(200,
                "{\"totalResults\":1,\"Resources\":[{\"id\":\"uid-5\",\"userName\":\"pippo\","
                + "\"emails\":[{\"type\":\"work\",\"value\":\"user@acme.com\"}]}]}");

        java.util.Map<String, Object> details = dao.getDetailsByPivot("pippo", null);
        // Bean only knows the type clause — primary is not asserted because data didn't say so
        assertThat(details).containsKey("emails[type eq \"work\"]");
        assertThat(details).doesNotContainKey("emails[type eq \"work\" and primary eq true]");
    }

    /** Variant A — read side: SCIM response with structured sub-fields under a typed
     *  multivalued attribute (e.g. addresses) exposes one bean key per non-selector
     *  sub-attribute, so {@code addresses[type eq "work"].streetAddress} resolves. */
    @Test
    void e2e_flattenAddressWithSubFields_emitsPerSubFieldKeys() throws Exception {
        ScimDao dao = newDao(List.of("addresses[type eq \"work\"].streetAddress",
                "addresses[type eq \"work\"].locality"));
        usersRoot.respondWith(200,
                "{\"totalResults\":1,\"Resources\":[{\"id\":\"uid-a\",\"userName\":\"pippo\","
                + "\"addresses\":[{\"type\":\"work\",\"streetAddress\":\"Via Roma 1\","
                + "\"locality\":\"Milano\",\"postalCode\":\"20100\"}]}]}");

        java.util.Map<String, Object> details = dao.getDetailsByPivot("pippo", null);

        assertThat(details).containsKey("addresses[type eq \"work\"].streetAddress");
        assertThat(details.get("addresses[type eq \"work\"].streetAddress")).isEqualTo("Via Roma 1");
        assertThat(details.get("addresses[type eq \"work\"].locality")).isEqualTo("Milano");
        // Sub-fields not in writableAttributes are still emitted (they're free-form data fields).
        assertThat(details.get("addresses[type eq \"work\"].postalCode")).isEqualTo("20100");
        // No spurious bare key (no `value` field in the SCIM element).
        assertThat(details).doesNotContainKey("addresses[type eq \"work\"]");
    }

    /** Variant A — read side: legacy {@code emails[type eq "work"]} (no sub-field path)
     *  remains exposed under the bare canonical key for backward compatibility. */
    @Test
    void e2e_flattenEmailValueField_keepsBareCanonicalKey() throws Exception {
        ScimDao dao = newDao(List.of("emails[type eq \"work\"]"));
        usersRoot.respondWith(200,
                "{\"totalResults\":1,\"Resources\":[{\"id\":\"uid-b\",\"userName\":\"pippo\","
                + "\"emails\":[{\"type\":\"work\",\"value\":\"user@acme.com\"}]}]}");

        java.util.Map<String, Object> details = dao.getDetailsByPivot("pippo", null);

        // Both forms resolve to the same value.
        assertThat(details.get("emails[type eq \"work\"]")).isEqualTo("user@acme.com");
        assertThat(details.get("emails[type eq \"work\"].value")).isEqualTo("user@acme.com");
    }

    /** Variant A — write/CREATE side: two datasets that target sub-fields of the same typed
     *  selector merge into ONE element with both fields. */
    @Test
    void e2e_createWithMultipleSubFieldsForSameSelector_buildsSingleElement() throws Exception {
        ScimDao dao = newDao(List.of("addresses[type eq \"work\"].streetAddress",
                "addresses[type eq \"work\"].locality"));

        usersRoot.respondWith(201, "{\"id\":\"new-uid\",\"userName\":\"pippo\"}");

        LscModifications lm = new LscModifications(LscModificationType.CREATE_OBJECT);
        lm.setMainIdentifer("pippo");
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(ADD_VALUES, "userName", List.of("pippo")),
                new LscDatasetModification(ADD_VALUES, "addresses[type eq \"work\"].streetAddress",
                        List.of("Via Roma 1")),
                new LscDatasetModification(ADD_VALUES, "addresses[type eq \"work\"].locality",
                        List.of("Milano"))));

        assertThat(dao.create(lm)).isTrue();
        JsonNode body = MAPPER.readTree(usersRoot.requests.get(0).body);
        JsonNode addrs = body.get("addresses");
        assertThat(addrs).hasSize(1);
        JsonNode addr = addrs.get(0);
        assertThat(addr.get("type").asText()).isEqualTo("work");
        assertThat(addr.get("streetAddress").asText()).isEqualTo("Via Roma 1");
        assertThat(addr.get("locality").asText()).isEqualTo("Milano");
    }

    /** Variant A — UPDATE side: REPLACE on a sub-field path of a matching destination
     *  element produces a SCIM Replace on canonicalKey.subField (NOT the hardcoded .value). */
    @Test
    void e2e_replaceSubFieldWithExistingMatch_sendsReplaceOnSubFieldPath() throws Exception {
        ScimDao dao = newDao(List.of("addresses[type eq \"work\"].streetAddress"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> existing = new LinkedHashSet<>();
        existing.add("Via Vecchia 99");
        ds.put("addresses[type eq \"work\"].streetAddress", existing);
        ds.put("id", "uid-c");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-c");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(REPLACE_VALUES,
                        "addresses[type eq \"work\"].streetAddress",
                        List.of("Via Roma 1"))));

        assertThat(dao.update(lm)).isTrue();
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode op0 = body.get("Operations").get(0);
        assertThat(op0.get("op").asText()).isEqualTo("replace");
        assertThat(op0.get("path").asText())
                .isEqualTo("addresses[type eq \"work\"].streetAddress");
        assertThat(op0.get("value").asText()).isEqualTo("Via Roma 1");
    }

    /** Reproducer for the user's "values cross-pollute between sub-fields" report:
     *  with both addresses[type eq work].streetAddress AND .locality datasets, two
     *  separate REPLACE diffs must produce two separate PATCH ops, each targeting
     *  exclusively its own sub-field path. */
    @Test
    void e2e_replaceTwoSubFieldsForSameSelector_eachOpTargetsItsSubField() throws Exception {
        ScimDao dao = newDao(List.of("addresses[type eq work].streetAddress",
                "addresses[type eq work].locality"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        LinkedHashSet<Object> oldStreet = new LinkedHashSet<>();
        oldStreet.add("Old Street");
        ds.put("addresses[type eq \"work\"].streetAddress", oldStreet);
        LinkedHashSet<Object> oldCity = new LinkedHashSet<>();
        oldCity.add("Old City");
        ds.put("addresses[type eq \"work\"].locality", oldCity);
        ds.put("id", "uid-multi");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-multi");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(REPLACE_VALUES,
                        "addresses[type eq work].streetAddress", List.of("Via Roma 1")),
                new LscDatasetModification(REPLACE_VALUES,
                        "addresses[type eq work].locality", List.of("Milano"))));

        assertThat(dao.update(lm)).isTrue();
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode ops = body.get("Operations");
        assertThat(ops).hasSize(2);

        // First op must be the streetAddress replace (path AND value match exclusively).
        JsonNode op0 = ops.get(0);
        assertThat(op0.get("op").asText()).isEqualTo("replace");
        assertThat(op0.get("path").asText())
                .isEqualTo("addresses[type eq \"work\"].streetAddress");
        assertThat(op0.get("value").asText()).isEqualTo("Via Roma 1");

        // Second op must be the locality replace — independent path, independent value.
        JsonNode op1 = ops.get(1);
        assertThat(op1.get("op").asText()).isEqualTo("replace");
        assertThat(op1.get("path").asText())
                .isEqualTo("addresses[type eq \"work\"].locality");
        assertThat(op1.get("value").asText()).isEqualTo("Milano");
    }

    /** Variant A — UPDATE side: ADD on a sub-field path with no matching destination
     *  element creates a new element with that sub-field set (NOT a {@code value} field). */
    @Test
    void e2e_addSubFieldNoMatch_emitsAddElementWithCorrectSubField() throws Exception {
        ScimDao dao = newDao(List.of("addresses[type eq \"work\"].streetAddress"));

        userById.respondWith(204, "");

        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        ds.put("id", "uid-d");
        dstBean.setDatasets(ds);

        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setMainIdentifer("uid-d");
        lm.setDestinationBean(dstBean);
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(ADD_VALUES,
                        "addresses[type eq \"work\"].streetAddress",
                        List.of("Via Roma 1"))));

        assertThat(dao.update(lm)).isTrue();
        JsonNode body = MAPPER.readTree(userById.requests.get(0).body);
        JsonNode op0 = body.get("Operations").get(0);
        assertThat(op0.get("op").asText()).isEqualTo("add");
        assertThat(op0.get("path").asText()).isEqualTo("addresses");
        JsonNode added = op0.get("value").get(0);
        assertThat(added.get("type").asText()).isEqualTo("work");
        assertThat(added.get("streetAddress").asText()).isEqualTo("Via Roma 1");
        assertThat(added.has("value")).isFalse();
    }

    /** End-to-end: CREATE with multi-value selector emits N elements in the POST body. */
    @Test
    void e2e_createWithMultiValueSelector_postsAllElements() throws Exception {
        ScimDao dao = newDao(List.of("phoneNumbers[type eq \"work\"]"));

        usersRoot.respondWith(201, "{\"id\":\"new-uid\",\"userName\":\"pippo\"}");

        LscModifications lm = new LscModifications(LscModificationType.CREATE_OBJECT);
        lm.setMainIdentifer("pippo");
        lm.setLscAttributeModifications(List.of(
                new LscDatasetModification(ADD_VALUES, "userName", List.of("pippo")),
                new LscDatasetModification(ADD_VALUES, "phoneNumbers[type eq \"work\"]",
                        List.of("+39111", "+39222"))));

        assertThat(dao.create(lm)).isTrue();
        CapturedRequest req = usersRoot.requests.get(0);
        assertThat(req.method).isEqualTo("POST");
        JsonNode body = MAPPER.readTree(req.body);
        JsonNode phones = body.get("phoneNumbers");
        assertThat(phones).hasSize(2);
        assertThat(phones.get(0).get("type").asText()).isEqualTo("work");
        assertThat(phones.get(0).get("value").asText()).isEqualTo("+39111");
        assertThat(phones.get(1).get("type").asText()).isEqualTo("work");
        assertThat(phones.get(1).get("value").asText()).isEqualTo("+39222");
    }

    // ---------- mock server helpers ----------

    private static final class CapturedRequest {
        final String method;
        final String path;
        final String body;

        CapturedRequest(String method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }
    }

    private static final class CapturingHandler implements HttpHandler {
        final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
        private int status = 200;
        private String responseBody = "";

        void respondWith(int status, String body) {
            this.status = status;
            this.responseBody = body;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            byte[] body;
            try (InputStream is = ex.getRequestBody()) {
                body = is.readAllBytes();
            }
            requests.add(new CapturedRequest(ex.getRequestMethod(),
                    ex.getRequestURI().getPath(), new String(body, StandardCharsets.UTF_8)));
            byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, resp.length == 0 ? -1 : resp.length);
            if (resp.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(resp);
                }
            }
        }
    }
}
