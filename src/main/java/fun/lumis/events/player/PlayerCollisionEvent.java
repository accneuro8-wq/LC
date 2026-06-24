package fun.lumis.events.player;

import lombok.*;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Block;
import fun.lumis.utils.client.managers.event.events.callables.EventCancellable;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerCollisionEvent extends EventCancellable {
    private Block block;

}
