package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.quests.FinishedQuest;
import com.minecolonies.api.quests.IQuestInstance;
import com.minecolonies.api.quests.IQuestManager;
import com.minecolonies.api.quests.IQuestObjectiveTemplate;
import com.minecolonies.api.quests.IQuestTemplate;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;

/** Preview of {@link QuestLogScreen}: a proxy quest manager with two quests in progress, one available and two finished. */
public final class QuestLogPreview
{
    private static IQuestManager manager;

    private QuestLogPreview()
    {
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final BiFunction<String, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(QuestLogPreview.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            switch (method.getName())
            {
                case "toString":
                    return type.getSimpleName() + " sample";
                case "hashCode":
                    return System.identityHashCode(self);
                case "equals":
                    return self == args[0];
                default:
                    break;
            }
            final Object result = handler.apply(method.getName(), args == null ? new Object[0] : args);
            if (result == null && method.getReturnType() == int.class)
            {
                return 0;
            }
            if (result == null && method.getReturnType() == boolean.class)
            {
                return false;
            }
            return result;
        });
    }

    private static IQuestTemplate template(final String name, final int objectives, final int max, final String progress)
    {
        final IQuestObjectiveTemplate objective = proxy(IQuestObjectiveTemplate.class, (m, a) -> m.equals("getProgressText")
                                                                                               ? Component.literal(progress).withStyle((Style) a[1]) : null);
        return proxy(IQuestTemplate.class, (m, a) -> switch (m)
        {
            case "getName" -> Component.literal(name);
            case "getObjective" -> objective;
            case "getObjectiveCount" -> objectives;
            case "getMaxOccurrence" -> max;
            default -> null;
        });
    }

    private static IQuestInstance quest(final String id, final int giver, final int target, final int index)
    {
        final ResourceLocation key = ResourceLocation.fromNamespaceAndPath("mcvanillatweaks", id);
        return proxy(IQuestInstance.class, (m, a) -> switch (m)
        {
            case "getId" -> key;
            case "getQuestGiverId" -> giver;
            case "getQuestTarget" -> target;
            case "getObjectiveIndex" -> index;
            default -> null;
        });
    }

    public static IQuestManager manager()
    {
        if (manager != null)
        {
            return manager;
        }
        final IQuestTemplate bread = template("Le pain du matin", 3, 1, "Apporte 16 pains à Alice (4/16)");
        final IQuestTemplate wolf = template("Un loup dans la bergerie", 2, 1, "Tue le loup près des champs (0/1)");
        final IQuestTemplate guide = template("Le guide du bâtisseur", 4, 1, "Parle à Félix");
        final IQuestTemplate fish = template("Pêche miraculeuse", 2, 5, "");
        try
        {
            IQuestManager.GLOBAL_SERVER_QUESTS.put(ResourceLocation.fromNamespaceAndPath("mcvanillatweaks", "bread"), bread);
            IQuestManager.GLOBAL_SERVER_QUESTS.put(ResourceLocation.fromNamespaceAndPath("mcvanillatweaks", "wolf"), wolf);
            IQuestManager.GLOBAL_SERVER_QUESTS.put(ResourceLocation.fromNamespaceAndPath("mcvanillatweaks", "guide"), guide);
        }
        catch (final RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Quest log preview: quest templates not registered", e);
        }
        final List<IQuestInstance> inProgress = List.of(quest("bread", 1, 1, 1), quest("wolf", 6, 6, 0));
        final List<IQuestInstance> available = List.of(quest("guide", 6, 6, 0));
        final List<FinishedQuest> finished = List.of(new FinishedQuest(fish, 2), new FinishedQuest(template("Bienvenue au village", 1, 1, ""), 1));
        manager = proxy(IQuestManager.class, (m, a) -> switch (m)
        {
            case "getInProgressQuests" -> inProgress;
            case "getAvailableQuests" -> available;
            case "getFinishedQuests" -> finished;
            default -> null;
        });
        return manager;
    }

    public static Screen screen(final IColonyView colony, final String page)
    {
        return new QuestLogScreen(colony, QuestLogScreen.Page.valueOf(page));
    }
}
