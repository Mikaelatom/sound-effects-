package com.mikaelatom.tensuragacha.shop;

import com.mikaelatom.tensuragacha.registry.ModAttachments;
import com.mikaelatom.tensuragacha.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Soul Market screen. It's a plain 6-row chest screen driven entirely by the server,
 * so it needs no client-side code: the top 5 rows are items for sale, the bottom row is controls.
 * <ul>
 *   <li>Left-click an item: buy 1. Right-click: buy 8. Shift-click: buy a full stack.</li>
 *   <li>Shift-click an item in your own inventory: sell that stack.</li>
 * </ul>
 */
public class ShopMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_FILTER = 46;
    private static final int SLOT_BALANCE = 49;
    private static final int SLOT_PAGE = 51;
    private static final int SLOT_NEXT = 53;
    private static final int SHOP_SLOTS = ROWS * 9;
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);

    private final ServerPlayer player;
    private final SimpleContainer display;
    private final String search;
    private final List<String> filters;
    private int filterIndex;
    private int page;
    private List<Item> view = List.of();

    public static void open(ServerPlayer player, String search) {
        if (ItemValues.shopItems().isEmpty()) ItemValues.recompute(player.server);
        String cleanSearch = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        MutableComponent title = Component.translatable("container.tensuragacha.soul_market");
        if (cleanSearch != null) title.append(Component.literal(" - \"" + cleanSearch + "\""));
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShopMenu(id, inventory, player, cleanSearch), title));
    }

    private ShopMenu(int id, Inventory inventory, ServerPlayer player, String search) {
        this(id, inventory, player, search, new SimpleContainer(SHOP_SLOTS));
    }

    private ShopMenu(int id, Inventory inventory, ServerPlayer player, String search, SimpleContainer display) {
        super(MenuType.GENERIC_9x6, id, inventory, display, ROWS);
        this.player = player;
        this.display = display;
        this.search = search;
        this.filters = buildFilters();
        refresh();
    }

    // ---- Filtering and paging ----

    private static List<String> buildFilters() {
        Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add("all");
        namespaces.add("tensuragacha");
        namespaces.add("tensura");
        namespaces.add("minecraft");
        for (Item item : ItemValues.shopItems()) namespaces.add(BuiltInRegistries.ITEM.getKey(item).getNamespace());
        List<String> present = new ArrayList<>();
        for (String namespace : namespaces) {
            if (namespace.equals("all") || ItemValues.shopItems().stream()
                    .anyMatch(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(namespace))) {
                present.add(namespace);
            }
        }
        return present;
    }

    private void rebuildView() {
        String namespace = filters.get(filterIndex);
        List<Item> items = new ArrayList<>();
        for (Item item : ItemValues.shopItems()) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!namespace.equals("all") && !id.getNamespace().equals(namespace)) continue;
            if (search != null && !id.getPath().contains(search)
                    && !item.getDescription().getString().toLowerCase(Locale.ROOT).contains(search)) continue;
            items.add(item);
        }
        view = items;
    }

    private int pageCount() {
        return Math.max(1, (view.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void refresh() {
        rebuildView();
        page = Math.max(0, Math.min(page, pageCount() - 1));

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            display.setItem(i, index < view.size() ? forSale(view.get(index)) : ItemStack.EMPTY);
        }

        ItemStack filler = button(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "));
        for (int i = PAGE_SIZE; i < SHOP_SLOTS; i++) display.setItem(i, filler.copy());

        display.setItem(SLOT_PREV, page > 0
                ? button(Items.ARROW, Component.translatable("tensuragacha.shop.prev"))
                : filler.copy());
        display.setItem(SLOT_NEXT, page < pageCount() - 1
                ? button(Items.SPECTRAL_ARROW, Component.translatable("tensuragacha.shop.next"))
                : filler.copy());

        String namespace = filters.get(filterIndex);
        display.setItem(SLOT_FILTER, button(Items.HOPPER,
                Component.translatable("tensuragacha.shop.filter", namespace.equals("all")
                        ? Component.translatable("tensuragacha.shop.filter.all") : Component.literal(namespace)),
                Component.translatable("tensuragacha.shop.filter.hint").withStyle(ChatFormatting.GRAY)));

        display.setItem(SLOT_BALANCE, button(Items.SUNFLOWER,
                Component.translatable("tensuragacha.shop.balance", coins(balance())).withStyle(ChatFormatting.GOLD),
                Component.translatable("tensuragacha.shop.help.buy").withStyle(ChatFormatting.GRAY),
                Component.translatable("tensuragacha.shop.help.sell").withStyle(ChatFormatting.GRAY),
                Component.translatable("tensuragacha.shop.help.search").withStyle(ChatFormatting.GRAY)));

        display.setItem(SLOT_PAGE, button(Items.PAPER,
                Component.translatable("tensuragacha.shop.page", page + 1, pageCount()),
                Component.translatable("tensuragacha.shop.count", view.size()).withStyle(ChatFormatting.GRAY)));

        broadcastChanges();
    }

    private ItemStack forSale(Item item) {
        ItemStack stack = new ItemStack(item);
        long price = ItemValues.buyPrice(item);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("tensuragacha.shop.price", coins(price)).withStyle(ChatFormatting.GOLD));
        if (stack.getMaxStackSize() > 1) {
            lore.add(Component.translatable("tensuragacha.shop.price_stack", stack.getMaxStackSize(),
                    coins(price * stack.getMaxStackSize())).withStyle(ChatFormatting.YELLOW));
        }
        lore.add(Component.translatable("tensuragacha.shop.sells_for",
                coins(ItemValues.sellPrice(new ItemStack(item)))).withStyle(ChatFormatting.GRAY));
        lore.add(Component.translatable("tensuragacha.shop.help.buy").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack button(Item item, Component name, Component... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(style -> style.withItalic(false)));
        if (lore.length > 0) stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        return stack;
    }

    // ---- Clicks ----

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
        if (slotId >= 0 && slotId < SHOP_SLOTS) {
            handleShopClick(slotId, button, clickType);
            sendAllDataToRemote();
            return;
        }
        if (slotId >= SHOP_SLOTS && clickType == ClickType.QUICK_MOVE) {
            sell(slots.get(slotId));
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, clickType, clicker);
    }

    private void handleShopClick(int slotId, int button, ClickType clickType) {
        switch (slotId) {
            case SLOT_PREV -> {
                if (page > 0) page--;
                refresh();
            }
            case SLOT_NEXT -> {
                if (page < pageCount() - 1) page++;
                refresh();
            }
            case SLOT_FILTER -> {
                filterIndex = Math.floorMod(filterIndex + (button == 1 ? -1 : 1), filters.size());
                page = 0;
                refresh();
            }
            default -> {
                if (slotId >= PAGE_SIZE) return;
                int index = page * PAGE_SIZE + slotId;
                if (index >= view.size()) return;
                if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE) return;
                Item item = view.get(index);
                int max = new ItemStack(item).getMaxStackSize();
                int amount = clickType == ClickType.QUICK_MOVE ? max : button == 1 ? Math.min(8, max) : 1;
                buy(item, amount);
            }
        }
    }

    private void buy(Item item, int amount) {
        long price = ItemValues.buyPrice(item);
        if (price <= 0) return;
        long total = price * amount;
        long balance = balance();
        if (balance < total) {
            player.displayClientMessage(Component.translatable("tensuragacha.shop.too_poor", coins(total), coins(balance))
                    .withStyle(ChatFormatting.RED), true);
            player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
            return;
        }
        setBalance(balance - total);
        ItemStack bought = new ItemStack(item, amount);
        if (!player.getInventory().add(bought) && !bought.isEmpty()) player.drop(bought, false);
        player.displayClientMessage(Component.translatable("tensuragacha.shop.bought", amount,
                item.getDescription(), coins(total)).withStyle(ChatFormatting.GREEN), true);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        refresh();
    }

    private void sell(Slot slot) {
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !slot.mayPickup(player)) return;
        long earned = ItemValues.sellPrice(stack);
        if (earned <= 0) {
            player.displayClientMessage(Component.translatable("tensuragacha.shop.cant_sell").withStyle(ChatFormatting.RED), true);
            return;
        }
        int count = stack.getCount();
        Component name = stack.getHoverName();
        slot.set(ItemStack.EMPTY);
        setBalance(balance() + earned);
        player.displayClientMessage(Component.translatable("tensuragacha.shop.sold", count, name, coins(earned))
                .withStyle(ChatFormatting.GOLD), true);
        player.playNotifySound(ModSounds.SQUISH_POP.get(), SoundSource.PLAYERS, 0.8F, 1.3F);
        refresh();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != display && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container != display && super.canDragTo(slot);
    }

    // ---- Coins ----

    private long balance() {
        return player.getData(ModAttachments.SOUL_COINS);
    }

    private void setBalance(long value) {
        player.setData(ModAttachments.SOUL_COINS, value);
    }

    static Component coins(long amount) {
        return Component.translatable("tensuragacha.shop.coins", NUMBERS.format(amount));
    }
}
