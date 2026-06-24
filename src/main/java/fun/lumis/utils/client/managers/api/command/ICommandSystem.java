package fun.lumis.utils.client.managers.api.command;

import fun.lumis.utils.client.managers.api.command.argparser.IArgParserManager;

public interface ICommandSystem {
    IArgParserManager getParserManager();
}
