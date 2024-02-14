package io.izzel.arclight.boot.fabric.mod;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class ArclightGameProvider extends MinecraftGameProvider {

    private Path modFile;

    @Override
    public void initialize(FabricLauncher launcher) {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        System.setProperty("log4j.jul.LoggerAdapter", "io.izzel.arclight.boot.log.ArclightLoggerAdapter");
        System.setProperty("log4j.configurationFile", "arclight-log4j2.xml");
        for (var lib : System.getProperty("arclight.fabric.classpath").split(File.pathSeparator)) {
            launcher.addToClassPath(Paths.get(lib));
        }
        try {
            this.modFile = this.extract();
            launcher.addToClassPath(modFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        super.initialize(launcher);
    }

    @Override
    public Arguments getArguments() {
        Arguments arguments = super.getArguments();
        String old = arguments.get(Arguments.ADD_MODS);
        var path = this.modFile.toString();
        if (old != null) {
            path = old + File.pathSeparator + path;
        }
        arguments.put(Arguments.ADD_MODS, path);
        return arguments;
    }

    private Path extract() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/META-INF/MANIFEST.MF")) {
            Manifest manifest = new Manifest(stream);
            Attributes attributes = manifest.getMainAttributes();
            String version = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            var path = getClass().getModule().getResourceAsStream("/common.jar");
            System.setProperty("arclight.version", version);
            var dir = Paths.get(".arclight", "mod_file");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            var mod = dir.resolve(version + ".jar");
            if (!Files.exists(mod) || Boolean.getBoolean("arclight.alwaysExtract")) {
                try (var files = Files.list(dir)) {
                    for (Path old : files.toList()) {
                        Files.delete(old);
                    }
                    Files.copy(path, mod);
                }
            }
            return mod;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        super.unlockClassPath(launcher);
        try {
            var field = launcher.getClass().getDeclaredField("unlocked");
            field.setAccessible(true);
            field.set(launcher, true);
            var ctor = launcher.loadIntoTarget("io.izzel.arclight.fabric.boot.FabricBootstrap").getConstructor();
            ((Consumer<FabricLauncher>) ctor.newInstance()).accept(launcher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}