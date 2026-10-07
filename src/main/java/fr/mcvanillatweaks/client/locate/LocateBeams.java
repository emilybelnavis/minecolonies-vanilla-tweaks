package fr.mcvanillatweaks.client.locate;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.CitizenInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * "Locate" beams: a coloured beacon beam drawn in the world above a citizen or a hut, so the player finds them from
 * afar, with a line per beam at the top of the screen (name, distance, direction). Purely client-side: citizens follow
 * their entity while it is loaded on the client, their last synced position otherwise. A beam goes away when the player
 * gets there, after {@link #LIFETIME_MS}, or when its button is pressed again.
 * <p>
 * Beams are widened with distance so they stay a few pixels wide, and a target past the render distance is drawn at the
 * edge of it, in its direction (fog hides anything further).
 */
public final class LocateBeams
{
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(MineColoniesVanillaTweaks.MOD_ID, "locate_beams");
    public static final int CITIZEN_COLOUR = 0xFF55DDFF;
    public static final int BUILDING_COLOUR = 0xFFFFC23F;
    /** Graves of dead citizens ({@link DeathBeams}). */
    public static final int DEATH_COLOUR = 0xFFFF3030;
    private static final long LIFETIME_MS = 5 * 60 * 1000L;
    private static final long GRAVE_LIFETIME_MS = 60 * 60 * 1000L;
    /** Horizontal distance at which the target counts as reached. */
    private static final double REACHED = 3.5D;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /**
     * One beam. {@code position} is looked up each frame (null: unknown, the citizen is gone); {@code end} is the
     * moment it expires.
     */
    private record Target(Component label, int colour, ResourceKey<Level> dimension, Function<Float, Vec3> position, long end)
    {
    }

    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();
    private static boolean failed;

    private LocateBeams()
    {
    }

    // ------------------------------------------------------------------ targets

    public static boolean isLocated(final ICitizenDataView citizen)
    {
        try
        {
            return citizen.getColony() != null && TARGETS.containsKey(key(citizen));
        }
        catch (final LinkageError | RuntimeException e)
        {
            return false;
        }
    }

    public static boolean isLocated(final IBuildingView building)
    {
        try
        {
            return building.getColony() != null && TARGETS.containsKey(key(building));
        }
        catch (final LinkageError | RuntimeException e)
        {
            return false;
        }
    }

    /**
     * Adds a beam above the citizen, or removes it if there is one. Returns true when a beam was added, so the caller
     * can close its screen and let the player see it.
     */
    public static boolean toggle(final ICitizenDataView citizen)
    {
        final Target target = target(citizen);
        return target != null && toggle(key(citizen), target);
    }

    /**
     * The citizens of a hut: its workers (every assignment module), or a house's residents. The hut screen's locate
     * button beams them: the player opening it usually stands at the hut already.
     */
    public static List<ICitizenDataView> occupants(final IBuildingView building)
    {
        final List<ICitizenDataView> list = new ArrayList<>();
        final IColonyView colony = building.getColony();
        if (colony == null)
        {
            return list;
        }
        try
        {
            final List<Integer> ids = new ArrayList<>();
            if (building instanceof LivingBuildingView living)
            {
                ids.addAll(living.getResidents());
            }
            else
            {
                for (final IAssignmentModuleView module : building.getModuleViews(IAssignmentModuleView.class))
                {
                    ids.addAll(module.getAssignedCitizens());
                }
            }
            for (final int id : ids)
            {
                final ICitizenDataView citizen = colony.getCitizen(id);
                if (citizen != null && !list.contains(citizen))
                {
                    list.add(citizen);
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("Hut citizens unavailable", e);
        }
        return list;
    }

    /** Whether one of these citizens has a beam: their button then takes all of them away. */
    public static boolean anyLocated(final List<ICitizenDataView> citizens)
    {
        return citizens.stream().anyMatch(LocateBeams::isLocated);
    }

    /** {@link #toggle(ICitizenDataView)} for several citizens at once: adds a beam to each, or removes them all. */
    public static boolean toggle(final List<ICitizenDataView> citizens)
    {
        if (citizens.size() == 1)
        {
            return toggle(citizens.get(0));
        }
        final List<String> names = new ArrayList<>();
        if (anyLocated(citizens))
        {
            for (final ICitizenDataView citizen : citizens)
            {
                if (TARGETS.remove(key(citizen)) != null)
                {
                    names.add(citizen.getName());
                }
            }
            message(Component.translatable("mcvanillatweaks.locate.removed", String.join(", ", names)));
            return false;
        }
        for (final ICitizenDataView citizen : citizens)
        {
            final Target target = target(citizen);
            if (target != null && target.position().apply(1.0F) != null)
            {
                TARGETS.put(key(citizen), target);
                names.add(citizen.getName());
            }
        }
        if (names.isEmpty())
        {
            message(Component.translatable("mcvanillatweaks.locate.unknown",
              String.join(", ", citizens.stream().map(ICitizenDataView::getName).toList())).withStyle(ChatFormatting.RED));
            return false;
        }
        message(Component.translatable("mcvanillatweaks.locate.added_many", String.join(", ", names)));
        return true;
    }

    @Nullable
    private static Target target(final ICitizenDataView citizen)
    {
        final IColonyView colony = citizen.getColony();
        if (colony == null)
        {
            return null;
        }
        final int colonyId = colony.getID();
        final ResourceKey<Level> dimension = colony.getDimension();
        final int citizenId = citizen.getId();
        return new Target(Component.literal(citizen.getName()), CITIZEN_COLOUR, dimension, partialTick -> {
            final IColonyView current = IColonyManager.getInstance().getColonyView(colonyId, dimension);
            final ICitizenDataView data = current == null ? null : current.getCitizen(citizenId);
            return data == null ? null : position(data, partialTick);
        }, System.currentTimeMillis() + LIFETIME_MS);
    }

    /** Where the citizen is: its entity when loaded on the client, its last synced position otherwise (null if unknown). */
    @Nullable
    public static Vec3 position(final ICitizenDataView citizen, final float partialTick)
    {
        final LivingEntity entity = CitizenInfo.entity(citizen);
        if (entity != null)
        {
            return entity.getPosition(partialTick);
        }
        final BlockPos pos = citizen.getPosition();
        return pos == null || pos.equals(BlockPos.ZERO) ? null : Vec3.atBottomCenterOf(pos);
    }

    /** Same as {@link #toggle(ICitizenDataView)} for a hut, the beam rising from its hut block. */
    public static boolean toggle(final IBuildingView building)
    {
        final IColonyView colony = building.getColony();
        if (colony == null)
        {
            return false;
        }
        final Vec3 pos = Vec3.atBottomCenterOf(building.getID());
        return toggle(key(building), new Target(name(building), BUILDING_COLOUR, colony.getDimension(), partialTick -> pos,
          System.currentTimeMillis() + LIFETIME_MS));
    }

    /**
     * A red beam above a grave, kept while the colony still lists the grave (it goes when the grave is collected or dug
     * up), for {@link #GRAVE_LIFETIME_MS} at most. {@code message} is shown in the action bar when not null.
     */
    static void addGrave(final IColonyView colony, final BlockPos grave, final Component label, @Nullable final Component message)
    {
        final String key = "grave/" + colony.getDimension() + "/" + colony.getID() + "/" + grave.asLong();
        if (TARGETS.containsKey(key))
        {
            return;
        }
        final int colonyId = colony.getID();
        final ResourceKey<Level> dimension = colony.getDimension();
        final Vec3 pos = Vec3.atBottomCenterOf(grave);
        TARGETS.put(key, new Target(label, DEATH_COLOUR, dimension, partialTick -> {
            final IColonyView current = IColonyManager.getInstance().getColonyView(colonyId, dimension);
            return current != null && current.getGraveManager() != null && current.getGraveManager().getGraves().containsKey(grave) ? pos : null;
        }, System.currentTimeMillis() + GRAVE_LIFETIME_MS));
        if (message != null)
        {
            message(message);
        }
    }

    private static boolean toggle(final String key, final Target target)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (TARGETS.remove(key) != null)
        {
            message(Component.translatable("mcvanillatweaks.locate.removed", target.label()));
            return false;
        }
        final Vec3 pos = target.position().apply(1.0F);
        if (pos == null)
        {
            message(Component.translatable("mcvanillatweaks.locate.unknown", target.label()).withStyle(ChatFormatting.RED));
            return false;
        }
        TARGETS.put(key, target);
        if (mc.player != null && mc.level != null && mc.level.dimension() == target.dimension())
        {
            message(Component.translatable("mcvanillatweaks.locate.added", target.label(), (int) horizontal(mc.player.position(), pos)));
        }
        else
        {
            message(Component.translatable("mcvanillatweaks.locate.added_elsewhere", target.label()));
        }
        return true;
    }

    /** The hut's custom name, or its type's name. */
    public static Component name(final IBuildingView building)
    {
        final String custom = building.getCustomName();
        return custom == null || custom.isEmpty() ? Component.translatable(building.getBuildingDisplayName()) : Component.literal(custom);
    }

    private static String key(final ICitizenDataView citizen)
    {
        return "citizen/" + citizen.getColony().getDimension() + "/" + citizen.getColony().getID() + "/" + citizen.getId();
    }

    private static String key(final IBuildingView building)
    {
        return "building/" + building.getColony().getDimension() + "/" + building.getID().asLong();
    }

    private static void message(final Component text)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
        {
            mc.player.displayClientMessage(text, true);
        }
    }

    private static double horizontal(final Vec3 from, final Vec3 to)
    {
        return Math.sqrt(Mth.square(to.x - from.x) + Mth.square(to.z - from.z));
    }

    // ------------------------------------------------------------------ events

    /** Drops expired beams, gone citizens and reached targets. */
    public static void onClientTick(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (TARGETS.isEmpty() || mc.player == null || mc.level == null)
        {
            return;
        }
        final long now = System.currentTimeMillis();
        try
        {
            for (final Iterator<Target> it = TARGETS.values().iterator(); it.hasNext(); )
            {
                final Target target = it.next();
                final Vec3 pos = target.position().apply(1.0F);
                if (now > target.end() || pos == null)
                {
                    it.remove();
                }
                else if (mc.level.dimension() == target.dimension() && horizontal(mc.player.position(), pos) < REACHED
                           && Math.abs(mc.player.getY() - pos.y) < 6)
                {
                    it.remove();
                    message(Component.translatable("mcvanillatweaks.locate.reached", target.label()).withStyle(ChatFormatting.GREEN));
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        TARGETS.clear();
    }

    public static void onRenderLevel(final RenderLevelStageEvent event)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || TARGETS.isEmpty() || mc.level == null || failed)
        {
            return;
        }
        final Vec3 camera = event.getCamera().getPosition();
        final float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final double maxDistance = mc.options.getEffectiveRenderDistance() * 16 * 0.8D;
        final PoseStack pose = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // The pose stack is relative to the camera; the model view matrix holds its rotation.
        final Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try
        {
            for (final Target target : TARGETS.values())
            {
                final Vec3 pos = target.position().apply(partialTick);
                if (pos == null || mc.level.dimension() != target.dimension())
                {
                    continue;
                }
                double dx = pos.x - camera.x;
                double dz = pos.z - camera.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > maxDistance)
                {
                    dx *= maxDistance / distance;
                    dz *= maxDistance / distance;
                    distance = maxDistance;
                }
                final float radius = (float) Math.max(0.2D, distance / 160.0D);
                pose.pushPose();
                // renderBeaconBeam centres the beam on the block: +0.5 on x and z.
                pose.translate(dx - 0.5D, pos.y - camera.y, dz - 0.5D);
                BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, mc.level.getGameTime(), 0,
                  BeaconRenderer.MAX_RENDER_Y, target.colour(), radius, radius * 1.25F);
                pose.popPose();
            }
            buffers.endBatch();
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
        finally
        {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void fail(final Throwable e)
    {
        if (!failed)
        {
            failed = true;
            TARGETS.clear();
            MineColoniesVanillaTweaks.LOGGER.warn("Locate beams unavailable", e);
        }
    }

    // ------------------------------------------------------------------ screen lines

    public static void registerLayer(final RegisterGuiLayersEvent event)
    {
        event.registerAboveAll(LAYER_ID, new Layer());
    }

    /** One line per beam at the top of the screen: direction arrow, name, distance. */
    private static final class Layer implements LayeredDraw.Layer
    {
        @Override
        public void render(final GuiGraphics g, final DeltaTracker deltaTracker)
        {
            final Minecraft mc = Minecraft.getInstance();
            if (TARGETS.isEmpty() || mc.player == null || mc.level == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen())
            {
                return;
            }
            final List<Component> lines = new ArrayList<>();
            final List<Integer> colours = new ArrayList<>();
            try
            {
                for (final Target target : TARGETS.values())
                {
                    final Vec3 pos = target.position().apply(deltaTracker.getGameTimeDeltaPartialTick(false));
                    if (pos == null)
                    {
                        continue;
                    }
                    if (mc.level.dimension() != target.dimension())
                    {
                        lines.add(Component.translatable("mcvanillatweaks.locate.line_elsewhere", target.label()));
                    }
                    else
                    {
                        lines.add(Component.translatable("mcvanillatweaks.locate.line", arrow(mc, pos), target.label(),
                          (int) horizontal(mc.player.position(), pos)));
                    }
                    colours.add(target.colour());
                }
            }
            catch (final LinkageError | RuntimeException e)
            {
                fail(e);
                return;
            }
            int y = 4;
            for (int i = 0; i < lines.size(); i++)
            {
                final int w = mc.font.width(lines.get(i));
                final int x = (g.guiWidth() - w) / 2;
                g.fill(x - 3, y - 2, x + w + 3, y + 10, 0x80000000);
                g.drawString(mc.font, lines.get(i), x, y, colours.get(i), true);
                y += 13;
            }
        }

        /** Direction of the target relative to where the player looks. */
        private static String arrow(final Minecraft mc, final Vec3 pos)
        {
            final double angle = Math.toDegrees(Math.atan2(pos.z - mc.player.getZ(), pos.x - mc.player.getX())) - 90.0D;
            final double relative = Mth.wrapDegrees(angle - mc.player.getYRot());
            return ARROWS[Math.floorMod((int) Math.round(relative / 45.0D), 8)];
        }
    }
}
