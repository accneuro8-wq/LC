package fun.lumis.utils.client;

import lombok.experimental.UtilityClass;
import fun.lumis.utils.client.managers.api.draggable.AbstractDraggable;
import fun.lumis.features.module.Module;
import fun.lumis.lumis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@UtilityClass
public class Instance {
    private final ConcurrentMap<Class<? extends Module>, Module> instanceModules = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<? extends AbstractDraggable>, AbstractDraggable> instanceDraggables = new ConcurrentHashMap<>();

    public <T extends Module> T get(Class<T> clazz) {
        return clazz.cast(instanceModules.computeIfAbsent(clazz, instance -> lumis.getInstance().getModuleProvider().get(instance)));
    }

    public <T extends Module> T get(String module) {
        return lumis.getInstance().getModuleProvider().get(module);
    }

    public <T extends AbstractDraggable> T getDraggable(Class<T> clazz) {
        return clazz.cast(instanceDraggables.computeIfAbsent(clazz, instance -> lumis.getInstance().getDraggableRepository().get(instance)));
    }

    public <T extends AbstractDraggable> T getDraggable(String draggable) {
        return lumis.getInstance().getDraggableRepository().get(draggable);
    }
}
