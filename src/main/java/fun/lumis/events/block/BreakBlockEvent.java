package fun.lumis.events.block;

import net.minecraft.util.math.BlockPos;
import fun.lumis.utils.client.managers.event.events.Event;

public record BreakBlockEvent(BlockPos blockPos) implements Event {}
