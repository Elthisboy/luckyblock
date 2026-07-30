package mod.lucky.neoforge.game

import com.mojang.serialization.MapCodec
import mod.lucky.neoforge.*
import mod.lucky.java.game.getLuckModifierCraftingResult
import mod.lucky.java.game.matchesLuckModifierCraftingRecipe
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level

/**
 * In 26.1 `CustomRecipe` lost its `CraftingBookCategory` constructor argument,
 * `RecipeSerializer` became a plain record of (MapCodec, StreamCodec), and
 * `Recipe.assemble` no longer receives a `HolderLookup.Provider`.
 *
 * We still need registry access to round-trip item components through NBT, so we
 * capture it from the `Level` handed to `matches`, which the crafting container
 * always calls immediately before `assemble`.
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
        return ForgeLuckyRegistry.luckModifierCraftingRecipe.get()
    }
}
