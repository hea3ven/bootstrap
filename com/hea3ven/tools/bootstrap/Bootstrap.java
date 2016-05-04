package com.hea3ven.tools.bootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.launchwrapper.Launch;

import net.minecraftforge.fml.common.versioning.ComparableVersion;
import net.minecraftforge.fml.relauncher.FMLRelaunchLog;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class Bootstrap {

	private static final Logger logger = LogManager.getLogger("H3NTBootstrap.Bootstrap");

	public static final String version = "1.1.1";
	private static boolean init = false;

	private Map<String, LibEntry> libs = Maps.newHashMap();

	@Deprecated
	public static void require(String modId, String versionPattern) {
		throw new BootstrapError("Mod " + modId + " is using an old version of the bootstrap");
	}

	public static void init() {
		if (init)
			return;
		logger.info("initializing");
		init = true;

		Bootstrap bs = new Bootstrap();
		bs.discover();

		logger.info("loading libraries");
		bs.load();
	}

	private static boolean matches(String versionPattern, String version) {
		return Pattern.matches(Pattern.quote(versionPattern).replace("x", "\\E[0-9]+\\Q"), version);
	}

	private static Path getLibsDir() {
		URL jarUrl = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();
		if (!jarUrl.getProtocol().equals("jar"))
			return null;
		String jarFilePath;
		try {
			jarFilePath = Paths.get(new URI(jarUrl.getPath())).toString();
		} catch (URISyntaxException e) {
			throw new BootstrapError("Could not get the current jar's path", e);
		}
		if (jarFilePath.lastIndexOf('!') != -1)
			jarFilePath = jarFilePath.substring(0, jarFilePath.lastIndexOf('!'));
		Path jarDir = Paths.get(jarFilePath).getParent();
		return jarDir.resolve("h3ntlibs");
	}

	private void checkVersion(String modId, String versionPattern) {
		if (!matches(versionPattern, version))
			throw new RuntimeException("Mod '" + modId + "' requires Boostrap version " + versionPattern +
					", but the current loaded version is " + version);
	}

	private void addLib(String modid, File zipFile, String name, String version, String versionPattern) {
		if (!libs.containsKey(name))
			libs.put(name, new LibEntry(name));
		libs.get(name).add(modid, zipFile, new ComparableVersion(version), versionPattern);
	}

	private void discover() {
		File mcDir = ReflectionHelper.getPrivateValue(FMLRelaunchLog.class, null, "minecraftHome");
		Path modsDir = Paths.get(mcDir.toString(), "mods");

		String mcVersion = getMinecraftVersion();
		discoverFromDir(modsDir, mcVersion);
	}

	private void discoverFromDir(Path modsDir, String mcVersion) {
		try {
			DirectoryStream<Path> modDirs = Files.newDirectoryStream(modsDir);
			for (Path modPath : modDirs) {
				if (!Files.isRegularFile(modPath)) {
					if (modPath.getFileName().toString().equals(mcVersion))
						discoverFromDir(modPath, mcVersion);
				} else {
					discoverFromZip(modPath);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Error discovering mods from " + modsDir, e);
		}
	}

	private void discoverFromZip(Path modPath) {
		try (ZipFile jarZip = new ZipFile(modPath.toFile())) {
			ZipEntry manifestEntry = jarZip.getEntry("META-INF/MANIFEST.MF");
			if (manifestEntry != null) {
				String bootstrapPath = getBootstrapPathFromManifest(jarZip, manifestEntry);
				if (bootstrapPath == null)
					return;

				ZipEntry bootstrapEntry = jarZip.getEntry(bootstrapPath);
				if (bootstrapEntry == null) {
					logger.warn(
							"Could not get " + bootstrapPath + " from " + modPath.getFileName().toString());
					return;
				}
				if (bootstrapEntry.isDirectory())
					return;

				InputStreamReader reader =
						new InputStreamReader(jarZip.getInputStream(bootstrapEntry));
				JsonObject bsObj = new Gson().fromJson(reader, JsonObject.class);

				String modid = bsObj.get("modid").getAsString();
				checkVersion(modid, bsObj.get("required").getAsString());

				JsonObject libs = bsObj.getAsJsonObject("libs");
				for (Entry<String, JsonElement> libEntry : libs.entrySet()) {
					JsonObject lib = libEntry.getValue().getAsJsonObject();
					String version = lib.get("version").getAsString();
					String required = lib.get("required").getAsString();
					logger.debug("mod {} provides {} version {} and requires version {}", modid,
							libEntry.getKey(), version, required);
					addLib(modid, modPath.toFile(), libEntry.getKey(), version, required);
				}
			}
		} catch (IOException e) {
			logger.error("Could not open the jar file", e);
		}
	}

	private String getBootstrapPathFromManifest(ZipFile zip, ZipEntry manifestEntry) {
		Properties props = new Properties();
		try {
			props.load(zip.getInputStream(manifestEntry));
		} catch (IOException e) {
			return null;
		}
		if (props.containsKey("H3NTBootstrap"))
			return props.getProperty("H3NTBootstrap");
		return null;
	}

	private String getMinecraftVersion() {
		InputStream stream =
				Launch.classLoader.getResourceAsStream("net/minecraft/server/MinecraftServer.class");
		ClassNode serverClass = new ClassNode();
		try {
			new ClassReader(stream).accept(serverClass, 0);
		} catch (IOException e) {
			throw new RuntimeException("could not detect the running version");
		}
		VersionScannerVisitor versionScanner = new VersionScannerVisitor();
		for (MethodNode method : serverClass.methods)
			method.accept(versionScanner);
		if (versionScanner.version == null)
			throw new RuntimeException("could not detect the running version");
		return versionScanner.version;
	}

	private void load() {
		Path libsDir = getLibsDir();
		if (libsDir == null)
			return;
		logger.debug("extracting libraries to {}", libsDir.toString());

		for (LibEntry entry : libs.values()) {
			entry.checkCompatibility();
		}

		for (LibEntry entry : libs.values()) {
			loadLib(libsDir, entry);
		}
	}

	private void loadLib(Path libsDir, LibEntry entry) {
		String libJarName = entry.getName() + "-" + entry.getVersion() + ".jar";
		logger.debug("loading library {}", libJarName);

		try {
			if (!Files.exists(libsDir))
				Files.createDirectory(libsDir);

			if (!Files.exists(libsDir.resolve(libJarName))) {
				// Remove other versions
				try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(libsDir)) {
					for (Path path : dirStream) {
						if (path.getFileName().toString().startsWith(entry.getName() + "-") &&
								path.toString().endsWith(".jar")) {
							logger.debug("removing old file {}", path.toString());
							Files.delete(path);
						}
					}
				}

				try (ZipFile zip = new ZipFile(entry.getZipFile())) {
					ZipEntry zipEntry = zip.getEntry(
							String.format("libs/%s-%s.jar", entry.getName(), entry.getVersion()));
					Files.copy(zip.getInputStream(zipEntry), libsDir.resolve(libJarName));
				}
			}
		} catch (IOException e) {
			throw new BootstrapError(
					String.format("Failed to extract library %s-%s", entry.getName(), entry.getVersion()), e);
		}
		try {
			logger.debug("adding {} to classpath", libsDir.resolve(libJarName).toUri().toURL());
			Launch.classLoader.addURL(libsDir.resolve(libJarName).toUri().toURL());
		} catch (MalformedURLException e) {
			throw new BootstrapError(
					String.format("Failed to load library %s-%s", entry.getName(), entry.getVersion()), e);
		}
	}

	public static class BootstrapError extends RuntimeException {
		public BootstrapError(String msg) {
			super(msg);
		}

		public BootstrapError(String msg, Exception ex) {
			super(msg, ex);
		}
	}

	private class LibEntry {
		private final String name;
		private ComparableVersion latestVersion;
		private final Map<String, String> requests = Maps.newHashMap();
		private File zipFile;

		public LibEntry(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public String getVersion() {
			return latestVersion.toString();
		}

		public File getZipFile() {
			return zipFile;
		}

		public void add(String modid, File zipFile, ComparableVersion version, String versionPattern) {
			if (latestVersion == null || latestVersion.compareTo(version) < 0) {
				latestVersion = version;
				this.zipFile = zipFile;
			}
			requests.put(modid, versionPattern);
		}

		public void checkCompatibility() {
			for (Entry<String, String> entry : requests.entrySet()) {
				if (!matches(entry.getValue(), latestVersion.toString()))
					throw new BootstrapError(
							"Mod '" + entry.getKey() + "' requires library " + name + " with version " +
									entry.getValue() + " but the version " + latestVersion.toString() +
									" is loaded");
			}
		}
	}

	class VersionScannerVisitor extends MethodVisitor {

		public VersionScannerVisitor() {
			super(Opcodes.ASM4);
		}

		public String version = null;

		Pattern normalVer = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?$");
		Pattern snapVer = Pattern.compile("^\\d\\dw\\d+[a-z]$");

		@Override
		public void visitLdcInsn(Object cst) {
			if (cst instanceof String) {
				String potentialVersion = (String) cst;
				if (normalVer.matcher(potentialVersion).matches())
					setVersion(potentialVersion);
				else if (snapVer.matcher(potentialVersion).matches())
					setVersion(potentialVersion);
			}
			super.visitLdcInsn(cst);
		}

		private void setVersion(String potentialVersion) {
			if (version != null && !version.equals(potentialVersion))
				throw new RuntimeException("could not detect running version, multiple matches");
			version = potentialVersion;
		}
	}
}
