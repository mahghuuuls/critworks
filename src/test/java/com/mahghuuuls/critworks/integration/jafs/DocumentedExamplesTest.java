package com.mahghuuuls.critworks.integration.jafs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.config.WeaponRuleParser;
import com.mahghuuuls.critworks.config.WeaponRulesFile;
import com.mahghuuuls.critworks.core.WeaponClassifier;
import com.mahghuuuls.critworks.core.WeaponRule;
import com.mahghuuuls.critworks.core.WeaponRules;
import com.mahghuuuls.critworks.integration.spartan.SpartanWeaponTypes;
import com.mahghuuuls.jafs.api.PropertySpec;
import com.mahghuuuls.jafs.config.DefinitionJson;
import com.mahghuuuls.jafs.core.DefinitionValidator;
import com.mahghuuuls.jafs.core.RawDefinition;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every fenced json example in docs/api.md and docs/pack-makers.md, read with the production readers: a
 * weapons.json example through the weapon rule parser and the checks the loader makes after it, a JAFS food
 * through JAFS's own reader and validator with the two Critworks properties, and a Quality Tools quality by
 * the attribute names and the operation it uses. Any warning fails. The test lives next to the JAFS
 * properties because their ids are package-private.
 */
class DocumentedExamplesTest {

    private static final String[] DOCUMENTS = {"docs/api.md", "docs/pack-makers.md"};
    private static final Pattern JSON_BLOCK = Pattern.compile("```json[ \\t]*\\r?\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern ZENSCRIPT_BLOCK = Pattern.compile("```zenscript[ \\t]*\\r?\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern SETWORKS_ATTRIBUTE = Pattern.compile(
            "\\.attribute\\(\\s*\"([^\"]*)\"\\s*,\\s*[-0-9.]+\\s*,\\s*\"([^\"]*)\"\\s*\\)");

    /** The weapons.json parser looks items up in the vanilla registry. */
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    /** The file an example shows. */
    private enum Kind {
        WEAPONS,
        JAFS_FOODS,
        QUALITY_TOOLS
    }

    /** One fenced json block: where it is, its text, and the file it shows. */
    private static final class Example {
        final String where;
        final String json;
        final Kind kind;

        Example(String where, String json, Kind kind) {
            this.where = where;
            this.json = json;
            this.kind = kind;
        }
    }

    /** Every json block of both documents, in document order; a block that is not strict JSON fails here. */
    private static List<Example> examples() throws IOException {
        List<Example> examples = new ArrayList<Example>();
        for (String document : DOCUMENTS) {
            String text = new String(Files.readAllBytes(Paths.get(document)), StandardCharsets.UTF_8);
            Matcher block = JSON_BLOCK.matcher(text);
            int number = 0;
            while (block.find()) {
                number++;
                String where = document + " json example #" + number;
                examples.add(new Example(where, block.group(1), kindOf(where, strictParse(where, block.group(1)))));
            }
        }
        return examples;
    }

    private static List<Example> only(Kind kind) throws IOException {
        List<Example> result = new ArrayList<Example>();
        for (Example example : examples()) {
            if (example.kind == kind) {
                result.add(example);
            }
        }
        return result;
    }

    /** Parses strict JSON, as a pack maker's editor would check it: no comments, no trailing text. */
    private static JsonElement strictParse(String where, String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            JsonElement root = new Gson().getAdapter(JsonElement.class).read(reader);
            assertEquals(JsonToken.END_DOCUMENT, reader.peek(), where + " has text after its JSON value");
            return root;
        } catch (IOException | RuntimeException e) {
            return fail(where + " is not valid JSON: " + e);
        }
    }

    private static Kind kindOf(String where, JsonElement root) {
        if (root.isJsonObject() && root.getAsJsonObject().has("qualities")) {
            return Kind.QUALITY_TOOLS;
        }
        if (root.isJsonArray() && root.getAsJsonArray().size() > 0 && root.getAsJsonArray().get(0).isJsonObject()) {
            JsonObject first = root.getAsJsonArray().get(0).getAsJsonObject();
            if (first.has("modifiers")) {
                return Kind.JAFS_FOODS;
            }
            if (first.has("item") || first.has("spartan_type")) {
                return Kind.WEAPONS;
            }
        }
        return fail(where + " is not a weapons.json, JAFS food, or Quality Tools quality example");
    }

    @Test
    void theDocumentsShowEachRequiredExample() throws IOException {
        List<Example> examples = examples();
        for (Kind kind : Kind.values()) {
            boolean shown = false;
            for (Example example : examples) {
                shown |= example.kind == kind;
            }
            assertTrue(shown, "no " + kind + " example in " + String.join(", ", DOCUMENTS));
        }
    }

    @Test
    void weaponsExamplesLoadEveryRuleWithNoWarning() throws IOException {
        for (Example example : only(Kind.WEAPONS)) {
            List<String> warnings = new ArrayList<String>();
            WeaponRuleParser parser = new WeaponRuleParser(WeaponRulesFile::lookup, SpartanWeaponTypes::forName,
                    SpartanWeaponTypes.names(), warnings::add);
            List<WeaponRule> rules = parser.parse(example.json);
            // The loader's checks after parsing: equally specific rules, and values for an item that is not a weapon.
            new WeaponRules(rules, warnings::add);
            for (WeaponRule rule : WeaponRules.rulesWithoutEffect(rules, WeaponClassifier::isRecognizedItem)) {
                warnings.add("rule #" + (rule.getIndex() + 1) + " sets values for an item that is not a weapon");
            }
            assertTrue(warnings.isEmpty(), example.where + ": " + warnings);
            assertEquals(strictParse(example.where, example.json).getAsJsonArray().size(), rules.size(), example.where);
        }
    }

    @Test
    void jafsFoodExamplesUseTheCritworksPropertiesAndPassJafsValidation() throws IOException {
        Map<String, PropertySpec> specs = new HashMap<String, PropertySpec>();
        for (JafsCritProperties.Property property : JafsCritProperties.Property.values()) {
            specs.put(property.id, property.spec());
        }
        for (Example example : only(Kind.JAFS_FOODS)) {
            List<RawDefinition> foods = DefinitionJson.read(new StringReader(example.json));
            assertFalse(foods.isEmpty(), example.where);
            for (RawDefinition food : foods) {
                assertFalse(food.modifiers.isEmpty(), example.where + ": food " + food.id + " has no modifier");
                for (RawDefinition.RawModifier modifier : food.modifiers) {
                    assertTrue(specs.containsKey(modifier.property), example.where + ": " + modifier.property
                            + " is not a Critworks property " + specs.keySet());
                }
            }
            DefinitionValidator.Result result = DefinitionValidator.validate(foods, specs::get);
            assertTrue(result.problems.isEmpty(), example.where + ": " + result.problems);
            assertEquals(foods.size(), result.definitions.size(), example.where);
        }
    }

    @Test
    void qualityToolsExamplesAddToTheCritworksAttributesWithOperationZero() throws IOException {
        Set<String> names = new HashSet<String>();
        for (CritStat stat : CritStat.values()) {
            names.add(stat.getAttributeName());
        }
        for (Example example : only(Kind.QUALITY_TOOLS)) {
            JsonArray qualities = strictParse(example.where, example.json).getAsJsonObject().getAsJsonArray("qualities");
            assertTrue(qualities.size() > 0, example.where + " has no quality");
            for (JsonElement quality : qualities) {
                JsonArray attributes = quality.getAsJsonObject().getAsJsonArray("attributes");
                assertTrue(attributes != null && attributes.size() > 0, example.where + ": a quality has no attribute");
                for (JsonElement element : attributes) {
                    JsonObject attribute = element.getAsJsonObject();
                    String name = attribute.get("name").getAsString();
                    assertTrue(names.contains(name), example.where + ": " + name + " is not a Critworks attribute " + names);
                    assertTrue(attribute.get("amount").getAsJsonPrimitive().isNumber(), example.where + ": " + name);
                    assertEquals(0, attribute.get("operation").getAsInt(),
                            example.where + ": " + name + " must use operation 0, the only one Critworks counts");
                }
            }
        }
    }

    @Test
    void setworksExamplesAddToTheCritworksAttributes() throws IOException {
        Set<String> names = new HashSet<String>();
        for (CritStat stat : CritStat.values()) {
            names.add(stat.getAttributeName());
        }
        int found = 0;
        for (String document : DOCUMENTS) {
            String text = new String(Files.readAllBytes(Paths.get(document)), StandardCharsets.UTF_8);
            Matcher block = ZENSCRIPT_BLOCK.matcher(text);
            while (block.find()) {
                Matcher attribute = SETWORKS_ATTRIBUTE.matcher(block.group(1));
                while (attribute.find()) {
                    found++;
                    assertTrue(names.contains(attribute.group(1)), document + ": " + attribute.group(1)
                            + " is not a Critworks attribute " + names);
                    assertEquals("add", attribute.group(2), document + ": only add counts on a Critworks attribute");
                }
            }
        }
        assertTrue(found > 0, "no Setworks attribute example in " + String.join(", ", DOCUMENTS));
    }
}
