package fun.lumis.commands;

import fun.lumis.utils.client.managers.api.command.ICommandSystem;
import fun.lumis.utils.client.managers.api.command.argparser.IArgParserManager;
import fun.lumis.commands.argparser.ArgParserManager;

public enum CommandSystem implements ICommandSystem {
    INSTANCE;

    @Override
    public IArgParserManager getParserManager() {
        return ArgParserManager.INSTANCE;
    }
}
