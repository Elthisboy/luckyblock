package mod.lucky.fabric.game

import mod.lucky.common.DEFAULT_RANDOM
import mod.lucky.fabric.*
import mod.lucky.java.*
import mod.lucky.java.game.doBowDrop
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.*
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.Level
import java.util.function.Consumer

/**
 * Bow "pulling"/"pull" visuals are no longer registered in code -- since 1.21.4
 * they come from the item model definition under assets/lucky/items/, so the old
 * `registerLuckyBowModels` helper is gone.
 */
class LuckyBow(registryId: MCIdentifier) : BowItem(
    Item.Properties()
        .setId(ResourceKey.create(Registries.ITEM, registryId))
        .stacksTo(1)
        .durability(1000)
) {
    override fun releaseUsing(stack: MCItemStack, world: Level, player: LivingEntity, timeLeft: Int): Boolean {
        if (player is MCPlayerEntity) {
            val arrowStack = player.getProjectile(stack)
            if (arrowStack.isEmpty) {
                return false
            } else {
                val i: Int = getUseDuration(stack, player) - timeLeft
                val power = getPowerForTime(i)
                if (i < 0 || power < 0.1f) {
                    return false
                } else {
                    draw(stack, arrowStack, player)

                    if (!isClientWorld(world)) {
                        doBowDrop(
                            world = world,
                            player = player,
                            power = power.toDouble(),
                            stackNBT = componentsToNbt(stack.components, world.registryAccess()),
                            sourceId = JAVA_GAME_API.getItemId(this),
                        )
                    }

                    world.playSound(null,
                        player.x,
                        player.y,
                        player.z,
                        SoundEvents.ARROW_SHOOT,
                        SoundSource.PLAYERS,
                        1.0f,
                        1.0f / (DEFAULT_RANDOM.nextDouble().toFloat() * 0.4f + 1.2f) + power * 0.5f
                    )
                    return true
                }
            }
        }
        return false
    }

    // NeoForge's Item.getEnchantmentLevel override has no vanilla equivalent, so the
    // Fabric build simply doesn't suppress enchantment levels on the lucky bow.

    @OnlyInClient
    override fun isFoil(stack: MCItemStack): Boolean {
        return true
    }

    @OnlyInClient
    override fun appendHoverText(
        stack: MCItemStack,
        context: TooltipContext,
        tooltipDisplay: TooltipDisplay,
        tooltipAdder: Consumer<Component>,
        flag: TooltipFlag
    ) {
        context.registries()?.let { access ->
            createLuckyTooltip(stack, access).forEach { tooltipAdder.accept(it) }
        }
    }
}
