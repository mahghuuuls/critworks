package com.mahghuuuls.critworks.config;

import com.mahghuuuls.critworks.core.WeaponClassifier;
import com.mahghuuuls.critworks.core.WeaponRule;
import com.mahghuuuls.critworks.core.WeaponRules;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loads {@code config/critworks/weapons.json} once at start. A missing file is created as an empty
 * list; an existing file is only read. {@code weapons.examples.json} is rewritten on every start for
 * reading and is never loaded.
 */
public final class WeaponRulesFile {

    static final String EXAMPLES = "[\n"
            + "  { \"item\": \"minecraft:stick\", \"eligible\": true, \"chance\": 50, \"damage\": 2.0 },\n"
            + "  { \"item\": \"minecraft:diamond_sword\", \"eligible\": false },\n"
            + "  { \"item\": \"minecraft:iron_sword\", \"chance\": 10 },\n"
            + "  { \"item\": \"minecraft:wool\", \"meta\": 14, \"eligible\": true, \"chance\": 20 },\n"
            + "  { \"item\": \"minecraft:golden_sword\", \"nbt\": \"{display:{Name:\\\"Lucky Blade\\\"}}\", \"chance\": 100 }\n"
            + "]\n";

    private final Logger logger;

    public WeaponRulesFile(Logger logger) {
        this.logger = logger;
    }

    public WeaponRules load(File folder) {
        Consumer<String> warnings = logger::warn;
        File file = new File(folder, "weapons.json");
        try {
            Files.createDirectories(folder.toPath());
        } catch (IOException e) {
            logger.warn("Critworks config folder {} could not be created ({}); no rules loaded", folder, e.getMessage());
            return WeaponRules.empty();
        }
        writeExamples(folder);
        try {
            if (!file.exists()) {
                Files.write(file.toPath(), "[]\n".getBytes(StandardCharsets.UTF_8));
                return WeaponRules.empty();
            }
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<WeaponRule> rules = new WeaponRuleParser(WeaponRulesFile::lookup, warnings).parse(json);
            for (WeaponRule rule : WeaponRules.rulesWithoutEffect(rules, WeaponClassifier::isRecognizedItem)) {
                warnings.accept("Critworks config weapons.json: rule #" + (rule.getIndex() + 1) + " sets values for "
                        + rule.getItem().getRegistryName() + ", which is not a weapon; add \"eligible\": true to make it one");
            }
            logger.info("Critworks loaded {} weapon rule(s) from {}", rules.size(), file.getName());
            return new WeaponRules(rules, warnings);
        } catch (IOException e) {
            logger.warn("Critworks config weapons.json could not be read or created ({}); no rules loaded",
                    e.getMessage());
            return WeaponRules.empty();
        }
    }

    /** The examples are for reading only; failing to write them never affects the rules. */
    private void writeExamples(File folder) {
        try {
            Files.write(new File(folder, "weapons.examples.json").toPath(), EXAMPLES.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warn("Critworks could not write weapons.examples.json ({})", e.getMessage());
        }
    }

    private static Item lookup(String id) {
        ResourceLocation location = new ResourceLocation(id);
        return ForgeRegistries.ITEMS.containsKey(location) ? ForgeRegistries.ITEMS.getValue(location) : null;
    }
}
