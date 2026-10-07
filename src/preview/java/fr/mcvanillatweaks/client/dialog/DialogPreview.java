package fr.mcvanillatweaks.client.dialog;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.colony.connections.IColonyConnectionManager;
import fr.mcvanillatweaks.client.hut.ConnectionsScreen;
import fr.mcvanillatweaks.client.townhall.BannerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.TreeMap;

/**
 * Dev preview of the dialogs and other small screens (captured by HutPreview): MineColonies' windows can't be built
 * without a server, so the specs are rebuilt here with the same lang keys and sample values.
 */
public final class DialogPreview
{
    private DialogPreview()
    {
    }

    public static Screen covenant()
    {
        return new DialogScreen(new DialogScreen.Spec(new ItemStack(Items.WRITTEN_BOOK), Component.translatable("com.minecolonies.core.settlementcovenant.title"),
          List.of(DialogScreen.Line.of(Component.translatable("com.minecolonies.core.settlementcovenant1", 13)),
            DialogScreen.Line.of(Component.translatable("com.minecolonies.core.settlementcovenant2")),
            DialogScreen.Line.of(Component.translatable("com.minecolonies.core.settlementcovenant3.hasclose", Component.literal("Ravenhold").withStyle(ChatFormatting.RED),
              Component.literal("640").withStyle(ChatFormatting.RED))),
            DialogScreen.Line.soft(Component.translatable("com.minecolonies.core.settlementcovenant4"))),
          new DialogScreen.Input("Colonie de Dev", 32, 1),
          List.of(new DialogScreen.Action(Component.translatable("gui.cancel"), s -> { }),
            new DialogScreen.Action(Component.translatable("com.minecolonies.coremod.gui.colony.create"), s -> { })),
          320, null));
    }

    public static Screen oldColony()
    {
        return new DialogScreen(new DialogScreen.Spec(new ItemStack(ModBlocks.blockHutTownHall), Component.translatable("mcvanillatweaks.dialog.old_colony"),
          List.of(DialogScreen.Line.of(Component.translatable("com.minecolonies.core.gui.colony.abandon.warning", 120, 64, -340,
            Component.literal("Exempleville").withStyle(ChatFormatting.DARK_RED)))),
          List.of(new DialogScreen.Action(Component.translatable("com.minecolonies.core.founding.pickup"), s -> { }),
            new DialogScreen.Action(Component.translatable("com.minecolonies.core.founding.delete"), s -> { }),
            new DialogScreen.Action(Component.translatable("com.minecolonies.core.founding.abandon"), s -> { }),
            new DialogScreen.Action(Component.translatable("gui.cancel"), s -> { })),
          320, null));
    }

    public static Screen guide()
    {
        return new DialogScreen(new DialogScreen.Spec(new ItemStack(ModBlocks.blockHutBuilder), Component.translatable("mcvanillatweaks.dialog.guide"),
          List.of(DialogScreen.Line.icon(new ItemStack(Items.KNOWLEDGE_BOOK), Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.questionmark")),
            DialogScreen.Line.icon(new ItemStack(Items.PAPER), Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.arrow")),
            DialogScreen.Line.icon(new ItemStack(Items.BARREL), Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.chest"))),
          List.of(new DialogScreen.Action(Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.confirm"), s -> { })),
          260, null));
    }

    private static <T> T proxy(final Class<T> type, final java.util.function.BiFunction<String, Object[], Object> answer)
    {
        return type.cast(Proxy.newProxyInstance(DialogPreview.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            final Object value = answer.apply(method.getName(), args);
            if (value != null)
            {
                return value;
            }
            final Class<?> r = method.getReturnType();
            if (r == boolean.class)
            {
                return false;
            }
            if (r == int.class || r == long.class || r == double.class || r == float.class || r == short.class || r == byte.class)
            {
                return r == int.class ? 0 : r == long.class ? 0L : r == double.class ? 0.0D : r == float.class ? 0.0F : r == short.class ? (short) 0 : (byte) 0;
            }
            return null;
        }));
    }

    /** What an outsider sees at a gatehouse: two direct connections (an ally, a neutral colony) and an indirect one. */
    public static Screen connections()
    {
        final TreeMap<Integer, ColonyConnection> direct = new TreeMap<>();
        direct.put(2, new ColonyConnection(2, "Ravenhold", new BlockPos(640, 70, -120), DiplomacyStatus.ALLIES));
        direct.put(3, new ColonyConnection(3, "Pierrefonds", new BlockPos(-300, 64, 800), DiplomacyStatus.NEUTRAL));
        final TreeMap<Integer, ColonyConnection> indirect = new TreeMap<>();
        indirect.put(4, new ColonyConnection(4, "Brumeval", new BlockPos(1500, 64, 900), DiplomacyStatus.HOSTILE));
        final IColonyConnectionManager manager = proxy(IColonyConnectionManager.class, (m, a) -> switch (m)
        {
            case "getDirectlyConnectedColonies" -> direct;
            case "getIndirectlyConnectedColonies" -> indirect;
            default -> null;
        });
        final IColonyView colony = proxy(IColonyView.class, (m, a) -> switch (m)
        {
            case "getConnectionManager" -> manager;
            case "getCenter" -> new BlockPos(100, 64, 100);
            case "getName" -> "Exempleville";
            case "getID" -> 1;
            default -> null;
        });
        final IBuildingView gatehouse = proxy(IBuildingView.class, (m, a) -> switch (m)
        {
            case "getColony" -> colony;
            case "getPosition", "getID" -> new BlockPos(120, 64, 90);
            case "getBuildingType" -> ModBuildings.gateHouse.get();
            case "getBuildingLevel" -> 2;
            default -> null;
        });
        return new ConnectionsScreen(gatehouse);
    }

    /** A decoration controller with a construction under way, its buttons as MineColonies shows them once the blueprint is read. */
    public static Screen decorationController()
    {
        return new DialogScreen(new DialogScreen.Spec(new ItemStack(ModBlocks.blockDecorationPlaceholder), Component.translatable("mcvanillatweaks.decoration.title"),
          List.of(DialogScreen.Line.of(Component.literal("fountain2")),
            DialogScreen.Line.soft(Component.translatable("mcvanillatweaks.decoration.where", "Medieval Oak", "decorations / fountains")),
            DialogScreen.Line.soft(Component.translatable("mcvanillatweaks.decoration.level", 2)),
            DialogScreen.Line.icon(new ItemStack(Items.WRITABLE_BOOK), Component.translatable("mcvanillatweaks.decoration.building"))),
          List.of(new DialogScreen.Action(Component.translatable("gui.cancel"), s -> { }),
            new DialogScreen.Action(Component.translatable("com.minecolonies.coremod.gui.workerhuts.repair"), s -> { }),
            new DialogScreen.Action(Component.translatable("com.minecolonies.coremod.gui.workerhuts.cancelbuild"), s -> { })),
          280, null));
    }

    public static Screen decorationBuild()
    {
        DecorationBuildScreen.previewBuilders = List.of("N'importe quel bâtisseur", "Bob Bâtisseur", "Alice Maçonne");
        DecorationBuildScreen.previewResources = List.of(new ItemStack(Items.STONE_BRICKS, 96), new ItemStack(Items.OAK_LOG, 24),
          new ItemStack(Items.OAK_STAIRS, 18), new ItemStack(Items.WATER_BUCKET, 4), new ItemStack(Items.LANTERN, 2), new ItemStack(Items.CHISELED_STONE_BRICKS, 1));
        return new DecorationBuildScreen(null);
    }

    /** A hut inventory of 4 rows, drawn on a vanilla chest menu (MineColonies' needs a world). */
    public static Screen chest()
    {
        try
        {
            final java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            final net.minecraft.world.entity.player.Player player = (net.minecraft.world.entity.player.Player) unsafe.allocateInstance(
              net.minecraft.client.player.RemotePlayer.class);
            final net.minecraft.world.entity.player.Inventory inventory = new net.minecraft.world.entity.player.Inventory(player);
            final net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(36);
            container.setItem(0, new ItemStack(Items.BREAD, 12));
            container.setItem(3, new ItemStack(Items.IRON_PICKAXE));
            container.setItem(10, new ItemStack(Items.OAK_LOG, 64));
            final var menu = new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x4, 0, inventory, container, 4);
            final ChestLikeScreen<?> chest = new ChestLikeScreen<>(menu, inventory, Component.literal("Ferme niveau 2"));
            return new Screen(Component.literal("chest preview"))
            {
                @Override
                protected void init()
                {
                    chest.init(minecraft, width, height);
                }

                @Override
                public void render(final net.minecraft.client.gui.GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
                {
                    chest.render(g, mouseX, mouseY, partialTick);
                }
            };
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Structurize's replace picker (its window can be built without a server), with a choice made. */
    public static Screen selectRes()
    {
        try
        {
            final List<ItemStack> all = new java.util.ArrayList<>();
            for (final var item : List.of(Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
              Items.COBBLESTONE, Items.STONE_BRICKS, Items.BRICKS, Items.GLASS, Items.WHITE_WOOL, Items.SAND, Items.GRAVEL, Items.DIRT, Items.GRASS_BLOCK,
              Items.OAK_LOG, Items.DEEPSLATE, Items.TUFF, Items.CALCITE, Items.AIR))
            {
                all.add(new ItemStack(item));
            }
            final var window = new com.ldtteam.structurize.client.gui.WindowSelectRes(null, Component.translatable("com.ldtteam.structurize.gui.replaceblock.info"),
              new ItemStack(Items.COBBLESTONE), all, (stack, count) -> { }, true, Component.translatable("com.ldtteam.structurize.gui.scan.replace.pct"));
            return new fr.mcvanillatweaks.client.hut.SelectResScreen(window);
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static Screen undoRedo()
    {
        com.ldtteam.structurize.client.gui.WindowUndoRedo.lastOperations = new java.util.ArrayList<>(List.of(
          new net.minecraft.util.Tuple<>("Scan replace stone -> granite", 3), new net.minecraft.util.Tuple<>("Shape cube 5x5x5", 2),
          new net.minecraft.util.Tuple<>("undo Paste house1", 1)));
        return new fr.mcvanillatweaks.client.buildtool.UndoRedoScreen();
    }

    /** The flag editor with a red and white flag of two patterns, at the second layer. */
    public static Screen banner()
    {
        final HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        final HolderLookup.RegistryLookup<BannerPattern> registry = lookup.lookupOrThrow(Registries.BANNER_PATTERN);
        final Holder<BannerPattern> base = registry.getOrThrow(BannerPatterns.BASE);
        final BannerPatternLayers flag = new BannerPatternLayers.Builder().add(base, DyeColor.RED)
                                           .add(registry.getOrThrow(BannerPatterns.CROSS), DyeColor.WHITE)
                                           .add(registry.getOrThrow(BannerPatterns.BORDER), DyeColor.YELLOW).build();
        final IColonyView colony = proxy(IColonyView.class, (m, a) -> "getColonyFlag".equals(m) ? flag : null);
        final List<Holder<BannerPattern>> patterns = registry.listElements().filter(h -> !h.is(BannerPatterns.BASE)).map(h -> (Holder<BannerPattern>) h).toList();
        BannerScreen.previewActive = 2;
        return new BannerScreen(colony, patterns, base, () -> { });
    }
}
