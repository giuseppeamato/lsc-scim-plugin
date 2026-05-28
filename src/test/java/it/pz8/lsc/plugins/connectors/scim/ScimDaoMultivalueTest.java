package it.pz8.lsc.plugins.connectors.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.ADD_VALUES;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.DELETE_VALUES;
import static org.lsc.LscDatasetModification.LscDatasetModificationType.REPLACE_VALUES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.lsc.LscDatasetModification;
import org.lsc.LscDatasets;
import org.lsc.LscModificationType;
import org.lsc.LscModifications;
import org.lsc.beans.IBean;
import org.lsc.beans.SimpleBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.ValuesType;

import it.pz8.lsc.plugins.connectors.scim.bean.OperationType;
import it.pz8.lsc.plugins.connectors.scim.bean.ScimPathOperation;
import it.pz8.lsc.plugins.connectors.scim.generated.FlatMultivalueStrategyType;
import it.pz8.lsc.plugins.connectors.scim.generated.ScimServiceSettings;

/**
 * Unit tests for {@code ScimDao}'s multivalue PATCH generation and JSON flattening, exercised in
 * isolation (no HTTP, no DB) by invoking {@code buildPatchOperations} and {@code flatten}
 * reflectively. Organized by the kind of attribute path:
 *
 * <ul>
 *   <li><b>Compound selector paths</b> ({@code attr[type eq "work"]}): a single matching dst
 *       element yields a {@code replace} on the sub-field; multiple or no matches yield a
 *       {@code remove}+{@code add} pair (never a single {@code replace} that would drop the
 *       peer values); {@code ADD_VALUES} always adds a new element rather than replacing; and
 *       {@code DELETE_VALUES} removes either the whole selector (no values) or each value via a
 *       precise {@code selector and subField eq "X"} filter, with RFC 7644 §3.4.2.2 escaping.</li>
 *   <li><b>Flat multivalue paths</b> ({@code attr[]}, e.g. {@code members[]}): under the default
 *       {@code ELEMENT_DIFF} strategy a {@code REPLACE} is reshaped into per-value {@code remove}
 *       of the surplus dst values plus one aggregated {@code add} of the net-new ones (so Keycloak
 *       does not reject a wholesale replace on {@code group.members} with HTTP 501), while
 *       {@code ADD_VALUES} stays a plain {@code add}; under {@code WHOLESALE_REPLACE} every diff
 *       collapses into a single {@code replace} carrying the final list (required by WSO2
 *       Asgardeo/IS on extension flat paths).</li>
 *   <li><b>Flattening / normalization</b> ({@code flatten}): each SCIM element is exposed under its
 *       canonical selector key and, when {@code writableAttributes} declares a different
 *       (non-canonical) form, also under that user form — so LSC's BeanComparator resolves either
 *       spelling regardless of clause order or quoting; duplicate selectors accumulate into a
 *       value list.</li>
 * </ul>
 */
class ScimDaoMultivalueTest {

    private static final String CANONICAL = "phoneNumbers[type eq \"work\"]";
    private static final String USER_FORM = "phoneNumbers[type eq work]";

    private ScimDao newDao(List<String> writableAttrs) throws Exception {
        return newDao(writableAttrs, null);
    }

    private ScimDao newDao(List<String> writableAttrs, FlatMultivalueStrategyType strategy) throws Exception {
        PluginConnectionType conn = mock(PluginConnectionType.class);
        when(conn.getUrl()).thenReturn("http://localhost:0/scim2");
        when(conn.getUsername()).thenReturn("u");
        when(conn.getPassword()).thenReturn("p");
        ScimServiceSettings settings = mock(ScimServiceSettings.class);
        when(settings.getEntity()).thenReturn(ScimDao.USERS);
        when(settings.getPivot()).thenReturn("userName");
        when(settings.getCacheConnection()).thenReturn(null);
        ValuesType vt = mock(ValuesType.class);
        when(vt.getString()).thenReturn(writableAttrs);
        when(settings.getWritableAttributes()).thenReturn(vt);
        when(settings.getFlatMultivalueStrategy()).thenReturn(strategy);
        return new ScimDao(conn, settings);
    }

    private static IBean beanWith(String key, Object value) {
        IBean bean = new SimpleBean();
        bean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        ds.put(key, value);
        bean.setDatasets(ds);
        return bean;
    }

    @SuppressWarnings("unchecked")
    private static List<ScimPathOperation> invokeBuildPatchOperations(ScimDao dao, String op,
            LscDatasetModification diff, LscModifications lm) throws Exception {
        Method m = ScimDao.class.getDeclaredMethod("buildPatchOperations", String.class,
                LscDatasetModification.class, LscModifications.class);
        m.setAccessible(true);
        return (List<ScimPathOperation>) m.invoke(dao, op, diff, lm);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeFlatten(ScimDao dao, String json) throws Exception {
        Method m = ScimDao.class.getDeclaredMethod("flatten", String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(dao, json);
    }

    /** Problem 1: 3 source values + matching dst element ⇒ REMOVE + ADD with 3 elements. */
    @Test
    void multipleValuesWithSelector_existingMatch_emitsRemovePlusAdd() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "+39111");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, USER_FORM,
                List.of("+39111", "+39222", "+39333"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REMOVE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo(CANONICAL);
        assertThat(ops.get(0).getValue()).isNull();

        assertThat(ops.get(1).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(1).getPath()).isEqualTo("phoneNumbers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) ops.get(1).getValue();
        assertThat(elements).hasSize(3);
        assertThat(elements).allSatisfy(el -> assertThat(el).containsEntry("type", "work"));
        assertThat(elements).extracting(el -> el.get("value"))
                .containsExactly("+39111", "+39222", "+39333");
    }

    /** Problem 1 (no existing): N source values + no dst match ⇒ single ADD with N elements. */
    @Test
    void multipleValuesWithSelector_noMatch_emitsAddOnly() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith("userName", "pippo");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, USER_FORM,
                List.of("+39111", "+39222"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("phoneNumbers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) ops.get(0).getValue();
        assertThat(elements).hasSize(2);
    }

    /** Single source value + matching dst element ⇒ REPLACE on .value (regression: keep current behavior). */
    @Test
    void singleValueWithSelector_existingMatch_emitsReplaceOnValue() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "+39111");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, USER_FORM, List.of("+39222"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REPLACE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo(CANONICAL + ".value");
        assertThat(ops.get(0).getValue()).isEqualTo("+39222");
    }

    /** ADD_VALUES with selector + matching dst element ⇒ ADD a new element, NOT REPLACE on .value
     *  (that would overwrite the existing matching elements). */
    @Test
    void addValuesWithSelector_existingMatch_emitsAddNotReplace() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "+39111");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(ADD_VALUES, USER_FORM, List.of("+39222"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.ADD.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("phoneNumbers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) ops.get(0).getValue();
        assertThat(elements).hasSize(1);
        assertThat(elements.get(0)).containsEntry("type", "work").containsEntry("value", "+39222");
    }

    /** ADD_VALUES with selector + multiple new values + matching dst ⇒ single ADD with N elements. */
    @Test
    void addValuesWithSelector_multipleValues_existingMatch_emitsAddWithAllElements() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "+39111");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(ADD_VALUES, USER_FORM,
                List.of("+39222", "+39333"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.ADD.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("phoneNumbers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) ops.get(0).getValue();
        assertThat(elements).extracting(el -> el.get("value"))
                .containsExactly("+39222", "+39333");
    }

    /** Source attribute deleted with no values ⇒ REMOVE on canonical selector path (drops every match). */
    @Test
    void deleteValuesWithSelector_emitsRemoveOnCanonicalPath() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "+39111");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(DELETE_VALUES, USER_FORM, List.of());

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REMOVE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REMOVE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo(CANONICAL);
        assertThat(ops.get(0).getValue()).isNull();
    }

    /** Delete of a single value with selector ⇒ REMOVE on selector AND-ed with subField eq value,
     *  so peer values with the same selector are preserved. RFC 7644 strict (Keycloak-compatible). */
    @Test
    void deleteValuesWithSelector_singleValue_emitsRemoveWithFilterInPath() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "+39111");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(DELETE_VALUES, USER_FORM,
                List.of("+39327333333"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REMOVE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REMOVE.getName());
        assertThat(ops.get(0).getPath())
                .isEqualTo("phoneNumbers[type eq \"work\" and value eq \"+39327333333\"]");
        assertThat(ops.get(0).getValue()).isNull();
    }

    /** Delete of multiple values with selector ⇒ one REMOVE op per value, each with its own filter. */
    @Test
    void deleteValuesWithSelector_multipleValues_emitsOneRemovePerValue() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "+39111");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(DELETE_VALUES, USER_FORM,
                List.of("+39111", "+39222"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REMOVE.getName(), diff, lm);

        assertThat(ops).hasSize(2);
        assertThat(ops).allSatisfy(op -> {
            assertThat(op.getOp()).isEqualTo(OperationType.REMOVE.getName());
            assertThat(op.getValue()).isNull();
        });
        assertThat(ops).extracting(ScimPathOperation::getPath).containsExactly(
                "phoneNumbers[type eq \"work\" and value eq \"+39111\"]",
                "phoneNumbers[type eq \"work\" and value eq \"+39222\"]");
    }

    /** Filter values containing double quotes / backslashes are escaped per RFC 7644 §3.4.2.2. */
    @Test
    void deleteValuesWithSelector_escapesQuotesAndBackslashesInValue() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        IBean dstBean = beanWith(CANONICAL, "x");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(DELETE_VALUES, USER_FORM,
                List.of("a\"b\\c"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REMOVE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getPath())
                .isEqualTo("phoneNumbers[type eq \"work\" and value eq \"a\\\"b\\\\c\"]");
    }

    /** Problem 2/3: SCIM payload yields BOTH canonical and writableAttributes user-form keys. */
    @Test
    void flatten_aliasesUserFormFromWritableAttributes() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        String json = "{\"phoneNumbers\":[{\"type\":\"work\",\"value\":\"+39111\"}]}";

        Map<String, Object> result = invokeFlatten(dao, json);

        assertThat(result).containsKeys(CANONICAL, USER_FORM);
        assertThat(result.get(CANONICAL)).isEqualTo("+39111");
        assertThat(result.get(USER_FORM)).isEqualTo("+39111");
    }

    /** Without writableAttributes only canonical is emitted. */
    @Test
    void flatten_withoutWritableAttributes_emitsCanonicalOnly() throws Exception {
        ScimDao dao = newDao(null);
        String json = "{\"phoneNumbers\":[{\"type\":\"work\",\"value\":\"+39111\"}]}";

        Map<String, Object> result = invokeFlatten(dao, json);

        assertThat(result).containsKey(CANONICAL);
        assertThat(result).doesNotContainKey(USER_FORM);
    }

    /** Two SCIM elements canonicalizing to the same selector key accumulate into a list. */
    @Test
    void flatten_duplicateSelector_accumulatesValues() throws Exception {
        ScimDao dao = newDao(null);
        String json = "{\"phoneNumbers\":["
                + "{\"type\":\"work\",\"value\":\"+39111\"},"
                + "{\"type\":\"work\",\"value\":\"+39222\"}"
                + "]}";

        Map<String, Object> result = invokeFlatten(dao, json);

        Object value = result.get(CANONICAL);
        assertThat(value).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) value;
        assertThat(values).containsExactly("+39111", "+39222");
    }

    /** End-to-end bean wiring: setDatasets converts the accumulated list into a Set, so the
     *  user-form lookup yields all values via getDatasetById. */
    @Test
    void aliasedBean_lookupByUserForm_returnsValueSet() throws Exception {
        ScimDao dao = newDao(List.of(USER_FORM));
        Map<String, Object> entity = invokeFlatten(dao,
                "{\"phoneNumbers\":[{\"type\":\"work\",\"value\":\"+39111\"}]}");
        IBean bean = new SimpleBean();
        LscDatasets ds = new LscDatasets();
        entity.forEach((k, v) -> ds.put(k, v == null ? new LinkedHashSet<>() : v));
        bean.setDatasets(ds);

        Set<Object> byCanonical = bean.getDatasetById(CANONICAL);
        Set<Object> byUserForm = bean.getDatasetById(USER_FORM);
        assertThat(byCanonical).containsExactly("+39111");
        assertThat(byUserForm).containsExactly("+39111");
    }

    /** REPLACE on a flat multivalue path (e.g. {@code members[]}) with existing dst values
     *  emits per-value REMOVE ops for surplus dst + a single ADD with the net-new values.
     *  Regression guard for the Keycloak SCIM "501 patch-REPLACE-operation not supported
     *  for group.members" rejection of wholesale REPLACE. */
    @Test
    void replaceFlatMultivalue_existingDst_emitsRemoveAndAdd() throws Exception {
        ScimDao dao = newDao(List.of("members[]"));
        IBean dstBean = beanWith("members[]", new LinkedHashSet<>(List.of("uuid-keep", "uuid-drop")));
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, "members[]",
                List.of("uuid-keep", "uuid-new"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REMOVE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("members[value eq \"uuid-drop\"]");
        assertThat(ops.get(0).getValue()).isNull();

        assertThat(ops.get(1).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(1).getPath()).isEqualTo("members");
        @SuppressWarnings("unchecked")
        List<Object> addValues = (List<Object>) ops.get(1).getValue();
        assertThat(addValues).containsExactly("uuid-new");
    }

    /** REPLACE on a flat multivalue path with no dst values yields a single ADD with all values. */
    @Test
    void replaceFlatMultivalue_noDst_emitsAddOnly() throws Exception {
        ScimDao dao = newDao(List.of("members[]"));
        IBean dstBean = beanWith("id", "g-1");
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, "members[]",
                List.of("uuid-1", "uuid-2"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("members");
        @SuppressWarnings("unchecked")
        List<Object> addValues = (List<Object>) ops.get(0).getValue();
        assertThat(addValues).containsExactly("uuid-1", "uuid-2");
    }

    /** REPLACE on a flat multivalue path where dst already matches the requested values
     *  emits no operations — the destination is already coherent. */
    @Test
    void replaceFlatMultivalue_idempotent_emitsNothing() throws Exception {
        ScimDao dao = newDao(List.of("members[]"));
        IBean dstBean = beanWith("members[]", new LinkedHashSet<>(List.of("uuid-1", "uuid-2")));
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, "members[]",
                List.of("uuid-1", "uuid-2"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).isEmpty();
    }

    /** Source values arriving as JSON-wrapped {@code {"value":"uuid"}} (from LSC templating)
     *  must compare equal to dst values exposed as bare scalars by {@code processFlatDiffs}. */
    @Test
    void replaceFlatMultivalue_jsonWrappedSrc_comparesByValueField() throws Exception {
        ScimDao dao = newDao(List.of("members[]"));
        IBean dstBean = beanWith("members[]", new LinkedHashSet<>(List.of("uuid-keep", "uuid-drop")));
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, "members[]",
                List.of("{\"value\":\"uuid-keep\"}", "{\"value\":\"uuid-new\"}"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REMOVE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("members[value eq \"uuid-drop\"]");

        assertThat(ops.get(1).getOp()).isEqualTo(OperationType.ADD.getName());
        @SuppressWarnings("unchecked")
        List<Object> addValues = (List<Object>) ops.get(1).getValue();
        assertThat(addValues).hasSize(1);
        assertThat(addValues.get(0).toString()).contains("uuid-new");
    }

    /** REPLACE on a flat multivalue path emits element-level diff even when dst exposes the
     *  attribute only under selector keys (e.g. {@code emails[type eq "work"]}). The bare
     *  selector key carries the SCIM element's {@code value} field (see {@code processFlatDiffs}),
     *  so it is usable as the source of scalar deltas. Regression guard against the previous
     *  wholesale-REPLACE fallback, which made Keycloak return 501 on {@code group.members}. */
    @Test
    void replaceFlatMultivalue_dstSelectorKeyedOnly_emitsElementLevelDiff() throws Exception {
        ScimDao dao = newDao(List.of("emails[]"));
        IBean dstBean = new SimpleBean();
        dstBean.setMainIdentifier("pippo");
        LscDatasets ds = new LscDatasets();
        ds.put("emails[type eq \"work\"]", "dev@acme.com");
        dstBean.setDatasets(ds);
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, "emails[]",
                List.of("other@localhost.com"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REMOVE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("emails[value eq \"dev@acme.com\"]");
        assertThat(ops.get(0).getValue()).isNull();

        assertThat(ops.get(1).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(1).getPath()).isEqualTo("emails");
        @SuppressWarnings("unchecked")
        List<Object> addValues = (List<Object>) ops.get(1).getValue();
        assertThat(addValues).containsExactly("other@localhost.com");
    }

    /** With {@code flatMultivalueStrategy=WHOLESALE_REPLACE}, REPLACE on a flat multivalue path
     *  emits a single {@code replace} op carrying the full new value array — bypassing the
     *  element-level diff. Required by WSO2 Asgardeo, which rejects both {@code add} on
     *  extension flat paths and {@code remove} without a selector filter. */
    @Test
    void replaceFlatMultivalue_wholesaleReplaceStrategy_emitsSingleReplaceOp() throws Exception {
        ScimDao dao = newDao(List.of("phoneNumbers[]"), FlatMultivalueStrategyType.WHOLESALE_REPLACE);
        IBean dstBean = beanWith("phoneNumbers[]", new LinkedHashSet<>(List.of("+39327222225")));
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(REPLACE_VALUES, "phoneNumbers[]",
                List.of("+39327222222"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REPLACE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REPLACE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("phoneNumbers");
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) ops.get(0).getValue();
        assertThat(values).containsExactly("+39327222222");
    }

    /** With {@code flatMultivalueStrategy=WHOLESALE_REPLACE}, ADD_VALUES on a flat multivalue path
     *  emits a single {@code replace} op carrying the merge of dst current values + the new
     *  values (deduplicated). Asgardeo rejects {@code add} on extension flat paths, so even
     *  ADD_VALUES must be reshaped as a wholesale replace. */
    @Test
    void addValuesFlatMultivalue_wholesaleReplaceStrategy_emitsReplaceWithMerge() throws Exception {
        ScimDao dao = newDao(List.of("phoneNumbers[]"), FlatMultivalueStrategyType.WHOLESALE_REPLACE);
        IBean dstBean = beanWith("phoneNumbers[]", new LinkedHashSet<>(List.of("+39327222225")));
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(ADD_VALUES, "phoneNumbers[]",
                List.of("+39327333333"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.ADD.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REPLACE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("phoneNumbers");
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) ops.get(0).getValue();
        assertThat(values).containsExactly("+39327222225", "+39327333333");
    }

    /** With {@code flatMultivalueStrategy=WHOLESALE_REPLACE}, DELETE_VALUES on a flat multivalue
     *  path emits a single {@code replace} op carrying the dst current values minus the values
     *  to remove. Asgardeo rejects wholesale {@code remove} without a selector filter. */
    @Test
    void deleteValuesFlatMultivalue_wholesaleReplaceStrategy_emitsReplaceWithSubtraction() throws Exception {
        ScimDao dao = newDao(List.of("phoneNumbers[]"), FlatMultivalueStrategyType.WHOLESALE_REPLACE);
        IBean dstBean = beanWith("phoneNumbers[]",
                new LinkedHashSet<>(List.of("+39327222225", "+39327333333", "+39327444444")));
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(DELETE_VALUES, "phoneNumbers[]",
                List.of("+39327333333"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.REMOVE.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.REPLACE.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("phoneNumbers");
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) ops.get(0).getValue();
        assertThat(values).containsExactly("+39327222225", "+39327444444");
    }

    /** ADD_VALUES on a flat multivalue path remains untouched: single ADD with all new values
     *  (regression guard: the REPLACE fix must not bleed into the ADD path). */
    @Test
    void addValuesFlatMultivalue_unchangedByReplaceFix() throws Exception {
        ScimDao dao = newDao(List.of("members[]"));
        IBean dstBean = beanWith("members[]", new LinkedHashSet<>(List.of("uuid-existing")));
        LscModifications lm = new LscModifications(LscModificationType.UPDATE_OBJECT);
        lm.setDestinationBean(dstBean);
        LscDatasetModification diff = new LscDatasetModification(ADD_VALUES, "members[]",
                List.of("uuid-new"));

        List<ScimPathOperation> ops = invokeBuildPatchOperations(dao, OperationType.ADD.getName(), diff, lm);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getOp()).isEqualTo(OperationType.ADD.getName());
        assertThat(ops.get(0).getPath()).isEqualTo("members");
        @SuppressWarnings("unchecked")
        List<Object> addValues = (List<Object>) ops.get(0).getValue();
        assertThat(addValues).containsExactly("uuid-new");
    }

    /** Sanity: writableAttributes can be ordered/quoted differently and still alias correctly. */
    @Test
    void aliasMatchesAcrossQuotingAndOrder() throws Exception {
        ScimDao dao = newDao(List.of("emails[primary eq true and type eq home]"));
        String json = "{\"emails\":[{\"type\":\"home\",\"primary\":true,\"value\":\"a@b.c\"}]}";

        Map<String, Object> result = invokeFlatten(dao, json);

        assertThat(result).containsKeys(
                "emails[type eq \"home\" and primary eq true]",
                "emails[primary eq true and type eq home]");
    }
}
