package doctor4t.ratsmischief.common.item;

import doctor4t.ratsmischief.common.RatsMischief;
import doctor4t.ratsmischief.common.entity.RatEntity;
import doctor4t.ratsmischief.common.init.ModEntities;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.network.GeckoLibNetwork;
import software.bernie.geckolib3.network.ISyncable;
import software.bernie.geckolib3.util.GeckoLibUtil;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.text.Style.EMPTY;

public class RatItem extends Item implements IAnimatable, ISyncable {
	private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

	public RatItem(Settings settings) {
		super(settings);
		GeckoLibNetwork.registerSyncable(this);
	}

	public static void cycleRatReturn(ItemStack stack) {
		NbtCompound ratTag = getRatTag(stack);
		if (ratTag == null) {
			return;
		}

		ratTag.putBoolean("ShouldReturnToOwnerInventory", !ratTag.getBoolean("ShouldReturnToOwnerInventory"));
	}

	private <P extends Item & IAnimatable> PlayState predicate(AnimationEvent<P> event) {
		return PlayState.CONTINUE;
	}

	@Override
	public void registerControllers(AnimationData data) {
		AnimationController<RatItem> controller = new AnimationController<>(this, "idle", 20, this::predicate);
		data.addAnimationController(controller);
	}

	@Override
	public AnimationFactory getFactory() {
		return this.factory;
	}

	@Override
	public void onAnimationSync(int id, int state) {
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient()) {
			RatEntity rat = getRatFromItem(world, user.getStackInHand(hand), new Vec3d(user.getX(), user.getEyeY() - 0.10000000149011612D, user.getZ()));
			if (rat == null) {
				return TypedActionResult.fail(user.getStackInHand(hand));
			}
			rat.sendFlying(user, user.getPitch(), user.getYaw(), user.getRoll(), 3f, 1f);
			world.spawnEntity(rat);
			if (!user.getAbilities().creativeMode) {
				user.getStackInHand(hand).decrement(1);
			}
			return TypedActionResult.success(user.getStackInHand(hand));
		}
		return super.use(world, user, hand);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity owner = context.getPlayer();
		Hand hand = context.getHand();
		World world = context.getWorld();

		world.spawnEntity(getRatFromItem(world, owner.getStackInHand(hand), context.getHitPos()));
		if (!owner.getAbilities().creativeMode) {
			owner.getStackInHand(hand).decrement(1);
		}

		return ActionResult.SUCCESS;
	}

	public RatEntity getRatFromItem(World world, ItemStack ratItemStack, Vec3d spawnPos) {
		NbtCompound ratTag = getRatTag(ratItemStack, world);
		RatEntity rat = ModEntities.RAT.create(world);
		if (rat != null) {
			rat.readNbt(ratTag);
			rat.updatePosition(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
			rat.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
			rat.setSitting(false);
		}
		return rat;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		NbtCompound ratTag = getRatTag(stack, world);
		MutableText ratType = Text.translatable("type.ratsmischief." + getRatType(stack).name().toLowerCase());

		Style style = EMPTY.withColor(Formatting.DARK_GRAY);
		if (ratTag.getString("RatType").equals(RatEntity.Type.GOLD.name())) {
			style = EMPTY.withColor(Formatting.GOLD);
		}

		if (ratTag.contains("CustomName")) {
			Matcher matcher = Pattern.compile("\\{\"text\":\"(.+)\"}").matcher(ratTag.getString("CustomName"));
			if (matcher.find()) {
				String name = matcher.group(1);
				tooltip.add(Text.literal(name).append(" (").append(ratType).append(")").setStyle(style));
			}
		} else {
			tooltip.add(ratType.setStyle(style));
		}

		// set to return
		if (ratTag.getBoolean("ShouldReturnToOwnerInventory")) {
			tooltip.add(Text.translatable("tooltip.ratsmischief.return").setStyle(EMPTY.withItalic(true).withColor(Formatting.GRAY)));
		}

		super.appendTooltip(stack, world, tooltip, context);
	}

	public static String getRatName(ItemStack stack) {
		return stack.hasCustomName() ? stack.getName().getString() : null;
	}

	@Nullable
	public static NbtCompound getRatTag(ItemStack stack) {
		NbtCompound subNbt = stack.getOrCreateSubNbt(RatsMischief.MOD_ID);
		if (subNbt.contains("rat")) {
			return subNbt.getCompound("rat");
		}
		return null;
	}

	public static NbtCompound getRatTag(ItemStack stack, World world) {
		NbtCompound ratTag = getRatTag(stack);
		if (ratTag == null) {
			RatEntity rat = new RatEntity(ModEntities.RAT, world);
			NbtCompound nbt = new NbtCompound();
			rat.saveNbt(nbt);
			stack.getOrCreateSubNbt(RatsMischief.MOD_ID).put("rat", nbt);
			ratTag = stack.getOrCreateSubNbt(RatsMischief.MOD_ID).getCompound("rat");
		}
		return ratTag;
	}

	public static RatEntity.Type getRatType(ItemStack stack) {
		NbtCompound ratTag = getRatTag(stack);
		if (ratTag == null) {
			return RatEntity.Type.WILD;
		}
		String ratType = ratTag.getString("RatType").toUpperCase();
		return RatEntity.Type.byName(ratType, RatEntity.Type.WILD);
	}

	public static DyeColor getRatColor(ItemStack stack) {
		NbtCompound ratTag = getRatTag(stack);
		if (ratTag == null) {
			return DyeColor.WHITE;
		}
		String ratColor = ratTag.getString("Color").toUpperCase();
		return DyeColor.byName(ratColor, DyeColor.WHITE);
	}
}
