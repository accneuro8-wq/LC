package fun.lumis.utils.client.managers.api.command.datatypes;

import fun.lumis.utils.client.managers.api.command.exception.CommandException;

public interface IDatatypePost<T, O> extends IDatatype {
    T apply(IDatatypeContext datatypeContext, O original) throws CommandException;
}
