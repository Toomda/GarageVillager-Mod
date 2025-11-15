package com.toomda.garagevillager.item;

import com.toomda.garagevillager.entity.GarageVillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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

public class GarageVillagerSpawnEggItem extends SpawnEggItem {

    private final EntityType<? extends GarageVillagerEntity> type;

    public GarageVillagerSpawnEggItem(EntityType<? extends GarageVillagerEntity> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        ItemStack stack = ctx.getItemInHand();

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

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom != null) {
            CompoundTag data = custom.copyTag();
            entity.loadFromItemTag(data);
        } else {
            entity.setOwner(player);
        }

        serverLevel.addFreshEntity(entity);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }

}
