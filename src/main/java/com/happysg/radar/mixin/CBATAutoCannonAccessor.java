package com.happysg.radar.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;

@Pseudo
@Mixin(targets = {
        "com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption",
        "com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption"
}, remap = false)
public interface CBATAutoCannonAccessor extends AutoCannonAccessor {
    @Override
    @Accessor("cannonMaterial")
    AutocannonMaterial getMaterial();
}
