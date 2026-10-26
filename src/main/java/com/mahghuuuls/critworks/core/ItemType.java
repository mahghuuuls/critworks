package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;

/**
 * A kind of item a pack rule can match instead of one item id, such as a Spartan Weaponry weapon type. A
 * type rule is less specific than any id rule (REQ-013). The integration that knows the type owns how it
 * matches; rules only ask.
 */
public interface ItemType {

    /** The name used in {@code weapons.json}, for example "dagger". */
    String name();

    /** How the type is named in inspection, for example "spartan type dagger". */
    String describe();

    boolean matches(Item item);
}
