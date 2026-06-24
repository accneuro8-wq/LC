package fun.lumis.events.item;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import fun.lumis.utils.client.managers.event.events.callables.EventCancellable;

@Getter
@Setter
@AllArgsConstructor
public class UsingItemEvent extends EventCancellable {
    byte type;
}
