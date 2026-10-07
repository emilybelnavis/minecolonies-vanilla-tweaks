package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.core.client.gui.map.WindowColonyMap;
import com.minecolonies.core.network.messages.client.colony.ColonyListMessage.ColonyInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Preview of {@link ColonyMapScreen}: a proxy town hall with one generated map, over the hut sample's colony. */
public final class ColonyMapPreview
{
    private ColonyMapPreview()
    {
    }

    private static ITownHallView hall(final IColonyView colony)
    {
        final MapItemSavedData data = MapItemSavedData.createFresh(0, 0, (byte) 1, false, false, Level.OVERWORLD);
        for (int i = 0; i < data.colors.length; i++)
        {
            final int x = i % 128 - 64;
            final int z = i / 128 - 64;
            final double d = Math.hypot(x - 20, z + 10);
            // map colour ids: 4 grass, 12 water, 8 sand, 5 darker grass (id * 4 + brightness 1)
            final int id = d < 18 ? 12 : d < 22 ? 2 : (x * 7 + z * 13) % 11 == 0 ? 7 : 1;
            data.colors[i] = (byte) (id * 4 + 1);
        }
        final List<ITownHallView.MapEntry> maps = List.of(new ITownHallView.MapEntry(new MapId(901), data));
        return (ITownHallView) Proxy.newProxyInstance(ColonyMapPreview.class.getClassLoader(), new Class<?>[] {ITownHallView.class}, (self, method, args) -> {
            switch (method.getName())
            {
                case "toString":
                    return "ITownHallView sample";
                case "hashCode":
                    return System.identityHashCode(self);
                case "equals":
                    return self == args[0];
                case "getMapDataList":
                    return maps;
                case "getColony":
                    return colony;
                default:
                    return method.getReturnType() == boolean.class ? (Object) false : method.getReturnType() == int.class ? (Object) 0 : null;
            }
        });
    }

    private static void colonies() throws ReflectiveOperationException
    {
        final List<ColonyInfo> list = new ArrayList<>();
        final String[] names = {"Exempleville", "Valdoria", "Petit Hameau", "Port-Rouge"};
        final int[] citizens = {18, 64, 6, 120};
        final int[] prestige = {450, 1210, 60, 2300};
        for (int i = 0; i < names.length; i++)
        {
            final ColonyInfo info = new ColonyInfo(i + 1);
            set(info, "name", names[i]);
            set(info, "owner", "Joueur" + i);
            set(info, "citizencount", citizens[i]);
            set(info, "prestige", prestige[i]);
            set(info, "center", new BlockPos(-300 + i * 220, 64, 120 * (i % 2)));
            list.add(info);
        }
        WindowColonyMap.setColonies(list);
    }

    private static void set(final Object target, final String field, final Object value) throws ReflectiveOperationException
    {
        final Field f = ColonyInfo.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    public static Screen map(final IColonyView colony)
    {
        try
        {
            colonies();
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
        return new ColonyMapScreen(hall(colony), true);
    }

    public static Screen ranking(final IColonyView colony)
    {
        final ColonyMapScreen screen = (ColonyMapScreen) map(colony);
        screen.showRanking();
        return screen;
    }
}
