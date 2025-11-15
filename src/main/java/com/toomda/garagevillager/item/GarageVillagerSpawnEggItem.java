package com.toomda.garagevillager.item;

import com.toomda.garagevillager.entity.GarageVillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class GarageVillagerSpawnEggItem extends SpawnEggItem {

    private final EntityType<? extends GarageVillagerEntity> type;

    public GarageVillagerSpawnEggItem(EntityType<? extends GarageVillagerEntity> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack stack = ctx.getItemInHand();

        UUID targetOwner = null;

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom != null) {
            CompoundTag data = custom.copyTag();
            if (data.contains("Owner")) {
                String ownerString = data.getStringOr("Owner", "");
                if (!ownerString.isEmpty()) {
                    try {
                        targetOwner = UUID.fromString(ownerString);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        if (targetOwner == null) {
            targetOwner = player.getUUID();
        }

        AABB worldBox = new AABB(
                -30_000_000, serverLevel.getMinY(), -30_000_000,
                30_000_000, serverLevel.getMaxY(),  30_000_000
        );

        UUID finalTargetOwner = targetOwner;
        boolean alreadyHasVillager = !serverLevel
                .getEntitiesOfClass(
                        GarageVillagerEntity.class,
                        worldBox,
                        (GarageVillagerEntity villager) -> {
                            UUID owner = villager.getOwnerUuid();
                            return owner != null && owner.equals(finalTargetOwner);
                        }
                )
                .isEmpty();

        if (alreadyHasVillager) {
            player.displayClientMessage(
                    Component.literal("You already have a Garage Villager! Pick it up before spawning a new one."),
                    true
            );
            return InteractionResult.CONSUME;
        }

        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());

        GarageVillagerEntity entity = type.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        entity.setPos(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        entity.setYRot(serverLevel.random.nextFloat() * 360F);
        entity.setXRot(0.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yBodyRot = entity.getYRot();

        if (custom != null) {
            CompoundTag data = custom.copyTag();
            entity.loadFromItemTag(data);
        } else {
            entity.setOwner(player);
        }

        serverLevel.addFreshEntity(entity);

        return InteractionResult.CONSUME;
    }


}
