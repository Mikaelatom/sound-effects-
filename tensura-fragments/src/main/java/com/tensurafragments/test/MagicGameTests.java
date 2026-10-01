package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.magic.SpellLearning;
import com.tensurafragments.skill.OriginalSkillStripper;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.entity.projectile.magic.FireBallProjectile;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/** Tensura's magic and lesser skills are kept and learnable; only its Unique and Ultimate skills are removed. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class MagicGameTests {
    private MagicGameTests() {
    }

    /** The first of Tensura's skills of this type that the player can actually learn (it learns it). */
    @Nullable
    private static ManasSkill learnTensuraSkillOfType(ServerPlayer player, Skill.SkillType type) {
        for (ManasSkill skill : SkillAPI.getSkillRegistry()) {
            ResourceLocation id = skill.getRegistryName();
            if (id != null && id.getNamespace().equals("tensura") && skill instanceof Skill s && s.getType() == type
                    && SkillHelper.learnSkill(player, skill) && SkillAPI.getSkillsFrom(player).getSkill(skill).isPresent()) {
                return skill;
            }
        }
        return null;
    }

    private static Magic fireBall() {
        return (Magic) SkillAPI.getSkillRegistry().get(ResourceLocation.fromNamespaceAndPath("tensura", "fire_ball"));
    }

    @GameTest(template = "platform")
    public static void onlyUniqueAndUltimateSkillsAreRemoved(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 4.5);
        // Tensura pays for most skills out of your maximum magicules.
        player.getAttribute(TensuraAttributes.MAX_MAGICULE).setBaseValue(10_000_000);
        TestPlayers.giveMagicules(player, 10_000_000);
        ManasSkill unique = learnTensuraSkillOfType(player, Skill.SkillType.UNIQUE);
        ManasSkill ultimate = learnTensuraSkillOfType(player, Skill.SkillType.ULTIMATE);
        ManasSkill common = learnTensuraSkillOfType(player, Skill.SkillType.COMMON);
        Magic magic = fireBall();
        // This Tensura version has no Ultimate skills yet, so that part is only checked if one turns up.
        helper.assertTrue(unique != null && common != null && magic != null, "Tensura has them: unique "
                + unique + ", common " + common + ", magic " + magic);
        SkillHelper.learnSkill(player, magic);
        helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(magic).isPresent(), "Fire Ball learned");
        OriginalSkillStripper.strip(player);
        var skills = SkillAPI.getSkillsFrom(player);
        helper.assertTrue(skills.getSkill(unique).isEmpty(), "Tensura's unique skill removed: " + unique.getRegistryName());
        helper.assertTrue(ultimate == null || skills.getSkill(ultimate).isEmpty(), "Tensura's ultimate skill removed");
        helper.assertTrue(skills.getSkill(common).isPresent(), "a common skill is kept: " + common.getRegistryName());
        helper.assertTrue(skills.getSkill(magic).isPresent(), "magic is kept");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void survivingASpellTeachesIt(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 4.5);
        Magic magic = fireBall();
        helper.assertTrue(magic != null, "Fire Ball exists");
        for (int hit = 1; hit <= 5; hit++) {
            DamageSource source = player.damageSources().magic();
            ((TensuraDamageSource) source).tensura$setAbilityInstance(magic.createDefaultInstance());
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            player.hurt(source, 1.0F);
            if (hit < 5) {
                helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(magic).isEmpty(), "not learned after " + hit);
                helper.assertTrue(SpellLearning.survived(player, magic) == hit, "survived " + hit + ", counted "
                        + SpellLearning.survived(player, magic));
            }
        }
        helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(magic).isPresent(), "learned Fire Ball by surviving it 5 times");
        helper.succeed();
    }

    /** A Tensura fire ball from a mob counts as the Fire Ball spell, even without the spell attached. */
    @GameTest(template = "platform")
    public static void projectilesAreRecognisedAsTheirSpell(GameTestHelper helper) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(1.5, TestPlayers.GROUND, 1.5));
        FireBallProjectile ball = new FireBallProjectile(helper.getLevel(), husk);
        DamageSource source = helper.getLevel().damageSources().mobProjectile(ball, husk);
        Magic magic = SpellLearning.spellBehind(source);
        helper.assertTrue(magic == fireBall(), "a fire ball is the Fire Ball spell, got " + (magic == null ? null : magic.getRegistryName()));
        helper.succeed();
    }
}
