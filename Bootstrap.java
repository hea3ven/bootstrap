package com.hea3ven.tweaks.bootstrap;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Throwables;

import net.minecraft.launchwrapper.Launch;

public class Bootstrap {

	protected static Path getLibsDir() {
		URL jarUrl = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();
		String jarFilePath = null;
		try {
			jarFilePath = new URL(jarUrl.getPath()).getPath();
		} catch (MalformedURLException e) {
			Throwables.propagate(e);
		}
		Path libsDir = FileSystems
				.getDefault()
				.getPath(jarFilePath.substring(0, jarFilePath.lastIndexOf('!')))
				.getParent()
				.resolve("h3ntlibs");
		return libsDir;
	}

	public static void initLib(String name, String version) {
		Path libsDir = getLibsDir();
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
			Launch.classLoader
					.addURL(new URL("file", null, libsDir.resolve(libJarName).toString()));
		} catch (MalformedURLException e) {
			Throwables.propagate(e);
		}
	}

}
