package fun.lumis.events.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter; // Добавляем Setter
import fun.lumis.utils.client.managers.event.events.Event;

@Getter
@Setter
@AllArgsConstructor
public class RotationUpdateEvent implements Event {
    float yaw;
    float pitch;
    boolean onGround;
    byte type;
}