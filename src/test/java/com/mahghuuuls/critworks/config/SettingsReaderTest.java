package com.mahghuuuls.critworks.config;

import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.CritFormula;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production reader of critworks.cfg against real files: creation, validation, read-only. */
class SettingsReaderTest {

    @TempDir
    Path folder;

    private final List<String> warnings = new ArrayList<String>();

    /** Forge's Configuration reads the game folder from FML's launch data, which a test has to provide. */
    @BeforeAll
    static void gameFolder() throws Exception {
        Field home = Class.forName("net.minecraftforge.fml.relauncher.FMLInjectionData").getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new File(System.getProperty("java.io.tmpdir")));
    }

    private CritSettings read(File file) {
        return new SettingsReader(warnings::add).read(file);
    }

    private File write(String general) throws Exception {
        File file = folder.resolve("critworks.cfg").toFile();
        Files.write(file.toPath(), ("general {\n" + general + "\n}\n").getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void missingFileIsCreatedWithShippedDefaults() {
        File file = folder.resolve("critworks.cfg").toFile();
        CritSettings settings = read(file);
        assertTrue(file.exists());
        assertEquals(5.0D, settings.getFallbackChance());
        assertEquals(1.5D, settings.getFallbackMultiplier());
        assertEquals(100.0D, settings.getMeleeChargeThreshold());
        assertEquals(100.0D, settings.getBowChargeThreshold());
        assertEquals(CritFormula.NO_CAP, settings.getDamageCap());
        assertTrue(settings.isPvpCrits());
        assertEquals("auto", settings.getCritSound());
        assertFalse(settings.isDiagnosticMode());
        for (CritSettings.Integration integration : CritSettings.Integration.values()) {
            assertTrue(settings.isIntegrationEnabled(integration));
        }
        assertTrue(warnings.isEmpty());
    }

    @Test
    void existingFileIsNeverRewritten() throws Exception {
        File file = write("    S:fallbackChance=abc\n    S:meleeChargeThreshold=150");
        byte[] before = Files.readAllBytes(file.toPath());
        read(file);
        read(file);
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
    }

    @Test
    void unparsableFileIsNotRenamedOrChanged() throws Exception {
        File file = write("    S:fallbackChance=10\n  {{{ broken");
        byte[] before = Files.readAllBytes(file.toPath());
        CritSettings settings = read(file);
        assertTrue(file.exists());
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        assertEquals(1, folder.toFile().list().length);
        assertEquals(1.5D, settings.getFallbackMultiplier());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("could not be parsed"), warnings.get(0));
    }

    @Test
    void eachBadValueGivesOneWarningAndTheDefault() throws Exception {
        CritSettings settings = read(write("    S:fallbackChance=abc\n    S:meleeChargeThreshold=150\n"
                + "    S:diagnosticMode=maybe"));
        assertEquals(5.0D, settings.getFallbackChance());
        assertEquals(100.0D, settings.getMeleeChargeThreshold());
        assertFalse(settings.isDiagnosticMode());
        assertEquals(3, warnings.size());
        assertTrue(warnings.get(0).contains("fallbackChance = \"abc\" is not a number"));
    }

    @Test
    void validValuesAreRead() throws Exception {
        CritSettings settings = read(write("    S:fallbackChance=10\n    S:fallbackDamage=2.25\n"
                + "    S:bowChargeThreshold=50\n    S:pvpCrits=false\n    S:diagnosticMode=true"));
        assertEquals(10.0D, settings.getFallbackChance());
        assertEquals(2.25D, settings.getFallbackMultiplier());
        assertEquals(50.0D, settings.getBowChargeThreshold());
        assertFalse(settings.isPvpCrits());
        assertTrue(settings.isDiagnosticMode());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void capZeroMeansNoMaximumAndBelowOneIsRefused() throws Exception {
        assertEquals(CritFormula.NO_CAP, read(write("    S:damageCap=0")).getDamageCap());
        assertEquals(3.0D, read(write("    S:damageCap=3")).getDamageCap());
        assertTrue(warnings.isEmpty());
        assertEquals(CritFormula.NO_CAP, read(write("    S:damageCap=0.5")).getDamageCap());
        assertEquals(1, warnings.size());
    }

    @Test
    void integrationCanBeTurnedOff() throws Exception {
        File file = folder.resolve("critworks.cfg").toFile();
        Files.write(file.toPath(), "integrations {\n    S:rlcombat=false\n}\n".getBytes(StandardCharsets.UTF_8));
        CritSettings settings = read(file);
        assertFalse(settings.isIntegrationEnabled(CritSettings.Integration.RLCOMBAT));
        assertTrue(settings.isIntegrationEnabled(CritSettings.Integration.JAFS));
    }
}
