package fun.lumis.main.client;

import fun.lumis.utils.client.chat.StringHelper;

import java.io.File;

public record ClientInfo(String clientName, String userName, String role, File clientDir, File filesDir) implements ClientInfoProvider {

    @Override
    public String getFullInfo() {
        return String.format("Welcome! Client: %s Version: %s Branch: %s", clientName, "Baflllik && HZeed", StringHelper.getUserRole());
    }

    @Override
    public File configsDir() {
        return filesDir;
    }
}
