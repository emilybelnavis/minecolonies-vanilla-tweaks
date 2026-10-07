package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.util.Tuple;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dev-only (source set "preview", never packaged): with {@code -Dmcvanillatweaks.preview=true}, opens every citizen
 * page with a sample citizen at the title screen, saves a screenshot of each in run/screenshots/, then quits.
 * MineColonies windows cannot be opened without a colony, so this is how the layout is checked outside the game.
 */
@EventBusSubscriber(modid = MineColoniesVanillaTweaks.MOD_ID, value = Dist.CLIENT)
public final class CitizenPreview
{
    private static final boolean ENABLED = Boolean.getBoolean("mcvanillatweaks.preview");
    private static final CitizenPage[] PAGES = {CitizenPage.OVERVIEW, CitizenPage.SKILLS, CitizenPage.REQUESTS, CitizenPage.HAPPINESS, CitizenPage.FAMILY};

    private static int index = -1;
    private static int step;
    private static boolean capture;
    /** Set once every citizen page is saved. */
    public static boolean finished;

    static
    {
        MineColoniesVanillaTweaks.LOGGER.info("Citizen preview loaded (enabled: {})", ENABLED);
    }

    private CitizenPreview()
    {
    }

    @SubscribeEvent
    public static void onScreenInit(final ScreenEvent.Init.Post event)
    {
        if (ENABLED && index < 0)
        {
            MineColoniesVanillaTweaks.LOGGER.info("Citizen preview: screen {}", event.getScreen().getClass().getName());
        }
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        // Any first screen will do (a fresh game directory starts on the accessibility onboarding screen).
        if (ENABLED && index == -1 && mc.getOverlay() == null && mc.screen != null)
        {
            MineColoniesVanillaTweaks.LOGGER.info("Citizen preview: starting");
            index = 0;
            step = -40;
        }
        if (!ENABLED || index < 0 || capture)
        {
            return;
        }
        if (index == PAGES.length)
        {
            // Last capture: a dialogue (MineColonies' interaction window).
            step++;
            if (step == 1 && !fr.mcvanillatweaks.client.PreviewFilter.wants("citizen_interaction"))
            {
                index++;
                step = 0;
                return;
            }
            if (step == 1)
            {
                mc.setScreen(new InteractionScreen(sampleCitizen(List.of(interaction(ChatPriority.IMPORTANT, "com.minecolonies.coremod.gui.chat.hometoofar",
                  "com.minecolonies.coremod.gui.chat.okay", "com.minecolonies.coremod.gui.chat.ignore", "com.minecolonies.coremod.gui.chat.remindmelater"),
                  interaction(ChatPriority.PENDING, "com.minecolonies.coremod.gui.chat.noguardnearhome", "com.minecolonies.coremod.gui.chat.okay"))), null));
            }
            else if (step == 20)
            {
                capture = true;
            }
            return;
        }
        if (index > PAGES.length)
        {
            // The town hall preview takes over, then quits.
            index = Integer.MIN_VALUE;
            finished = true;
            return;
        }
        step++;
        if (step == 1 && !fr.mcvanillatweaks.client.PreviewFilter.wants("citizen_" + PAGES[index].name().toLowerCase(Locale.ROOT)))
        {
            index++;
            step = 0;
            return;
        }
        if (step == 1)
        {
            mc.setScreen(new CitizenScreen(sampleCitizen(), PAGES[index], null));
        }
        else if (step == 20)
        {
            capture = true;
        }
    }

    @SubscribeEvent
    public static void onScreenRender(final ScreenEvent.Render.Post event)
    {
        if (!capture || !(event.getScreen() instanceof CitizenScreen || event.getScreen() instanceof InteractionScreen))
        {
            return;
        }
        capture = false;
        final Minecraft mc = Minecraft.getInstance();
        if (index == PAGES.length)
        {
            Screenshot.grab(mc.gameDirectory, "citizen_interaction.png", mc.getMainRenderTarget(),
              message -> MineColoniesVanillaTweaks.LOGGER.info("Citizen preview: {}", message.getString()));
            index++;
            step = 0;
            return;
        }
        Screenshot.grab(mc.gameDirectory, "citizen_" + PAGES[index].name().toLowerCase(Locale.ROOT) + ".png", mc.getMainRenderTarget(),
          message -> MineColoniesVanillaTweaks.LOGGER.info("Citizen preview: {}", message.getString()));
        index++;
        step = 0;
    }

    // ------------------------------------------------------------------ sample data

    private static final Map<String, Double> MODIFIERS = Map.of(
      "social", 0.8D, "saturation", 1.5D, "security", 0.5D, "homelessness", 1.0D, "unemployment", 1.0D, "health", 1.0D, "idleatjob", 1.0D);

    /** An interaction answered with MineColonies' own chat texts. */
    private static IInteractionResponseHandler interaction(final ChatPriority priority, final String inquiry, final String... answers)
    {
        final List<Component> responses = java.util.Arrays.stream(answers).map(key -> (Component) Component.translatable(key)).toList();
        return proxy(IInteractionResponseHandler.class, (method, args) -> switch (method.getName())
        {
            case "getInquiry", "getId" -> Component.translatable(inquiry);
            case "getPossibleResponses" -> responses;
            case "getPriority" -> priority;
            case "isVisible", "isPrimary", "isValid", "onClientResponseTriggered" -> true;
            default -> null;
        });
    }

    private static ICitizenDataView sampleCitizen()
    {
        return sampleCitizen(List.of());
    }

    private static ICitizenDataView sampleCitizen(final List<IInteractionResponseHandler> interactions)
    {
        final ICitizenSkillHandler skills = proxy(ICitizenSkillHandler.class, (method, args) ->
          method.getName().equals("getLevel") ? 1 + ((Skill) args[0]).ordinal() * 9 % 23 : null);
        final ICitizenHappinessHandler happiness = proxy(ICitizenHappinessHandler.class, (method, args) -> switch (method.getName())
        {
            case "getModifiers" -> List.copyOf(MODIFIERS.keySet());
            case "getModifier" -> proxy(IHappinessModifier.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> args[0];
                case "getFactor" -> MODIFIERS.get((String) args[0]);
                case "getWeight" -> 1.0D;
                default -> null;
            });
            default -> null;
        });
        return proxy(ICitizenDataView.class, (method, args) -> switch (method.getName())
        {
            case "getName" -> "Jules A. Moreau";
            case "getId" -> 1;
            case "getEntityId", "getColonyId" -> -1;
            case "isFemale", "isChild", "isPaused", "isSick", "hasBlockingInteractions", "hasPendingInteractions", "hasVisibleStatus" -> false;
            case "getHealth" -> 17.0D;
            case "getMaxHealth" -> 20.0D;
            case "getSaturation" -> 25.5D;
            case "getHappiness" -> 7.6D;
            case "getJob" -> "com.minecolonies.job.deliveryman";
            case "getJobComponent" -> Component.translatable("com.minecolonies.job.deliveryman");
            case "getWorkBuilding", "getHomeBuilding", "getPosition", "getStatusPosition" -> BlockPos.ZERO;
            case "getVisibleStatus" -> VisibleCitizenStatus.WORKING;
            case "getCitizenSkillHandler" -> skills;
            case "getHappinessHandler" -> happiness;
            case "getDisplayArmor" -> switch ((EquipmentSlot) args[0])
            {
                case HEAD -> new ItemStack(Items.IRON_HELMET);
                case CHEST -> new ItemStack(Items.LEATHER_CHESTPLATE);
                case FEET -> new ItemStack(Items.IRON_BOOTS);
                default -> ItemStack.EMPTY;
            };
            case "getPartner" -> 0;
            case "getChildren" -> List.of(2, 3, 4);
            case "getSiblings" -> List.of();
            case "getParents" -> new Tuple<>("", "");
            case "getOrderedInteractions" -> interactions;
            default -> null;
        });
    }

    private interface Handler
    {
        Object handle(Method method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final Handler handler)
    {
        return (T) Proxy.newProxyInstance(CitizenPreview.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            if (method.getName().equals("toString"))
            {
                return type.getSimpleName() + " sample";
            }
            if (method.getName().equals("hashCode"))
            {
                return System.identityHashCode(self);
            }
            if (method.getName().equals("equals"))
            {
                return self == args[0];
            }
            return handler.handle(method, args);
        });
    }
}
