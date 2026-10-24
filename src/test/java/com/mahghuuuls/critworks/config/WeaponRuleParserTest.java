package com.mahghuuuls.critworks.config;

import com.mahghuuuls.critworks.core.WeaponRule;
import net.minecraft.item.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production weapons.json parser with good and bad rules and the shipped examples. */
class WeaponRuleParserTest {

    private final Map<String, Item> items = new HashMap<String, Item>();
    private final List<String> warnings = new ArrayList<String>();
    private WeaponRuleParser parser;

    @BeforeEach
    void setUp() {
        for (String id : new String[]{"minecraft:stick", "minecraft:diamond_sword", "minecraft:iron_sword",
                "minecraft:wool", "minecraft:golden_sword"}) {
            items.put(id, new Item().setRegistryName(id));
        }
        parser = new WeaponRuleParser(items::get, warnings::add);
    }

    @Test
    void shippedExamplesAllParse() {
        List<WeaponRule> rules = parser.parse(WeaponRulesFile.EXAMPLES);
        assertEquals(5, rules.size());
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(Boolean.TRUE, rules.get(0).getEligible());
        assertEquals(50.0D, rules.get(0).getChance());
        assertEquals(2.0D, rules.get(0).getDamage());
        assertEquals(Boolean.FALSE, rules.get(1).getEligible());
        assertNull(rules.get(2).getEligible());
    }

    @Test
    void badRulesAreSkippedOneWarningEachAndTheRestLoad() {
        List<WeaponRule> rules = parser.parse("["
                + "{\"item\":\"minecraft:stick\",\"chance\":\"abc\"},"
                + "{\"item\":\"nomod:nothing\",\"chance\":10},"
                + "{\"item\":\"minecraft:stick\",\"colour\":1},"
                + "{\"item\":\"minecraft:stick\",\"chance\":150},"
                + "{\"item\":\"minecraft:stick\",\"damage\":0.5},"
                + "{\"item\":\"minecraft:stick\",\"meta\":-1,\"chance\":10},"
                + "{\"item\":\"minecraft:stick\",\"nbt\":\"{broken\",\"chance\":10},"
                + "{\"item\":\"minecraft:stick\"},"
                + "{\"item\":\"minecraft:stick\",\"nbt\":\"{}\",\"chance\":10},"
                + "{\"item\":\"minecraft:iron_sword\",\"chance\":10}"
                + "]");
        assertEquals(1, rules.size());
        assertEquals(9, warnings.size());
        assertTrue(warnings.get(1).contains("rule #2 skipped: unknown item \"nomod:nothing\""), warnings.get(1));
        assertEquals(9, rules.get(0).getIndex());
    }

    @Test
    void notAListLoadsNothing() {
        assertTrue(parser.parse("{\"item\":\"minecraft:stick\"}").isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(parser.parse("not json [").isEmpty());
        assertEquals(2, warnings.size());
    }

    @Test
    void emptyListLoadsNothingSilently() {
        assertTrue(parser.parse("[]").isEmpty());
        assertTrue(warnings.isEmpty());
    }
}
