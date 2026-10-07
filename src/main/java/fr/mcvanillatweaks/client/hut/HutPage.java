package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import org.jetbrains.annotations.Nullable;

/**
 * A page of {@link HutScreen}: the hut's home page, its workers (MineColonies' hiring window) or one of its modules
 * (the tabs MineColonies shows on the side of its hut windows).
 */
public record HutPage(Kind kind, @Nullable IBuildingModuleView module)
{
    public enum Kind
    {
        HOME, WORKERS, MODULE,
        /** The contents of all the hut's containers (MineColonies' all-inventory window). */
        STORAGE
    }

    public static final HutPage HOME = new HutPage(Kind.HOME, null);
    public static final HutPage WORKERS = new HutPage(Kind.WORKERS, null);
    public static final HutPage STORAGE = new HutPage(Kind.STORAGE, null);

    public static HutPage module(final IBuildingModuleView module)
    {
        return new HutPage(Kind.MODULE, module);
    }

    /**
     * Same page, even when MineColonies handed out another instance of the module view: views are matched by the
     * runtime id of the module that produced them.
     */
    public boolean sameAs(final HutPage other)
    {
        if (kind != other.kind)
        {
            return false;
        }
        if (kind != Kind.MODULE || module == other.module)
        {
            return true;
        }
        try
        {
            return module != null && other.module != null && module.getProducer() != null && other.module.getProducer() != null
                     && module.getProducer().getRuntimeID() == other.module.getProducer().getRuntimeID();
        }
        catch (final LinkageError | RuntimeException e)
        {
            return false;
        }
    }
}
