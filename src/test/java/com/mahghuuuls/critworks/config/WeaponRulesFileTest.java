package com.mahghuuuls.critworks.config;

import com.mahghuuuls.critworks.core.WeaponRules;
import net.minecraft.init.Bootstrap;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production loader of config/critworks against a real folder: first start and later starts. */
class WeaponRulesFileTest {

    @TempDir
    Path folder;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private WeaponRules load() {
        return new WeaponRulesFile(LogManager.getLogger("test")).load(folder.toFile());
    }

    @Test
    void firstStartCreatesAnEmptyRuleListAndTheExamples() throws Exception {
        WeaponRules rules = load();
        assertTrue(rules.all().isEmpty());
        assertEquals("[]\n", new String(Files.readAllBytes(folder.resolve("weapons.json")), StandardCharsets.UTF_8));
        assertEquals(WeaponRulesFile.EXAMPLES,
                new String(Files.readAllBytes(folder.resolve("weapons.examples.json")), StandardCharsets.UTF_8));
    }

    @Test
    void anEditedFileStaysByteIdenticalOverTwoStarts() throws Exception {
        File file = folder.resolve("weapons.json").toFile();
        byte[] edited = ("[ {\"item\": \"minecraft:stick\", \"eligible\": true, \"chance\": 50},\n"
                + "  {\"item\": \"nomod:nothing\", \"chance\": 10} ]\n").getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(), edited);
        assertEquals(1, load().all().size());
        assertEquals(1, load().all().size());
        assertArrayEquals(edited, Files.readAllBytes(file.toPath()));
    }

    @Test
    void examplesFileIsRewrittenButNeverRead() throws Exception {
        Files.write(folder.resolve("weapons.examples.json"), "not json".getBytes(StandardCharsets.UTF_8));
        Files.write(folder.resolve("weapons.json"), "[]\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(load().all().isEmpty());
        assertEquals(WeaponRulesFile.EXAMPLES,
                new String(Files.readAllBytes(folder.resolve("weapons.examples.json")), StandardCharsets.UTF_8));
    }
}
