package fr.mcvanillatweaks.client.citizen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The tabs of the citizen screen, in display order. {@link #DEBUG} is aligned on the right, like the vanilla creative
 * inventory's right-hand tabs.
 */
public enum CitizenPage
{
    OVERVIEW(Items.NAME_TAG, "mcvanillatweaks.citizen.tab.overview", "mainTab"),
    SKILLS(Items.EXPERIENCE_BOTTLE, "mcvanillatweaks.citizen.tab.skills", "jobTab"),
    INVENTORY(Items.CHEST, "com.minecolonies.coremod.gui.citizen.inventory", "inventoryTab"),
    REQUESTS(Items.WRITABLE_BOOK, "mcvanillatweaks.citizen.tab.requests", "requestTab"),
    HAPPINESS(Items.CAKE, "com.minecolonies.coremod.gui.citizen.happiness", "happinessTab"),
    FAMILY(Items.OAK_SAPLING, "com.minecolonies.coremod.gui.citizen.family", "familyTab"),
    DEBUG(Items.COMMAND_BLOCK, "com.minecolonies.coremod.debug.gui.tabicon", "debugTab");

    private final ItemStack icon;
    private final String titleKey;
    /** Id of the matching MineColonies tab button (sent with the GUI click trigger, like MineColonies does). */
    private final String buttonId;

    CitizenPage(final Item icon, final String titleKey, final String buttonId)
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
