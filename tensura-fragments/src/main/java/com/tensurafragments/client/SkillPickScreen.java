package com.tensurafragments.client;

import com.tensurafragments.network.PickSkillPayload;
import com.tensurafragments.skill.StartingSkill;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** The starting skill choice, shown once after picking a race. It stays up until a skill is picked. */
public class SkillPickScreen extends Screen {
    private final List<String> skills;

    private SkillPickScreen(List<String> skills) {
        super(Component.translatable("tensurafragments.starting_skill.title"));
        this.skills = skills;
    }

    /** Opens it, unless another screen (like the race menu) is up; the server asks again a little later. */
    public static void open(List<String> skills) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) {
            mc.setScreen(new SkillPickScreen(skills));
        }
    }

    @Override
    protected void init() {
        int columns = 2;
        int buttonWidth = 150;
        int rows = (skills.size() + columns - 1) / columns;
        int top = height / 2 - rows * 12 + 6;
        for (int i = 0; i < skills.size(); i++) {
            String id = skills.get(i);
            String path = ResourceLocation.parse(id).getPath();
            int x = width / 2 - buttonWidth - 4 + (i % columns) * (buttonWidth + 8);
            int y = top + (i / columns) * 24;
            Button button = Button.builder(Component.translatable(StartingSkill.skillKey(path)), b -> pick(id))
                    .bounds(x + 20, y, buttonWidth - 20, 20)
                    .tooltip(Tooltip.create(Component.translatable(StartingSkill.descriptionKey(path))))
                    .build();
            addRenderableWidget(button);
        }
    }

    private void pick(String id) {
        PacketDistributor.sendToServer(new PickSkillPayload(id));
        onClose();
    }

    @Nullable
    private static ResourceLocation icon(String id) {
        return StartingSkill.CHOICES.stream().filter(choice -> choice.getId().toString().equals(id)).findFirst()
                .map(choice -> {
                    ManasSkill skill = choice.get();
                    return skill instanceof Skill tensuraSkill ? tensuraSkill.getSkillIcon() : null;
                }).orElse(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int columns = 2;
        int buttonWidth = 150;
        int rows = (skills.size() + columns - 1) / columns;
        int top = height / 2 - rows * 12 + 6;
        graphics.drawCenteredString(font, title, width / 2, top - 34, 0xFFE9C46A);
        graphics.drawCenteredString(font, Component.translatable("tensurafragments.starting_skill.subtitle"), width / 2,
                top - 20, 0xCCCCCC);
        for (int i = 0; i < skills.size(); i++) {
            ResourceLocation icon = icon(skills.get(i));
            if (icon != null) {
                int x = width / 2 - buttonWidth - 4 + (i % columns) * (buttonWidth + 8);
                int y = top + (i / columns) * 24;
                graphics.blit(icon, x, y + 1, 0, 0, 18, 18, 18, 18);
            }
        }
    }

    /** It has to be answered. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
