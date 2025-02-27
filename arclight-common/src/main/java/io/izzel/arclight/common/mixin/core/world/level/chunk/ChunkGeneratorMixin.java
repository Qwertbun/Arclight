package io.izzel.arclight.common.mixin.core.world.level.chunk;

import io.izzel.arclight.common.bridge.core.world.IWorldBridge;
import io.izzel.arclight.common.bridge.core.world.WorldBridge;
import io.izzel.arclight.common.bridge.core.world.level.levelgen.ChunkGeneratorBridge;
import io.izzel.arclight.mixin.Decorate;
import io.izzel.arclight.mixin.DecorationOps;
import io.izzel.arclight.mixin.Local;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v.generator.CraftLimitedRegion;
import org.bukkit.craftbukkit.v.generator.structure.CraftStructure;
import org.bukkit.craftbukkit.v.util.RandomSourceWrapper;
import org.bukkit.generator.BlockPopulator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin implements ChunkGeneratorBridge {

    // @formatter:off
    @Shadow public abstract void applyBiomeDecoration(WorldGenLevel p_187712_, ChunkAccess p_187713_, StructureManager p_187714_);
    @Shadow @Final @Mutable protected BiomeSource biomeSource;
    // @formatter:on

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void arclight$addBukkitDecoration(WorldGenLevel level, ChunkAccess chunkAccess, StructureManager manager, CallbackInfo ci) {
        this.addDecorations(level, chunkAccess, manager);
    }

    @Decorate(method = "tryGenerateStructure", inject = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/StructureManager;setStartForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/chunk/StructureAccess;)V"))
    private void arclight$structureSpawn(@Local(ordinal = -1) StructureManager manager, @Local(ordinal = -1) ChunkPos chunkPos, @Local(ordinal = -1) Structure structure, @Local(ordinal = -1) StructureStart structurestart) throws Throwable {
        var box = structurestart.getBoundingBox();
        var event = new org.bukkit.event.world.AsyncStructureSpawnEvent(((IWorldBridge) manager.level).bridge$getMinecraftWorld().bridge$getWorld(), CraftStructure.minecraftToBukkit(structure), new org.bukkit.util.BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()), chunkPos.x, chunkPos.z);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            DecorationOps.cancel().invoke(true);
            return;
        }
        DecorationOps.blackhole().invoke();
    }

    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunkAccess, StructureManager structureFeatureManager, boolean vanilla) {
        if (vanilla) {
            this.applyBiomeDecoration(level, chunkAccess, structureFeatureManager);
        } else {
            this.addDecorations(level, chunkAccess, structureFeatureManager);
        }
    }

    private void addDecorations(WorldGenLevel region, ChunkAccess chunk, StructureManager structureManager) {
        org.bukkit.World world = ((WorldBridge) ((IWorldBridge) region).bridge$getMinecraftWorld()).bridge$getWorld();
        // only call when a populator is present (prevents unnecessary entity conversion)
        if (!world.getPopulators().isEmpty()) {
            CraftLimitedRegion limitedRegion = new CraftLimitedRegion(region, chunk.getPos());
            int x = chunk.getPos().x;
            int z = chunk.getPos().z;
            for (BlockPopulator populator : world.getPopulators()) {
                WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(region.getSeed()));
                random.setDecorationSeed(region.getSeed(), x, z);
                populator.populate(world, new RandomSourceWrapper.RandomWrapper(random), x, z, limitedRegion);
            }
            limitedRegion.saveEntities();
            limitedRegion.breakLink();
        }
    }

    @Override
    public void bridge$setBiomeSource(BiomeSource biomeSource) {
        this.biomeSource = biomeSource;
    }
}
