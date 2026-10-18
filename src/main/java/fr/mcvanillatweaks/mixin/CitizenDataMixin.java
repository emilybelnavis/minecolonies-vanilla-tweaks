package fr.mcvanillatweaks.mixin;

import com.minecolonies.api.colony.CitizenNameFile;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.CitizenData;
import fr.mcvanillatweaks.names.CitizenNaming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * Server side: MineColonies' two name draws give the colony owner's lists a say (see {@link CitizenNaming}). Both
 * injections are optional (mixin config {@code defaultRequire: 0}): if MineColonies renames these methods, its own
 * names are used.
 */
@Mixin(value = CitizenData.class, remap = false)
public abstract class CitizenDataMixin
{
    /** New citizens and visitors: {@code initForNewCivilian}, {@code setGenderAndGenerateName}. */
    @Inject(method = "generateName(Ljava/util/Random;ZLcom/minecolonies/api/colony/IColony;Lcom/minecolonies/api/colony/CitizenNameFile;)Ljava/lang/String;",
      at = @At("HEAD"), cancellable = true)
    private static void mcvanillatweaks$newName(final Random random, final boolean female, final IColony colony, final CitizenNameFile file,
      final CallbackInfoReturnable<String> callback)
    {
        final String name = CitizenNaming.forNewCivilian(random, female, colony, file);
        if (name != null)
        {
            callback.setReturnValue(name);
        }
    }

    /** Children, from their parents' names (ReproductionManager). */
    @Inject(method = "generateName(Ljava/util/Random;Ljava/lang/String;Ljava/lang/String;Lcom/minecolonies/api/colony/CitizenNameFile;)V",
      at = @At("HEAD"), cancellable = true)
    private void mcvanillatweaks$childName(final Random random, final String parentA, final String parentB, final CitizenNameFile file,
      final CallbackInfo callback)
    {
        final CitizenData self = (CitizenData) (Object) this;
        final String name = CitizenNaming.forChild(random, self.isFemale(), self.getColony(), parentA, parentB);
        if (name != null)
        {
            self.setName(name);
            callback.cancel();
        }
    }
}
