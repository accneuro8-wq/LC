package fun.lumis.features.impl.combat;

import antidaunleak.api.annotation.Native;
import fun.lumis.utils.interactions.inv.InventoryTask;
import fun.lumis.utils.mixin.IEntity;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.interactions.inv.InventoryFlowManager;
import fun.lumis.events.player.TickEvent;
import fun.lumis.display.hud.Notifications;
import fun.lumis.features.impl.render.Hud;
import fun.lumis.utils.display.interfaces.IArmorItem;
import fun.lumis.utils.math.time.StopWatch;
import fun.lumis.features.module.setting.implement.SliderSettings;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AutoArmor extends Module {

    SliderSettings delay = new SliderSettings("Задержка", "Задержка")
            .setValue(150f).range(50f, 1000f);

    StopWatch timer = new StopWatch();

    public AutoArmor() {
        super("AutoArmor", "Auto Armor", ModuleCategory.PLAYER);
        setup(delay);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || InventoryTask.isServerScreen()) return;
        if (!InventoryFlowManager.script.isFinished()) return;

        long currentDelay = (long) (delay.getValue() + ThreadLocalRandom.current().nextInt(0, 50));
        if (!timer.every(currentDelay)) return;

        List<Runnable> list = new ArrayList<>();

        for (EquipmentSlot equipment : EquipmentSlot.values()) {
            if (equipment.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

            ItemStack equipStack = mc.player.getInventory().getArmorStack(equipment.getEntitySlotId());

            if (equipment.equals(EquipmentSlot.CHEST) && equipStack.getItem().equals(Items.ELYTRA) && ((IEntity) mc.player).getFlag(7)) {
                continue;
            }

            if (hasCurseOfBinding(equipStack)) continue;

            int armorSlot = 8 - equipment.getEntitySlotId();

            Slot slot = InventoryTask.getSlot(s -> {
                ItemStack stack = s.getStack();
                return s.id != armorSlot && !isBroken(stack) && !hasCurseOfBinding(stack) &&
                        stack.getItem() instanceof ArmorItem armorItem &&
                        ((IArmorItem) armorItem).zov_pidarok$getType().getEquipmentSlot().equals(equipment);
            }, Comparator.comparingDouble(s -> calculateArmorValue(s.getStack(), (IArmorItem) s.getStack().getItem())));

            if (slot != null && isBetter(slot.getStack(), equipStack)) {
                list.add(() -> InventoryTask.moveItem(slot.id, armorSlot, false, true));
                break;
            }

            if (!equipStack.isEmpty() && isBroken(equipStack)) {
                Hud hud = Hud.getInstance();
                if (slot != null) {
                    list.add(() -> InventoryTask.moveItem(slot.id, armorSlot, false, true));
                    if (hud.state && hud.notificationSettings.isSelected("Auto Armor"))
                        Notifications.getInstance().addList(Text.literal("Replaced - " + Formatting.GREEN + equipName(equipment) + Formatting.RESET + " with ").append(equipStack.getName()), 3000);
                } else if (InventoryTask.getSlot(Items.AIR, s -> s.id >= 9) != null) {
                    list.add(() -> InventoryTask.clickSlot(armorSlot, 0, SlotActionType.QUICK_MOVE, false));
                    if (hud.state && hud.notificationSettings.isSelected("Auto Armor"))
                        Notifications.getInstance().addList(Text.literal("Saved - ").append(equipStack.getName()), 3000);
                }
                break;
            }
        }

        if (!list.isEmpty()) {
            InventoryFlowManager.addTask(() -> {
                list.forEach(Runnable::run);
                timer.reset();
            });
        }
    }

    private float calculateArmorValue(ItemStack stack, IArmorItem armorItem) {
        if (mc.world == null) return 0;

        Registry<Enchantment> enchantments = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        ArmorMaterial material = armorItem.zov_pidarok$getMaterial();

        float protection = material.defense().getOrDefault(armorItem.zov_pidarok$getType(), 0) + material.toughness() +
                EnchantmentHelper.getLevel(enchantments.getEntry(Enchantments.PROTECTION.getValue()).orElseThrow(), stack);
        float unbreaking = EnchantmentHelper.getLevel(enchantments.getEntry(Enchantments.UNBREAKING.getValue()).orElseThrow(), stack);
        float mending = EnchantmentHelper.getLevel(enchantments.getEntry(Enchantments.MENDING.getValue()).orElseThrow(), stack);

        return protection + (unbreaking * 0.1f) + (mending * 0.2f);
    }

    private boolean hasCurseOfBinding(ItemStack stack) {
        if (stack == null || stack.isEmpty() || mc.world == null) return false;
        RegistryEntry<Enchantment> curseEntry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.BINDING_CURSE.getValue()).orElse(null);
        if (curseEntry == null) return false;
        return EnchantmentHelper.getLevel(curseEntry, stack) > 0;
    }

    private boolean isBroken(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) return false;
        return (double) stack.getDamage() / stack.getMaxDamage() > 0.98;
    }

    private boolean isBetter(ItemStack newArmor, ItemStack currentArmor) {
        if (currentArmor.isEmpty()) return true;
        if (!(newArmor.getItem() instanceof ArmorItem newItem) || !(currentArmor.getItem() instanceof ArmorItem currentItem))
            return false;

        return calculateArmorValue(newArmor, (IArmorItem) newItem) > calculateArmorValue(currentArmor, (IArmorItem) currentItem);
    }

    private String equipName(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case FEET -> "Boots";
            case LEGS -> "Leggings";
            case CHEST -> "Chestplate";
            case HEAD -> "Helmet";
            default -> "None";
        };
    }
}