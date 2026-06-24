package fun.lumis.events.render;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import fun.lumis.utils.client.managers.event.events.callables.EventCancellable;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FogEvent extends EventCancellable {
    float distance;
    int color;
}
