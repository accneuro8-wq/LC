package fun.lumis.features.module;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;
import fun.lumis.features.module.exception.ModuleException;
import fun.lumis.utils.client.managers.event.EventManager;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.events.keyboard.KeyEvent;
import fun.lumis.common.logger.implement.ConsoleLogger;
import fun.lumis.utils.display.interfaces.QuickLogger;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModuleSwitcher implements QuickLogger, QuickImports {
    List<Module> modules;

    public ModuleSwitcher(List<Module> modules, EventManager eventManager) {
        this.modules = modules;
        eventManager.register(this);
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        for (Module module : modules) {
            if (event.key() == module.getKey() && mc.currentScreen == null) {
                try {
                    handleModuleState(module, event.action());
                } catch (Exception e) {
                    handleException(module.getName(), e);
                }
            }
        }
    }

    private void handleModuleState(Module module, int action) {
        if (module.getType() == 1 && action == 1) {
            module.switchState();
        }
    }

    private void handleException(String moduleName, Exception e) {
        final ConsoleLogger consoleLogger = new ConsoleLogger();

        if (e instanceof ModuleException) {
            logDirect("[" + moduleName + "] " + Formatting.RED + e.getMessage());
        } else {
            consoleLogger.log("Error in module " + moduleName + ": " + e.getMessage());
        }
    }
}
