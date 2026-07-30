package mod.lucky.fabric.game

import com.mojang.serialization.MapCodec
import mod.lucky.common.GAME_API
import mod.lucky.fabric.*
import mod.lucky.java.*
import mod.lucky.java.ItemStack
import mod.lucky.java.loader.ShapedCraftingRecipe
import mod.lucky.java.loader.ShapelessCraftingRecipe
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level
import java.util.Optional

typealias MCCraftingRecipe = net.minecraft.world.item.crafting.CraftingRecipe
typealias MCShapelessCraftingRecipe = net.minecraft.world.item.crafting.ShapelessRecipe
typealias MCShapedCraftingRecipe = net.minecraft.world.item.crafting.ShapedRecipe

private val commonInfo = Recipe.CommonInfo(true)
private val bookInfo = CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "lucky")

fun getIngredient(id: String): Ingredient? {
    val item = BuiltInRegistries.ITEM.getOptional(MCIdentifier.parse(id)).orElse(null)
    if (item == null) {
        GAME_API.logError("Invalid item in recipe: $id")
        return null
    }
    return Ingredient.of(item)
}

/**
 * 26.1 recipe shape: results are `ItemStackTemplate`s rather than `ItemStack`s,
 * shaped recipes take a `ShapedRecipePattern`, and both take Recipe.CommonInfo /
 * CraftingBookInfo instead of loose group + category arguments.
 */
private fun toResultTemplate(stack: ItemStack): ItemStackTemplate? {
    val item = BuiltInRegistries.ITEM.getOptional(MCIdentifier.parse(stack.itemId)).orElse(null)
    if (item == null) {
        GAME_API.logError("Invalid result item in recipe: ${stack.itemId}")
        return null
    }
    return ItemStackTemplate(item, stack.count)
}

fun registerAddonCraftingRecipes() {
    val recipes = JavaLuckyRegistry.allAddonResources.flatMap { addonResources ->
        val blockId = addonResources.addon.ids.block

        if (blockId == null) emptyList<MCCraftingRecipe>()
        else addonResources.blockCraftingRecipes.mapNotNull { recipe ->
            when (recipe) {
                is ShapelessCraftingRecipe -> toResultTemplate(recipe.resultStack)?.let { result ->
                    MCShapelessCraftingRecipe(
                        commonInfo,
                        bookInfo,
                        result,
                        recipe.ingredientIds.mapNotNull { getIngredient(it) },
                    )
                }

                is ShapedCraftingRecipe -> toResultTemplate(recipe.resultStack)?.let { result ->
                    MCShapedCraftingRecipe(
                        commonInfo,
                        bookInfo,
                        ShapedRecipePattern(
                            recipe.width,
                            recipe.height,
                            recipe.ingredientIds.map { id ->
                                Optional.ofNullable(id?.let { getIngredient(it) })
                            },
                            Optional.empty(),
                        ),
                        result,
                    )
                }

                else -> null
            }
        }
    }

    AddonCraftingRecipe.craftingRecipes = recipes
}

class AddonCraftingRecipe : CustomRecipe() {
    companion object {
        lateinit var craftingRecipes: List<MCCraftingRecipe>

        val MAP_CODEC: MapCodec<AddonCraftingRecipe> = MapCodec.unit { AddonCraftingRecipe() }
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AddonCraftingRecipe> =
            StreamCodec.unit(AddonCraftingRecipe())
        val SERIALIZER: RecipeSerializer<AddonCraftingRecipe> =
            RecipeSerializer(MAP_CODEC, STREAM_CODEC)
    }

    @Volatile
    private var lastLevel: Level? = null

    override fun matches(inv: CraftingInput, world: Level): Boolean {
        lastLevel = world
        return craftingRecipes.find { it.matches(inv, world) } != null
    }

    override fun assemble(inv: CraftingInput): MCItemStack {
        val level = lastLevel ?: return MCItemStack.EMPTY
        return craftingRecipes.find { it.matches(inv, level) }?.assemble(inv) ?: MCItemStack.EMPTY
    }

    override fun group(): String {
        return "lucky"
    }

    override fun getSerializer(): RecipeSerializer<out CustomRecipe> {
        return FabricLuckyRegistry.addonCraftingRecipe
    }
}
