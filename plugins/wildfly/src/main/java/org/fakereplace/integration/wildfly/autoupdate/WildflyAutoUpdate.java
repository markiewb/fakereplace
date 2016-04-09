package org.fakereplace.integration.wildfly.autoupdate;

import org.fakereplace.core.Agent;
import org.fakereplace.replacement.AddedClass;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class that is responsible for handling the wildfly automatic updates. Automatic updates are triggered by web requests,
 *
 * @author Stuart Douglas
 */
public class WildflyAutoUpdate {


    public static final String FAKEREPLACE_SOURCE_PATHS = "fakereplace.source-paths.";


    private static final Map<String, Long> replacedTimestamps = new HashMap<>();
    public static final String DEPLOYMENT = "deployment.";

    public static synchronized void runUpdate(ModuleClassLoader classLoader) {

        String moduleName = classLoader.getModule().getIdentifier().getName();
        if (moduleName.startsWith(DEPLOYMENT)) {
            moduleName = moduleName.substring(DEPLOYMENT.length());
        }

        String sourcePaths = System.getProperty(FAKEREPLACE_SOURCE_PATHS + moduleName);
        if (sourcePaths == null) {
            return;
        }
        List<Path> paths = Arrays.asList(sourcePaths.split(",")).stream().map((s) -> Paths.get(s)).collect(Collectors.toList());

        try {
            for (Path base : paths) {
                final Map<String, Long> timestamps = new HashMap<>();
                scan(base, base, timestamps);
                List<String> toUpdate = new ArrayList<>();
                List<String> added = new ArrayList<>();
                List<String> replace = new ArrayList<>();
                for (Map.Entry<String, Long> entry : timestamps.entrySet()) {
                    String name = entry.getKey();
                    if (name.endsWith(".java")) {
                        String baseName = name.substring(0, name.length() - 5);
                        Long last = replacedTimestamps.get(baseName);
                        if (last != null) {
                            if (last < entry.getValue()) {
                                toUpdate.add(baseName);
                                replacedTimestamps.put(baseName, entry.getValue());
                                replace.add(baseName);
                            }
                        } else {
                            URL res = classLoader.getResource(baseName + ".class");
                            if (res != null) {
                                URLConnection con = res.openConnection();
                                long lm = con.getLastModified();
                                if (lm < entry.getValue()) {
                                    toUpdate.add(baseName);
                                    replacedTimestamps.put(baseName, entry.getValue());
                                    replace.add(baseName);
                                }
                            } else {
                                toUpdate.add(baseName);
                                replacedTimestamps.put(baseName, entry.getValue());
                                added.add(baseName);
                            }
                        }
                    }
                }
                if (!toUpdate.isEmpty()) {
                    System.out.println("Fakereplace detected the following source files have been changed: " + toUpdate);
                    ClassLoaderCompiler compiler = new ClassLoaderCompiler(classLoader, base, toUpdate);
                    compiler.compile();
                    AddedClass[] addedClass = new AddedClass[added.size()];
                    for (int i = 0; i < added.size(); ++i) {
                        String className = added.get(i);
                        addedClass[i] = new AddedClass(className, compiler.getOutput().get(className).toByteArray(), classLoader);
                    }
                    ClassDefinition[] classDefinition = new ClassDefinition[replace.size()];
                    for (int i = 0; i < replace.size(); ++i) {
                        String className = replace.get(i);
                        classDefinition[i] = new ClassDefinition(classLoader.loadClass(className.replace("/", ".")), compiler.getOutput().get(className).toByteArray());
                    }
                    Agent.redefine(classDefinition, addedClass);
                }
            }
        } catch (Exception e) {
            System.err.println("Check for updated classes failed");
            e.printStackTrace();
        } finally {
            //something in the compiler clears the TCCL, fix it up
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    private static void scan(Path base, Path path, Map<String, Long> timestamps) {
        try {
            if (Files.isDirectory(path)) {
                Files.list(path).forEach((s) -> scan(base, s, timestamps));
            } else {
                String rel = base.relativize(path).toString();
                timestamps.put(rel, Files.getLastModifiedTime(path).toMillis());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static Module getModule() {
        return null;
    }

}