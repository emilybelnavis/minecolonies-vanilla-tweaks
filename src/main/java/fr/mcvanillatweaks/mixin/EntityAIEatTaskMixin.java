package fr.mcvanillatweaks.mixin;

import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import fr.mcvanillatweaks.fixes.CookMeals;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server side: the restaurant's cook, who nobody serves, gets a meal when MineColonies finds him nothing (see
 * {@link CookMeals}). Optional injection (mixin config {@code defaultRequire: 0}): if MineColonies renames the method,
 * its own behaviour stays.
 */
@Mixin(value = EntityAIEatTask.class, remap = false)
public abstract class EntityAIEatTaskMixin
{
    @Shadow
    @Final
    private EntityCitizen citizen;

    /** {@code goToHut} only returns DONE for a cook who found nothing he may eat in his inventory or his restaurant. */
    @Inject(method = "goToHut", at = @At("RETURN"))
    private void mcvanillatweaks$feedCook(final CallbackInfoReturnable<EntityAIEatTask.EatingState> callback)
    {
        if (callback.getReturnValue() == EntityAIEatTask.EatingState.DONE)
        {
            CookMeals.onNothingToEat(citizen);
        }
    }
}
