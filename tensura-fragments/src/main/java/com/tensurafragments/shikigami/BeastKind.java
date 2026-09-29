package com.tensurafragments.shikigami;

import com.tensurafragments.ModRegistries;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * The shapes Shikigami Control can fold paper into. Each is drawn with one of Tensura's creature models (in paper),
 * and plays differently when you see through it and take control.
 */
public enum BeastKind {
    /** Flies freely, sees in the dark; its peck marks what it hits so you can see it through walls. */
    OWL("owl", "one_eyed_owl", ModRegistries.PAPER_OWL, 2, 8, 3, 0.3, 0.55, 0, true, 1.4F, -0.81F,
            "flying", "flying", "flying", null),
    /** Fast and hard-biting; attacking with nothing in reach leaps forward. */
    HOUND("hound", "hound_dog", ModRegistries.PAPER_HOUND, 3, 20, 7, 0.35, 0.42, 0.55, false, 0.6F, 0,
            "idle", "run", "run", "bite"),
    /** Strikes twice; hold jump in the air to glide on its wings (a short stamina gauge, refilled on the ground). */
    CAT("cat", "winged_cat", ModRegistries.PAPER_CAT, 2, 14, 4, 0.33, 0.36, 0.5, false, 0.45F, 0,
            "idle", "walk", "fly", "double_strike"),
    /** Tiny, jumps very high; its horn charge hits harder the faster it's moving. */
    RABBIT("rabbit", "horned_rabbit", ModRegistries.PAPER_RABBIT, 1, 8, 3, 0.3, 0.32, 0.9, false, 0.6F, 0,
            "idle", "walk", "jump", "jump_attack");

    private final String id;
    private final String model;
    private final Supplier<? extends EntityType<PaperBeastEntity>> type;
    private final int paper;
    private final double health;
    private final double damage;
    private final double speed;
    private final double controlledSpeed;
    private final double jump;
    private final boolean flies;
    private final float renderScale;
    private final float renderYOffset;
    private final String idleAnim;
    private final String moveAnim;
    private final String airAnim;
    @Nullable
    private final String attackAnim;

    BeastKind(String id, String model, Supplier<? extends EntityType<PaperBeastEntity>> type, int paper, double health,
              double damage, double speed, double controlledSpeed, double jump, boolean flies, float renderScale,
              float renderYOffset, String idleAnim, String moveAnim, String airAnim, @Nullable String attackAnim) {
        this.id = id;
        this.model = model;
        this.type = type;
        this.paper = paper;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.controlledSpeed = controlledSpeed;
        this.jump = jump;
        this.flies = flies;
        this.renderScale = renderScale;
        this.renderYOffset = renderYOffset;
        this.idleAnim = idleAnim;
        this.moveAnim = moveAnim;
        this.airAnim = airAnim;
        this.attackAnim = attackAnim;
    }

    public String id() {
        return id;
    }

    /** Tensura's model (geo/entity/&lt;model&gt;.geo.json and its animations). */
    public String model() {
        return model;
    }

    public EntityType<PaperBeastEntity> type() {
        return type.get();
    }

    /** Sheets of paper (or leaves) it takes to fold one. */
    public int paper() {
        return paper;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    /** Movement speed attribute, for when it moves on its own. */
    public double speed() {
        return speed;
    }

    /** Blocks per tick when you steer it. */
    public double controlledSpeed() {
        return controlledSpeed;
    }

    public double jump() {
        return jump;
    }

    public boolean flies() {
        return flies;
    }

    public float renderScale() {
        return renderScale;
    }

    /** The owl model floats above its origin (it's made to perch on a shoulder); this brings it down. */
    public float renderYOffset() {
        return renderYOffset;
    }

    public String idleAnim() {
        return animation(idleAnim);
    }

    public String moveAnim() {
        return animation(moveAnim);
    }

    public String airAnim() {
        return animation(airAnim);
    }

    @Nullable
    public String attackAnim() {
        return attackAnim == null ? null : animation(attackAnim);
    }

    private String animation(String name) {
        return "animation." + model + "." + name;
    }

    public BeastKind next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static BeastKind byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }

    public static BeastKind of(EntityType<?> type) {
        for (BeastKind kind : values()) {
            if (kind.type.get() == type) {
                return kind;
            }
        }
        return HOUND;
    }
}
