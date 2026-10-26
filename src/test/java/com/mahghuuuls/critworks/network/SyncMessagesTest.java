package com.mahghuuuls.critworks.network;

import com.mahghuuuls.critworks.core.ItemType;
import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.core.WeaponRule;
import com.mahghuuuls.critworks.core.WeaponRules;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises both sync messages through their byte form: the server's settings and rules reach a client
 * that has a default local config (REQ-132), and bonus names survive the trip that vanilla drops them on.
 */
class SyncMessagesTest {

    private static final Function<String, Item> ITEMS = Item::getByNameOrId;
    private final List<String> warnings = new ArrayList<String>();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private static ConfigSyncMessage roundTrip(ConfigSyncMessage message) {
        ByteBuf buf = Unpooled.buffer();
        message.toBytes(buf);
        ConfigSyncMessage received = new ConfigSyncMessage();
        received.fromBytes(buf);
        assertEquals(0, buf.readableBytes());
        return received;
    }

    private static CritSettings serverSettings() {
        boolean[] all = new boolean[CritSettings.Integration.values().length];
        Arrays.fill(all, true);
        return new CritSettings(0.0D, 2.0D, 80.0D, 50.0D, 3.0D, false, "none", true, all);
    }

    @Test
    void aServerRuleShowsOnAClientWithTheDefaultConfig() throws Exception {
        WeaponRules server = new WeaponRules(Arrays.asList(
                new WeaponRule(0, Items.IRON_SWORD, null, null, null, 30.0D, null),
                new WeaponRule(1, Items.BOW, null, JsonToNBT.getTagFromJson("{display:{Name:\"Zero Bow\"}}"), null, 0.0D, 3.0D),
                new WeaponRule(2, Items.DIAMOND_SWORD, 0, null, false, null, null),
                new WeaponRule(3, Items.STICK, null, null, true, null, null)), s -> { });
        ConfigSyncMessage received = roundTrip(ConfigSyncMessage.of(serverSettings(), server));
        assertTrue(received.isReadable());

        CritSettings client = received.settingsOver(CritSettings.defaults());
        StatResolver resolver = new StatResolver(client, received.rules(ITEMS, warnings::add),
                new BonusCollector(LogManager.getLogger("test")));
        assertEquals(30.0D, resolver.resolve(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND).getBaseChance());
        ItemStack zeroBow = new ItemStack(Items.BOW);
        zeroBow.setTagCompound(JsonToNBT.getTagFromJson("{display:{Name:\"Zero Bow\"}}"));
        CritProfile bow = resolver.resolve(null, zeroBow, EnumHand.MAIN_HAND);
        assertEquals(0.0D, bow.getBaseChance());
        assertEquals(3.0D, bow.getBaseMultiplier());
        assertFalse(resolver.resolve(null, new ItemStack(Items.DIAMOND_SWORD), EnumHand.MAIN_HAND).isEligible());
        assertTrue(resolver.resolve(null, new ItemStack(Items.STICK), EnumHand.MAIN_HAND).isEligible());
        assertEquals(2.0D, resolver.resolve(null, new ItemStack(Items.GOLDEN_SWORD), EnumHand.MAIN_HAND).getBaseMultiplier());
        assertEquals(0, warnings.size());
    }

    @Test
    void numericSettingsComeFromTheServerAndTheRestStaysLocal() {
        CritSettings client = roundTrip(ConfigSyncMessage.of(serverSettings(), WeaponRules.empty()))
                .settingsOver(CritSettings.defaults());
        assertEquals(0.0D, client.getFallbackChance());
        assertEquals(2.0D, client.getFallbackMultiplier());
        assertEquals(80.0D, client.getMeleeChargeThreshold());
        assertEquals(50.0D, client.getBowChargeThreshold());
        assertEquals(3.0D, client.getDamageCap());
        assertEquals(CritSettings.SOUND_AUTO, client.getCritSound());
        assertFalse(client.isDiagnosticMode());
        assertTrue(client.isPvpCrits());
    }

    @Test
    void theServersIntegrationSwitchesReachTheClientExceptQuark() {
        boolean[] server = new boolean[CritSettings.Integration.values().length];
        Arrays.fill(server, true);
        server[CritSettings.Integration.QUALITY_TOOLS.ordinal()] = false;
        server[CritSettings.Integration.QUARK.ordinal()] = false;
        CritSettings serverSettings = new CritSettings(5.0D, 1.5D, 100.0D, 100.0D, 3.0D, true, "auto", false, server);
        CritSettings client = roundTrip(ConfigSyncMessage.of(serverSettings, WeaponRules.empty()))
                .settingsOver(CritSettings.defaults());
        assertFalse(client.isIntegrationEnabled(CritSettings.Integration.QUALITY_TOOLS));
        assertFalse(client.isIntegrationEnabled("qualitytools"));
        assertTrue(client.isIntegrationEnabled(CritSettings.Integration.QUARK), "the Quark row is this client's choice");
        assertTrue(client.isIntegrationEnabled(CritSettings.Integration.SPARTAN_WEAPONRY));
    }

    @Test
    void aRuleForAnItemTheClientLacksIsLeftOutWithAWarning() {
        ConfigSyncMessage received = roundTrip(ConfigSyncMessage.of(serverSettings(), new WeaponRules(Collections.singletonList(
                new WeaponRule(0, Items.IRON_SWORD, null, null, null, 30.0D, null)), s -> { })));
        WeaponRules rules = received.rules(id -> null, warnings::add);
        assertEquals(0, rules.all().size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("minecraft:iron_sword"));
    }

    @Test
    void anUnknownFormatIsNotRead() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(ConfigSyncMessage.VERSION + 1);
        ConfigSyncMessage received = new ConfigSyncMessage();
        received.fromBytes(buf);
        assertFalse(received.isReadable());
    }

    @Test
    void bonusNamesSurviveTheTripAndOnlyAddModifiersCount() {
        AttributeMap map = new AttributeMap();
        for (CritStat stat : CritStat.values()) {
            map.registerAttribute(CritAttributes.attribute(stat));
        }
        UUID jafs = UUID.randomUUID();
        map.getAttributeInstance(CritAttributes.attribute(CritStat.ADDITIONAL_CHANCE))
                .applyModifier(new AttributeModifier(jafs, "JAFS", 10.0D, 0));
        UUID percent = UUID.randomUUID();
        map.getAttributeInstance(CritAttributes.attribute(CritStat.INCREASED_DAMAGE))
                .applyModifier(new AttributeModifier(percent, "scaled", 0.5D, 1));
        Map<UUID, String> labels = ClientSyncSender.labelsOf(map::getAttributeInstance);
        assertEquals(1, labels.size());
        assertEquals("JAFS", labels.get(jafs));
        assertNull(labels.get(percent));

        ByteBuf buf = Unpooled.buffer();
        new BonusLabelsMessage(labels).toBytes(buf);
        BonusLabelsMessage received = new BonusLabelsMessage();
        received.fromBytes(buf);
        assertEquals(labels, received.getLabels());
    }

    @Test
    void typeRulesReachTheClientByName() {
        ItemType anySword = new ItemType() {
            @Override
            public String name() {
                return "longsword";
            }

            @Override
            public String describe() {
                return "spartan type longsword";
            }

            @Override
            public boolean matches(Item item) {
                return item == Items.IRON_SWORD;
            }
        };
        WeaponRules server = new WeaponRules(Arrays.asList(WeaponRule.forType(0, anySword, null, 25.0D, 1.8D),
                new WeaponRule(1, Items.DIAMOND_SWORD, null, null, null, 10.0D, null)), s -> { });
        ConfigSyncMessage received = roundTrip(ConfigSyncMessage.of(serverSettings(), server));
        WeaponRules client = received.rules(ITEMS, name -> "longsword".equals(name) ? anySword : null, warnings::add);
        assertEquals(2, client.all().size());
        WeaponRule type = client.match(new ItemStack(Items.IRON_SWORD)).chance;
        assertEquals("pack rule #1 (spartan type longsword)", type.describe());
        assertEquals(1.8D, client.match(new ItemStack(Items.IRON_SWORD)).damage.getDamage());
        assertEquals(10.0D, client.match(new ItemStack(Items.DIAMOND_SWORD)).chance.getChance());

        WeaponRules withoutTypes = received.rules(ITEMS, name -> null, warnings::add);
        assertEquals(1, withoutTypes.all().size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("spartan type longsword"));
    }
}
