package fun.lumis.utils.client.managers.file.impl;

import antidaunleak.api.annotation.Native;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import fun.lumis.common.proxy.Config;
import fun.lumis.common.proxy.ProxyServer;
import fun.lumis.utils.client.managers.file.ClientFile;
import fun.lumis.utils.client.managers.file.exception.FileLoadException;
import fun.lumis.utils.client.managers.file.exception.FileSaveException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ProxyFile extends ClientFile {

    public ProxyFile() {
        super("Proxy/Proxyconfig");
    }

    @Override
    public void saveToFile(File path) throws FileSaveException {
        Config.saveConfig();
    }

    @Override
    public void loadFromFile(File path) throws FileLoadException {
        Config.loadConfig();
    }
}
