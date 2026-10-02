package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Crafting recipes. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    private RecipeGameTests() {
    }

    /** A crafting table full of planks (any wood, even mixed) makes 20 paper, for Shikigami Control. */
    @GameTest(template = "platform")
    public static void planksMakePaper(GameTestHelper helper) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(new ItemStack(i % 2 == 0 ? Items.OAK_PLANKS : Items.SPRUCE_PLANKS));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(recipe.isPresent(), "a recipe matches 9 planks");
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        helper.assertTrue(result.is(Items.PAPER) && result.getCount() == 20, "20 paper, got " + result);
        helper.succeed();
    }
}
