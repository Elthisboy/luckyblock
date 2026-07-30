package mod.lucky.fabric

import com.mojang.logging.LogUtils
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import mod.lucky.common.GAME_API
import mod.lucky.common.LOGGER
import mod.lucky.common.PLATFORM_API
import mod.lucky.fabric.game.*
import mod.lucky.java.*
import mod.lucky.java.game.LuckyItemValues
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
// `object` is a Kotlin keyword, so the package segment needs backticks
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.ResourcePackActivationType
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.metadata.*
import net.fabricmc.loader.impl.metadata.ModOriginImpl
import net.fabricmc.loader.impl.util.FileSystemUtil
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceKey
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration
import org.slf4j.Logger
import java.nio.file.Path
import java.util.*

private fun id(path: String): MCIdentifier = MCIdentifier.parse(path)

// EntityType.Builder.build now requires the entity type's own ResourceKey
private fun entityKey(path: String): ResourceKey<EntityType<*>> =
    ResourceKey.create(Registries.ENTITY_TYPE, id(path))

object FabricLuckyRegistry {
    val LOGGER: Logger = LogUtils.getLogger()

    val luckyBlock = LuckyBlock(id(JavaLuckyRegistry.blockId))
    val luckyBlockItem = LuckyBlockItem(luckyBlock, id(JavaLuckyRegistry.blockId))
    val luckyBow = LuckyBow(id(JavaLuckyRegistry.bowId))
    val luckySword = LuckySword(id(JavaLuckyRegistry.swordId))
    val luckyPotion = LuckyPotion(id(JavaLuckyRegistry.potionId))
    val luckyWorldFeatureId = "lucky:lucky_world_gen"

    lateinit var luckyBlockCodec: MapCodec<LuckyBlock>
    lateinit var luckyBlockEntity: BlockEntityType<LuckyBlockEntity>
    lateinit var luckyProjectile: EntityType<LuckyProjectile>
    lateinit var thrownLuckyPotion: EntityType<ThrownLuckyPotion>
    lateinit var delayedDrop: EntityType<DelayedDrop>
    lateinit var luckModifierCraftingRecipe: RecipeSerializer<LuckModifierCraftingRecipe>
    lateinit var addonCraftingRecipe: RecipeSerializer<AddonCraftingRecipe>

    lateinit var luckComponent: DataComponentType<Int>
    lateinit var dropsComponent: DataComponentType<List<String>>

    val addonBlocks = HashMap<String, LuckyBlock>()
}

class FabricMod : ModInitializer {
    init {
        PLATFORM_API = JavaPlatformAPI
        LOGGER = FabricGameAPI
        GAME_API = FabricGameAPI
        JAVA_GAME_API = FabricJavaGameAPI
    }

    private fun registerWorldGen() {
        val featureId = id(FabricLuckyRegistry.luckyWorldFeatureId)
        val feature = LuckyWorldFeature(NoneFeatureConfiguration.CODEC)

        Registry.register(BuiltInRegistries.FEATURE, featureId, feature)

        BiomeModifications.addFeature(
            BiomeSelectors.all(),
            GenerationStep.Decoration.SURFACE_STRUCTURES,
            ResourceKey.create(Registries.PLACED_FEATURE, featureId)
        )
    }

    // fabric-item-group-api-v1 was replaced by fabric-creative-tab-api-v1:
    // ItemGroupEvents.modifyEntriesEvent -> CreativeModeTabEvents.modifyOutputEvent
    private fun setupCreativeTabs() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register { output ->
            output.accept(FabricLuckyRegistry.luckyBlockItem)
            createLuckySubItems(
                FabricLuckyRegistry.luckyBlockItem,
                output.context.holders(),
            ).forEach { output.accept(it) }

            for (addon in JavaLuckyRegistry.addons) {
                addon.ids.block?.let { output.accept(BuiltInRegistries.ITEM.getValue(id(it))) }
            }
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register { output ->
            output.accept(FabricLuckyRegistry.luckySword)
            output.accept(FabricLuckyRegistry.luckyBow)
            output.accept(FabricLuckyRegistry.luckyPotion)
            createLuckySubItems(
                FabricLuckyRegistry.luckyPotion,
                output.context.holders(),
                LuckyItemValues.veryLuckyPotion,
                LuckyItemValues.veryUnluckyPotion,
            ).forEach { output.accept(it) }

            for (addon in JavaLuckyRegistry.addons) {
                addon.ids.sword?.let { output.accept(BuiltInRegistries.ITEM.getValue(id(it))) }
                addon.ids.bow?.let { output.accept(BuiltInRegistries.ITEM.getValue(id(it))) }
                addon.ids.potion?.let { output.accept(BuiltInRegistries.ITEM.getValue(id(it))) }
            }
        }
    }

    override fun onInitialize() {
        FabricGameAPI.init()
        JavaLuckyRegistry.init()

        // Data component types, matching the NeoForge registrations in ForgeMod.
        // Without these, DataComponentMap.CODEC cannot round-trip lucky:luck and
        // lucky:drops, which the mod relies on for tooltips, luck crafting and
        // lucky block entity data.
        FabricLuckyRegistry.luckComponent = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            id("lucky:luck"),
            DataComponentType.Builder<Int>()
                .persistent(ExtraCodecs.intRange(-100, 100))
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .build()
        )
        FabricLuckyRegistry.dropsComponent = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            id("lucky:drops"),
            DataComponentType.Builder<List<String>>()
                .persistent(Codec.STRING.listOf())
                .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
                .build()
        )

        // Blocks and items must be registered before anything that resolves them.
        Registry.register(BuiltInRegistries.BLOCK, id(JavaLuckyRegistry.blockId), FabricLuckyRegistry.luckyBlock)
        Registry.register(BuiltInRegistries.ITEM, id(JavaLuckyRegistry.blockId), FabricLuckyRegistry.luckyBlockItem)
        Registry.register(BuiltInRegistries.ITEM, id(JavaLuckyRegistry.bowId), FabricLuckyRegistry.luckyBow)
        Registry.register(BuiltInRegistries.ITEM, id(JavaLuckyRegistry.swordId), FabricLuckyRegistry.luckySword)
        Registry.register(BuiltInRegistries.ITEM, id(JavaLuckyRegistry.potionId), FabricLuckyRegistry.luckyPotion)

        JavaLuckyRegistry.addons.map { addon ->
            addon.ids.block?.let {
                val block = LuckyBlock(id(it))
                FabricLuckyRegistry.addonBlocks[it] = block
                Registry.register(BuiltInRegistries.BLOCK, id(it), block)
                Registry.register(BuiltInRegistries.ITEM, id(it), LuckyBlockItem(block, id(it)))
            }
            addon.ids.bow?.let { Registry.register(BuiltInRegistries.ITEM, id(it), LuckyBow(id(it))) }
            addon.ids.sword?.let { Registry.register(BuiltInRegistries.ITEM, id(it), LuckySword(id(it))) }
            addon.ids.potion?.let { Registry.register(BuiltInRegistries.ITEM, id(it), LuckyPotion(id(it))) }
        }

        // A single shared block-type codec for LuckyBlock and every addon block.
        FabricLuckyRegistry.luckyBlockCodec = Registry.register(
            BuiltInRegistries.BLOCK_TYPE,
            id(JavaLuckyRegistry.blockId),
            MapCodec.unit { FabricLuckyRegistry.luckyBlock }
        )

        // BlockEntityType's constructor is private in vanilla (NeoForge access-transforms
        // it), so use Fabric API's builder instead.
        val validBlocks = (listOf(FabricLuckyRegistry.luckyBlock) + FabricLuckyRegistry.addonBlocks.values)
        FabricLuckyRegistry.luckyBlockEntity = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id(JavaLuckyRegistry.blockId),
            FabricBlockEntityTypeBuilder.create(
                FabricBlockEntityTypeBuilder.Factory(::LuckyBlockEntity),
                *validBlocks.toTypedArray()
            ).build()
        )

        // FabricEntityTypeBuilder is gone; vanilla's EntityType.Builder is used directly.
        // Note the vanilla names clientTrackingRange/updateInterval -- setTrackingRange /
        // setUpdateInterval / setShouldReceiveVelocityUpdates are NeoForge additions.
        FabricLuckyRegistry.luckyProjectile = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            id(JavaLuckyRegistry.projectileId),
            EntityType.Builder.of(::LuckyProjectile, MobCategory.MISC)
                .clientTrackingRange(100)
                .updateInterval(20)
                .build(entityKey(JavaLuckyRegistry.projectileId))
        )
        FabricLuckyRegistry.thrownLuckyPotion = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            id(JavaLuckyRegistry.potionId),
            EntityType.Builder.of(::ThrownLuckyPotion, MobCategory.MISC)
                .clientTrackingRange(100)
                .updateInterval(20)
                .build(entityKey(JavaLuckyRegistry.potionId))
        )
        FabricLuckyRegistry.delayedDrop = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            id(JavaLuckyRegistry.delayedDropId),
            EntityType.Builder.of(::DelayedDrop, MobCategory.MISC)
                .clientTrackingRange(100)
                .updateInterval(20)
                .build(entityKey(JavaLuckyRegistry.delayedDropId))
        )

        FabricLuckyRegistry.luckModifierCraftingRecipe = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER,
            id("lucky:crafting_luck"),
            LuckModifierCraftingRecipe.SERIALIZER,
        )
        FabricLuckyRegistry.addonCraftingRecipe = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER,
            id("lucky:crafting_addons"),
            AddonCraftingRecipe.SERIALIZER,
        )

        registerWorldGen()
        registerAddonCraftingRecipes()
        setupCreativeTabs()
    }
}

@OnlyInClient
class FabricModClient : ClientModInitializer {
    override fun onInitializeClient() {
        for (addon in JavaLuckyRegistry.addons) {
            val addonModMetadata = object : ModMetadata {
                override fun getType(): String = "builtin"
                override fun getId(): String = "${addon.addonId}_resources"
                override fun getProvides(): MutableCollection<String> = mutableListOf()
                override fun getVersion(): Version = Version.parse("1.0.0")
                override fun getEnvironment(): ModEnvironment = ModEnvironment.CLIENT
                override fun getDependencies(): MutableCollection<ModDependency> = mutableListOf()
                override fun getName(): String = "${addon.addonId} resources"
                override fun getDescription(): String = ""
                override fun getAuthors(): MutableCollection<Person> = mutableListOf()
                override fun getContributors(): MutableCollection<Person> = mutableListOf()
                override fun getContact(): ContactInformation = ContactInformation.EMPTY
                override fun getLicense(): MutableCollection<String> = mutableListOf()
                override fun getIconPath(size: Int): Optional<String> = Optional.empty()
                override fun containsCustomValue(key: String?): Boolean = false
                override fun getCustomValue(key: String?): CustomValue = throw Exception()
                override fun getCustomValues(): MutableMap<String, CustomValue> = mutableMapOf()
                @Deprecated("") override fun containsCustomElement(key: String?): Boolean = false
            }

            val addonModContainer = object : ModContainer {
                override fun getMetadata(): ModMetadata = addonModMetadata
                override fun getOrigin(): ModOrigin = ModOriginImpl(mutableListOf(addon.file.toPath()))
                override fun getRootPaths(): MutableList<Path> {
                    if (addon.file.isDirectory) return mutableListOf(addon.file.toPath())

                    val fileSystem = FileSystemUtil.getJarFileSystem(addon.file.toPath(), false).get()
                    return mutableListOf(fileSystem.rootDirectories.iterator().next())
                }

                override fun getContainingMod(): Optional<ModContainer> = Optional.empty()
                override fun getContainedMods(): MutableCollection<ModContainer> = mutableListOf()
                @Deprecated("") override fun getRootPath(): Path = rootPaths.first()
                @Deprecated("") override fun getPath(file: String?): Path = findPath(file).get()
            }

            // Now a public API rather than reaching into ResourceManagerHelperImpl.
            // ALWAYS_ENABLED (rather than the boolean overload, which only sets a
            // default that an existing options.txt overrides) keeps addon resources
            // switched on the way the NeoForge pack finder does.
            ResourceManagerHelper.registerBuiltinResourcePack(
                MCIdentifier.fromNamespaceAndPath("lucky", "${addon.addonId.substring("lucky:".length)}_resources"),
                addonModContainer,
                ResourcePackActivationType.ALWAYS_ENABLED,
            )
        }

        EntityRendererRegistry.register(FabricLuckyRegistry.luckyProjectile) { ctx ->
            LuckyProjectileRenderer(ctx)
        }
        EntityRendererRegistry.register(FabricLuckyRegistry.thrownLuckyPotion) { ctx ->
            ThrownLuckyPotionRenderer(ctx)
        }
        EntityRendererRegistry.register(FabricLuckyRegistry.delayedDrop) { ctx ->
            DelayedDropRenderer(ctx)
        }
    }
}
