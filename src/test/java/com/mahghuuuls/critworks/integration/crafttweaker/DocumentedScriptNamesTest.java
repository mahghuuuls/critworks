package com.mahghuuuls.critworks.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import org.junit.jupiter.api.Test;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenExpansion;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CraftTweaker section of the pack-maker guide names script fields and player calls in two tables. Pack makers
 * copy those names into scripts, so each one must exist in the registered script types, and each registered name
 * must be documented. The guide's examples also use CraftTweaker's own names; those are checked in game.
 */
class DocumentedScriptNamesTest {

    private static final Pattern EVENT_FIELD = Pattern.compile("^\\| `event\\.([a-zA-Z]+)` \\|", Pattern.MULTILINE);
    private static final Pattern PLAYER_CALL = Pattern.compile("`player\\.([a-zA-Z]+)[(`]");

    @Test
    void everyDocumentedEventFieldIsAScriptGetterAndBack() throws IOException {
        Set<String> documented = matches(EVENT_FIELD, subsection("### The crit event"));
        assertEquals(zenGetters(ScriptCritHit.class), documented);
    }

    @Test
    void everyDocumentedPlayerCallIsAScriptMethodAndBack() throws IOException {
        Set<String> documented = matches(PLAYER_CALL, subsection("### Player calls"));
        assertFalse(documented.isEmpty());
        Set<String> registered = new HashSet<String>(zenGetters(ScriptPlayerCalls.class));
        for (Method method : ScriptPlayerCalls.class.getMethods()) {
            if (method.getAnnotation(ZenMethod.class) != null) {
                assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
                registered.add(method.getName());
            }
        }
        assertEquals(registered, documented);
    }

    @Test
    void theDocumentedShortFormsHaveOptionalTrailingParameters() throws Exception {
        assertOptional(ScriptPlayerCalls.class.getMethod("grantCritGuarantee", crafttweaker.api.player.IPlayer.class,
                String.class, int.class, boolean.class), 2, 3);
        assertOptional(ScriptPlayerCalls.class.getMethod("getCritChance", crafttweaker.api.player.IPlayer.class,
                crafttweaker.api.item.IItemStack.class, String.class), 2);
        assertOptional(ScriptPlayerCalls.class.getMethod("getCritMultiplier", crafttweaker.api.player.IPlayer.class,
                crafttweaker.api.item.IItemStack.class, String.class), 2);
    }

    private static void assertOptional(Method method, int... parameters) {
        java.lang.annotation.Annotation[][] annotations = method.getParameterAnnotations();
        for (int parameter : parameters) {
            boolean optional = false;
            for (java.lang.annotation.Annotation annotation : annotations[parameter]) {
                optional |= annotation instanceof stanhebben.zenscript.annotations.Optional;
            }
            assertTrue(optional, method.getName() + " parameter " + parameter + " is optional in the guide");
        }
    }

    @Test
    void playerCallsExpandCraftTweakersPlayerType() {
        assertNotNull(ScriptPlayerCalls.class.getAnnotation(ZenRegister.class));
        assertEquals(crafttweaker.api.player.IPlayer.class.getAnnotation(ZenClass.class).value(),
                ScriptPlayerCalls.class.getAnnotation(ZenExpansion.class).value());
    }

    @Test
    void theDocumentedRegistrationLinesUseTheRegisteredNames() throws IOException {
        String crit = subsection("### The crit event");
        assertTrue(crit.contains("events.onCritworksHit(function(event as " + ScriptCritHit.class.getAnnotation(ZenClass.class).value()));
        assertTrue(crit.contains(ScriptEvents.class.getAnnotation(ZenClass.class).value() + ".onHit("));
    }

    private static Set<String> zenGetters(Class<?> type) {
        Set<String> names = new HashSet<String>();
        for (Method method : type.getMethods()) {
            ZenGetter getter = method.getAnnotation(ZenGetter.class);
            if (getter != null) {
                names.add(getter.value());
            }
        }
        return names;
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> names = new LinkedHashSet<String>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** The text of one subsection of the guide's CraftTweaker section, up to the next heading of its level or above. */
    private static String subsection(String heading) throws IOException {
        // Git may check the guide out with CRLF line endings on Windows.
        String guide = new String(Files.readAllBytes(Paths.get("docs", "pack-makers.md")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        int section = guide.indexOf("\n## CraftTweaker\n");
        assertTrue(section >= 0, "the guide has a CraftTweaker section");
        int start = guide.indexOf("\n" + heading + "\n", section);
        assertTrue(start >= 0, heading);
        int end = guide.length();
        for (String next : new String[] {"\n### ", "\n## "}) {
            int at = guide.indexOf(next, start + heading.length() + 2);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        return guide.substring(start, end);
    }
}
