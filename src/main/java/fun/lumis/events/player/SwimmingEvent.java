package fun.lumis.events.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.Vec3d;
import fun.lumis.utils.client.managers.event.events.callables.EventCancellable;

@Setter
@Getter
@AllArgsConstructor
public class SwimmingEvent extends EventCancellable {
    Vec3d vector;
}
