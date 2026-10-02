package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.card.CardEntity;
import com.tensurafragments.card.CardTornadoEntity;
import com.tensurafragments.card.SpellCard;
import com.tensurafragments.card.SpellCards;
import com.tensurafragments.skill.GambitCards;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.storage.TensuraStorages;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Gambit Cards' Spell Cards: inscribing, throwing, every spell, chains and combos. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class SpellCardGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private SpellCardGameTests() {
    }

    private static ServerPlayer gambler(GameTestHelper helper, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        SkillHelper.learnSkill(player, ModSkills.GAMBIT_CARDS.get());
        return player;
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /** A Spell Card of yours stuck on the husk. */
    private static CardEntity onto(GameTestHelper helper, ServerPlayer owner, Husk husk, SpellCard spell) {
        CardEntity card = CardEntity.createSpell(owner, spell);
        helper.getLevel().addFreshEntity(card);
        card.attachTo(husk);
        return card;
    }

    private static List<CardTornadoEntity> tornadoes(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(CardTornadoEntity.class, new AABB(player.blockPosition()).inflate(16));
    }

    @GameTest(template = "platform")
    public static void inscribingCostsMagicules(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 4.5);
        TestPlayers.giveMagicules(player, 1000);
        helper.assertTrue(SpellCards.selected(player) == SpellCard.FLAME, "Flame first");
        helper.assertTrue(SpellCards.inscribe(player), "inscribed");
        helper.assertTrue(player.getInventory().countItem(ModRegistries.SPELL_CARD_ITEMS.get(SpellCard.FLAME).get()) == 1,
                "a Flame Card");
        helper.assertTrue(TensuraStorages.getExistenceFrom(player).getMagicule() == 1000 - 120, "paid 120 magicules");
        SpellCards.cycle(player);
        helper.assertTrue(SpellCards.selected(player) == SpellCard.FROST, "switched to Frost");
        TestPlayers.giveMagicules(player, 50);
        helper.assertFalse(SpellCards.inscribe(player), "not enough magicules");
        helper.succeed();
    }

    /** Spell Cards don't take the plain cards' slots (3 at most). */
    @GameTest(template = "platform")
    public static void spellCardsAreOutsideTheCardLimit(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 4.5);
        CardEntity spell = SpellCards.throwCard(player, SpellCard.FLAME);
        for (int i = 0; i < 4; i++) {
            GambitCards.setDeck(player, 5);
            GambitCards.throwCard(player);
        }
        helper.assertTrue(spell.isAlive(), "the Spell Card survives four plain throws");
        helper.assertTrue(GambitCards.getCards(player).stream().filter(c -> !c.isSpellCard()).count() == 3, "3 plain cards");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void flameBurns(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 0.5);
        Husk husk = dummy(helper, 4.5, 5.5);
        onto(helper, player, husk, SpellCard.FLAME).detonate();
        helper.assertTrue(husk.getHealth() <= 1000 - 19 && husk.isOnFire(), "burned, at " + husk.getHealth());
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "never hurts you");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void frostSlows(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 0.5);
        Husk husk = dummy(helper, 4.5, 5.5);
        onto(helper, player, husk, SpellCard.FROST).detonate();
        helper.assertTrue(husk.getHealth() < 1000 && husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "frozen");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void thunderLeaps(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 0.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        Husk other = dummy(helper, 8.5, 8.5);
        onto(helper, player, husk, SpellCard.THUNDER).detonate();
        helper.assertTrue(husk.getHealth() <= 1000 - 28, "struck, at " + husk.getHealth());
        helper.assertTrue(other.getHealth() <= 1000 - 14, "and it leapt to the other, at " + other.getHealth());
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void galeMakesATornado(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 0.5, 0.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        Husk far = dummy(helper, 8.5, 4.5);
        onto(helper, player, husk, SpellCard.GALE).detonate();
        List<CardTornadoEntity> tornadoes = tornadoes(helper, player);
        helper.assertTrue(tornadoes.size() == 1 && tornadoes.get(0).variant() == CardTornadoEntity.Variant.WIND, "a tornado");
        double start = far.distanceTo(tornadoes.get(0));
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < 1000, "the caught husk is ground down");
            helper.assertTrue(far.distanceTo(tornadoes.get(0)) < start - 1, "the far one is dragged in");
        });
    }

    /** Quake: a wide slam that throws everything up. */
    @GameTest(template = "platform")
    public static void quakeHitsWide(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 0.5, 8.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        Husk wide = dummy(helper, 8.5, 0.5);
        onto(helper, player, husk, SpellCard.QUAKE).detonate();
        helper.assertTrue(husk.getHealth() <= 1000 - 18 && husk.getDeltaMovement().y > 0.5, "slammed up");
        helper.assertTrue(wide.getHealth() < 1000, "hits wide (5.6 blocks away)");
        helper.succeed();
    }

    /** Meteor: lands a second later in a huge blast. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void meteorLandsLater(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 0.5, 0.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        Husk wide = dummy(helper, 8.5, 8.5);
        onto(helper, player, husk, SpellCard.METEOR).detonate();
        helper.assertTrue(husk.getHealth() == 1000, "nothing yet: it's still falling");
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() <= 1000 - 50, "crushed, at " + husk.getHealth());
            helper.assertTrue(wide.getHealth() < 1000, "and the blast is huge");
        });
    }

    /** Three Flame Cards on one husk: links at 100%, 150%, 200%, then Three of a Kind at double power. */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void chainsGrowStronger(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 0.5);
        Husk husk = dummy(helper, 4.5, 5.5);
        CardEntity first = onto(helper, player, husk, SpellCard.FLAME);
        onto(helper, player, husk, SpellCard.FLAME);
        onto(helper, player, husk, SpellCard.FLAME);
        first.detonate();
        helper.assertTrue(GambitCards.getCards(player).isEmpty(), "all three went into the chain");
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() <= 1000 - 4.5F * 20,
                "a chain hits far harder than one card (20): at " + husk.getHealth()));
    }

    @GameTest(template = "platform")
    public static void combosAreRecognised(GameTestHelper helper) {
        helper.assertTrue(SpellCards.combo(List.of(SpellCard.FLAME, SpellCard.GALE)) == SpellCards.Combo.FIRE_TORNADO, "fire tornado");
        helper.assertTrue(SpellCards.combo(List.of(SpellCard.GALE, SpellCard.THUNDER)) == SpellCards.Combo.THUNDERSTORM, "storm");
        helper.assertTrue(SpellCards.combo(List.of(SpellCard.FROST, SpellCard.FLAME)) == SpellCards.Combo.STEAM_EXPLOSION, "steam");
        helper.assertTrue(SpellCards.combo(List.of(SpellCard.METEOR, SpellCard.QUAKE)) == SpellCards.Combo.CATACLYSM, "cataclysm");
        helper.assertTrue(SpellCards.combo(List.of(SpellCard.QUAKE, SpellCard.QUAKE, SpellCard.QUAKE))
                == SpellCards.Combo.THREE_OF_A_KIND, "three of a kind");
        helper.assertTrue(SpellCards.combo(List.of(SpellCard.FLAME, SpellCard.THUNDER)) == null, "no combo");
        helper.assertTrue(SpellCards.combo(List.of(SpellCard.FLAME)) == null, "one card, no combo");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void flameAndGaleMakeAFireTornado(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 0.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        CardEntity first = onto(helper, player, husk, SpellCard.FLAME);
        onto(helper, player, husk, SpellCard.GALE);
        first.detonate();
        helper.succeedWhen(() -> helper.assertTrue(tornadoes(helper, player).stream()
                .anyMatch(t -> t.variant() == CardTornadoEntity.Variant.FIRE), "a fire tornado"));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void thunderAndGaleMakeAStorm(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 0.5, 0.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        CardEntity first = onto(helper, player, husk, SpellCard.THUNDER);
        onto(helper, player, husk, SpellCard.GALE);
        first.detonate();
        helper.succeedWhen(() -> helper.assertTrue(tornadoes(helper, player).stream()
                .anyMatch(t -> t.variant() == CardTornadoEntity.Variant.STORM), "a thunderstorm"));
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void flameAndFrostMakeSteam(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 4.5, 0.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        CardEntity first = onto(helper, player, husk, SpellCard.FLAME);
        onto(helper, player, husk, SpellCard.FROST);
        first.detonate();
        helper.succeedWhen(() -> helper.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "blinded by steam"));
    }

    /** Quake + Meteor: the biggest blast in the kit. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void quakeAndMeteorMakeACataclysm(GameTestHelper helper) {
        ServerPlayer player = gambler(helper, 0.5, 0.5);
        Husk husk = dummy(helper, 4.5, 4.5);
        CardEntity first = onto(helper, player, husk, SpellCard.QUAKE);
        onto(helper, player, husk, SpellCard.METEOR);
        first.detonate();
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() <= 1000 - (18 + 75 + 80),
                "quake, meteor and cataclysm all landed: at " + husk.getHealth()));
    }
}
