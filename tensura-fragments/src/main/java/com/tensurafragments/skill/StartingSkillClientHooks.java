package com.tensurafragments.skill;

import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Opens the starting skill screen on the client, without the common code touching client classes. */
public final class StartingSkillClientHooks {
    private StartingSkillClientHooks() {
    }

    public static void open(List<String> skills) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.SkillPickScreen.open(skills);
        }
    }
}
