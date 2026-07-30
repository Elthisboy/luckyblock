package mod.lucky.fabric.game

import com.mojang.serialization.MapCodec
import mod.lucky.fabric.*
import mod.lucky.java.game.getLuckModifierCraftingResult
import mod.lucky.java.game.matchesLuckModifierCraftingRecipe
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level

/**
 * See the NeoForge counterpart: in 26.1 `CustomRecipe` lost its constructor
 * argument, `RecipeSerializer` became a record of (MapCodec, StreamCodec), and
 * `assemble` no longer receives a `HolderLookup.Provider` -- so we capture it
 * from the `Level` passed to `matches`, which always runs first.
 */
class LuckModifierCraftingRecipe : CustomRecipe() {
    companion object {
        val MAP_CODEC: MapCodec<LuckModifierCraftingRecipe> =
            MapCodec.unit { LuckModifierCraftingRecipe() }
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, LuckModifierCraftingRecipe> =
            StreamCodec.unit(LuckModifierCraftingRecipe())
        val SERIALIZER: RecipeSerializer<LuckModifierCraftingRecipe> =
            RecipeSerializer(MAP_CODEC, STREAM_CODEC)
    }

    @Volatile
    private var lastAccess: HolderLookup.Provider? = null

    override fun matches(inv: CraftingInput, world: Level): Boolean {
        val access = world.registryAccess()
        lastAccess = access
        val stacks = (0 until inv.width() * inv.height()).map {
            toItemStack(inv.getItem(it), access, skipComponents = true)
        }
        return matchesLuckModifierCraftingRecipe(stacks)
    }

    override fun assemble(inv: CraftingInput): MCItemStack {
        val access = lastAccess ?: return MCItemStack.EMPTY
        val stacks = (0 until inv.width() * inv.height()).map { toItemStack(inv.getItem(it), access) }
        val result = getLuckModifierCraftingResult(stacks)
        return result?.let { toMCItemStack(it, access) } ?: MCItemStack.EMPTY
    }

    override fun getSerializer(): RecipeSerializer<out CustomRecipe> {
        return FabricLuckyRegistry.luckModifierCraftingRecipe
    }
}
