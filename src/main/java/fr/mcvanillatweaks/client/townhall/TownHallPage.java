package fr.mcvanillatweaks.client.townhall;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The tabs of the town hall screen, in display order. */
public enum TownHallPage
{
    ACTIONS(Items.BELL, "com.minecolonies.coremod.gui.townhalltab.actions", "actions"),
    DASHBOARD(Items.BEACON, "mcvanillatweaks.townhall.tab.dashboard", "actions"),
    INFO(Items.WRITABLE_BOOK, "com.minecolonies.coremod.gui.townhalltab.information", "infopage"),
    CITIZENS(Items.PLAYER_HEAD, "com.minecolonies.coremod.gui.townhalltab.citizens", "citizens"),
    HAPPINESS(Items.CAKE, "mcvanillatweaks.townhall.tab.happiness", "citizens"),
    STATS(Items.CLOCK, "com.minecolonies.coremod.gui.townhalltab.stats", "happiness"),
    PERMISSIONS(Items.TRIPWIRE_HOOK, "com.minecolonies.coremod.gui.townhalltab.permissions", "permissions"),
    ALLIANCES(Items.EMERALD, "com.minecolonies.coremod.gui.townhalltab.alliances", "alliances"),
    SETTINGS(Items.COMPARATOR, "com.minecolonies.coremod.gui.townhalltab.settings", "settings");

    private final ItemStack icon;
    private final String titleKey;
    /** Id of the matching MineColonies tab button (sent with the GUI click trigger, like MineColonies does). */
    private final String buttonId;

    TownHallPage(final Item icon, final String titleKey, final String buttonId)
    {
        this.icon = new ItemStack(icon);
        this.titleKey = titleKey;
        this.buttonId = buttonId;
    }

    public ItemStack icon()
    {
        return icon;
    }

    public Component title()
    {
        return Component.translatable(titleKey);
    }

    public String buttonId()
    {
        return buttonId;
    }
}
