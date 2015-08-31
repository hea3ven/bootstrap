package com.hea3ven.tweaks.bootstrap;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.base.Throwables;

import net.minecraft.launchwrapper.Launch;

public class Bootstrap {

	protected static Path getLibsDir() {
		URL jarUrl = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();
		if (!jarUrl.getProtocol().equals("jar"))
			return null;
		String jarFilePath = null;
		try {
			jarFilePath = Paths.get(new URI(jarUrl.getPath())).toString();
		} catch (URISyntaxException e) {
			Throwables.propagate(e);
		}
		if (jarFilePath.lastIndexOf('!') != -1)
			jarFilePath = jarFilePath.substring(0, jarFilePath.lastIndexOf('!'));
		Path jarDir = Paths.get(jarFilePath).getParent();
		return jarDir.resolve("h3ntlibs");
	}

	public static void initLib(String name, String version) {
		Path libsDir = getLibsDir();
		if (libsDir == null)
			return;
		String libJarName = name + "-" + version + ".jar";

		try {
			if (!Files.exists(libsDir))
				Files.createDirectory(libsDir);

			// TODO: remove old versions

			if (!Files.exists(libsDir.resolve(libJarName)))
				Files.copy(Bootstrap.class.getResourceAsStream("/libs/" + libJarName),
						libsDir.resolve(libJarName));
		} catch (IOException e) {
			Throwables.propagate(e);
		}
		try {
			Launch.classLoader.addURL(libsDir.resolve(libJarName).toUri().toURL());
		} catch (MalformedURLException e) {
			Throwables.propagate(e);
		}
	}
}
