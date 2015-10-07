package com.hea3ven.tools.bootstrap;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

import net.minecraft.launchwrapper.Launch;

public class Bootstrap {

	public static String version = "1.0.0";

	private static Map<String, String> loadedLibs = Maps.newHashMap();

	public static void require(String modId, String versionPattern) {
		if (!matches(versionPattern, version))
			throw new RuntimeException("Mod '" + modId + "' requires Boostrap version "
					+ versionPattern + ", but the current loaded version is " + version);
	}

	private static boolean matches(String versionPattern, String version) {
		return Pattern.matches(Pattern.quote(versionPattern).replace("x", "\\E[0-9]+\\Q"), version);
	}

	private static Path getLibsDir() {
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

	public static void initLib(String modId, String name, String version, String versionPattern) {
		// Check if the library is already loaded
		if (loadedLibs.containsKey(name)) {
			if (matches(versionPattern, loadedLibs.get(name)))
				return;
			else
				throw new RuntimeException("Mod '" + modId + "' requires library " + name
						+ " with version " + versionPattern + " but the version "
						+ loadedLibs.get(name) + " is loaded");
		}

		Path libsDir = getLibsDir();
		if (libsDir == null)
			return;
		String libJarName = name + "-" + version + ".jar";

		try {
			if (!Files.exists(libsDir))
				Files.createDirectory(libsDir);

			if (!Files.exists(libsDir.resolve(libJarName))) {
				// Remove other versions
				try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(libsDir)) {
					for (Path path : dirStream) {
						if (path.startsWith(name + "-") && path.endsWith(".jar")) {
							Files.delete(path);
						}
					}
				}

				Files.copy(Bootstrap.class.getResourceAsStream("/libs/" + libJarName),
						libsDir.resolve(libJarName));
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to extract library " + name + "-" + version
					+ " for mod '" + modId + "'", e);
		}
		try {
			Launch.classLoader.addURL(libsDir.resolve(libJarName).toUri().toURL());
			loadedLibs.put(name, version);
		} catch (MalformedURLException e) {
			throw new RuntimeException(
					"Failed to load library " + name + "-" + version + " for mod '" + modId + "'",
					e);
		}
	}
}
