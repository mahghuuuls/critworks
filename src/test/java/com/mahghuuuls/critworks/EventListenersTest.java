package com.mahghuuuls.critworks;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge 1.12.2 calls each {@code @SubscribeEvent} method through a generated class in another package and
 * class loader. That class can only reach public classes and methods: a listener on a class that is not
 * public registers without a message and then throws at its first event. This checks every listener in
 * the mod's own classes.
 */
class EventListenersTest {

    @Test
    void everyEventListenerIsReachableByForge() throws Exception {
        Path root = Paths.get(CritworksMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> classNames;
        try (Stream<Path> files = Files.walk(root)) {
            classNames = files.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> root.relativize(path).toString().replace(File.separatorChar, '.'))
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .collect(Collectors.toList());
        }
        List<String> problems = new ArrayList<String>();
        int listeners = 0;
        for (String name : classNames) {
            Class<?> type = Class.forName(name, false, getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(SubscribeEvent.class)) {
                    continue;
                }
                listeners++;
                if (!Modifier.isPublic(method.getModifiers())) {
                    problems.add(name + "." + method.getName() + " is not public");
                }
                for (Class<?> owner = type; owner != null; owner = owner.getEnclosingClass()) {
                    if (!Modifier.isPublic(owner.getModifiers())) {
                        problems.add(owner.getName() + " (listener " + method.getName() + ") is not public");
                    }
                }
            }
        }
        assertTrue(listeners > 10, "found " + listeners + " listeners under " + root);
        assertEquals(new ArrayList<String>(), problems);
    }
}
