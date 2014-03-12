package mattmess.tweakloader;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TweakLoaderTweaker implements ITweaker {

	private List<String> argsList = new ArrayList<String>();
	private Logger log = LogManager.getLogger();
	private File gameDir;
	private File assetsDir;
	private File workingDir = WorkingDirectory.getWorkingDirectory();
	private int tweakNo = 0;

	@Override
	public void acceptOptions(List<String> args, File gameDir, File assetsDir,
			String profile) {
		log.info("Using profile: " + profile);
		this.gameDir = gameDir;
		this.assetsDir = assetsDir;
		this.argsList.add("--version");
		this.argsList.add(profile);
		this.argsList.add("--assetsDir");
		this.argsList.add(assetsDir.getPath());
		this.argsList.add("--gameDir");
		this.argsList.add(gameDir.getPath());
		this.argsList.addAll(args);
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader classLoader) {
		File modsDir = this.gameDir.toPath().resolve("mods").toFile();
		File[] externalJars = null;
		externalJars = modsDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				if (name.endsWith("jar"))
					return true;
				return false;
			}
		});
		List<File> classList = new ArrayList<File>();

		// Convert urls to files
		for (URL url : classLoader.getURLs()) {
			File file = new File(url.getFile());
			classList.add(file);
		}
		File[] classPath = classList.toArray(new File[0]);

		// Combine mods and classpath libraries.
		File[] classFiles = ArrayUtils.addAll(externalJars, classPath);

		// Find tweaks
		log.info("Looking for tweaks in classpath and mods folder.");
		findTweaks(classFiles, classLoader);

		// Prints the files for debugging purposes
		log.info("Loaded Libraries:");
		for (URL url : classLoader.getURLs()) {
			log.info(url.getPath());
		}
	}

	@Override
	public String[] getLaunchArguments() {
		if (tweakNo > 0)
			return new String[] {};

		return argsList.toArray(new String[0]);
	}

	@Override
	public String getLaunchTarget() {
		return "net.minecraft.client.main.Main";
	}

	private URL[] findTweaks(File[] classFiles, LaunchClassLoader classLoader) {
		List<URL> urlList = new ArrayList<URL>();
		for (File file : classFiles) {
			if (file.isDirectory() || !file.getName().endsWith(".jar"))
				return null;
			JarFile jar = null;
			try {
				jar = new JarFile(file);
				Manifest manifest = jar.getManifest();
				String tweak = manifest.getMainAttributes().getValue(
						"TweakClass");
				String classpath = manifest.getMainAttributes().getValue(
						"Class-Path");
				if (tweak != null) {
					if (tweak.contains("Forge"))
						tweak = tweak.replace("Forge", "");

					log.info("Found tweak class " + tweak);
					log.info("Adding " + file.getName() + " to classpath.");
					if (classpath != null) {
						log.info("Adding dependencies for " + file.getName());
						for (String s : classpath.split(" ")) {
							// Prevents loading of the minecraft_server jar when
							// loading Forge.
							if (!s.contains("server")) {
								log.info("Adding required library: "
										+ s.split("/")[s.split("/").length - 1]);
								URL library = this.workingDir.toPath()
										.resolve(s).toUri().toURL();
								addToClassPath(library, classLoader);
							}
						}
					}
					this.addToClassPath(file.toURI().toURL(), classLoader);
					this.injectTweak(tweak);
					this.tweakNo++;

				}

				jar.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return urlList.toArray(new URL[0]);
	}

	public void injectTweak(String tweakClassName) {
		List<String> tweakClasses = (List<String>) Launch.blackboard
				.get("TweakClasses");
		tweakClasses.add(tweakClassName);
	}

	public void addToClassPath(URL url, LaunchClassLoader classLoader) {
		classLoader.addURL(url);
		// Ensures every class is loaded
		JarFile jar = null;
		try {
			jar = new JarFile(url.getFile());
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String clazz = entry.getName();
				if (clazz.endsWith(".class")) {
					String clazzName = clazz.replace(".class", "").replace("/",
							".");
					classLoader.findClass(clazzName);
				}
			}

			jar.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			log.warn("Unable to load library: " + url.getPath());
			e.printStackTrace();
		} finally {
			try {
				jar.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
