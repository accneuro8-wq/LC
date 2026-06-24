package fun.lumis.events.block;

import lombok.AllArgsConstructor;
import lombok.Getter;
import fun.lumis.utils.client.managers.event.events.callables.EventCancellable;

@Getter
@AllArgsConstructor
public class PushEvent extends EventCancellable {
    private Type type;

    public enum Type {
        COLLISION, BLOCK, WATER
    }
}
