package fun.lumis.utils.client.managers.api.command.datatypes;

import fun.lumis.utils.client.managers.api.command.exception.CommandException;
import fun.lumis.utils.display.interfaces.QuickImports;

import java.util.stream.Stream;

public interface IDatatype extends QuickImports {
    Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException;
}
