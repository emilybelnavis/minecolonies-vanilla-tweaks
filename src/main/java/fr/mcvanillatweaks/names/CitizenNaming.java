package fr.mcvanillatweaks.names;

import com.minecolonies.api.colony.CitizenNameFile;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.IColony;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Server side, called by {@code CitizenDataMixin} where MineColonies draws a citizen's name: new citizens and
 * visitors ({@code initForNewCivilian}, the founding colonists, the spawn command) and children. The colony owner's
 * enabled lists ({@link NamePools}) are used; with none, or on any error, MineColonies names the citizen itself.
 * MineColonies stores the name as usual, so every screen, message and grave shows it.
 */
public final class CitizenNaming
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean failed;

    private CitizenNaming()
    {
    }

    /** @return the name to give, or null to let MineColonies draw it. */
    @Nullable
    public static String forNewCivilian(final Random random, final boolean female, final IColony colony, final CitizenNameFile file)
    {
        try
        {
            final List<NameList> lists = lists(colony);
            if (lists.isEmpty())
            {
                return null;
            }
            final List<String> fallbackFirst = file == null ? List.of() : female ? file.femalefirstNames : file.maleFirstNames;
            final List<String> fallbackSurnames = file == null ? List.of() : file.surnames;
            return NameGenerator.newName(random, female, lists, fallbackFirst, fallbackSurnames, taken(colony));
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return null;
        }
    }

    /** @return the child's name (a parent's surname, a first name from the lists), or null to let MineColonies draw it. */
    @Nullable
    public static String forChild(final Random random, final boolean female, final IColony colony, final String parentA, final String parentB)
    {
        try
        {
            final List<NameList> lists = lists(colony);
            if (lists.isEmpty())
            {
                return null;
            }
            final String name = NameGenerator.childName(random, female, lists, parentA, parentB, taken(colony));
            if (name != null)
            {
                return name;
            }
            // No parent to take a surname from: a whole new name, as MineColonies does.
            if ((parentA == null || parentA.isEmpty()) && (parentB == null || parentB.isEmpty()))
            {
                return forNewCivilian(random, female, colony, colony.getCitizenNameFile());
            }
            return null;
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return null;
        }
    }

    private static List<NameList> lists(@Nullable final IColony colony)
    {
        if (failed || colony == null || colony.isRemote() || !(colony.getWorld() instanceof ServerLevel level))
        {
            return List.of();
        }
        final UUID owner = colony.getPermissions().getOwner();
        return owner == null ? List.of() : NamePools.get(level.getServer()).lists(owner);
    }

    /** Names of the colony's citizens and visitors. */
    private static Set<String> taken(final IColony colony)
    {
        final Set<String> names = new HashSet<>();
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen != null && citizen.getName() != null)
            {
                names.add(citizen.getName());
            }
        }
        for (final ICivilianData visitor : colony.getVisitorManager().getCivilianDataMap().values())
        {
            if (visitor != null && visitor.getName() != null)
            {
                names.add(visitor.getName());
            }
        }
        return names;
    }

    private static void fail(final Throwable e)
    {
        if (!failed)
        {
            failed = true;
            LOGGER.warn("Custom citizen names unavailable, MineColonies names the citizens", e);
        }
    }
}
