package com.neoclar.jbi.java;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.neoclar.jbi.java.exceptions.BytecodeInjectionException;
public class CustomClassLoader extends ClassLoader {
    private final ArrayList<String> FORBIDDEN_PKG_PREFIXs = new ArrayList<String>();
    private final ArrayList<String> FORBIDDEN_PKGs = new ArrayList<String>();
    public HashMap<String, ArrayList<String>> modifiedFiles = new HashMap<String, ArrayList<String>>();
    private HashMap<String, String> modToSourceNames = new HashMap<String, String>();
    private HashMap<String, HashMap<String, String>> accesses = new HashMap<String, HashMap<String, String>>();
    public CustomClassLoader(ClassLoader parent) {
        super(parent);
        FORBIDDEN_PKGs.add("com.neoclar.mewnforge.java.CustomClassLoader");
        FORBIDDEN_PKG_PREFIXs.add("java.");
        FORBIDDEN_PKG_PREFIXs.add("jdk.");
        FORBIDDEN_PKG_PREFIXs.add("sun.");
        // try {
        //     this.loadClass("");
        // } catch (ClassNotFoundException e) {
        //     e.printStackTrace();
        // }
        System.out.println("Init classLoader start");
        try {
            allSourceChanged();
            // System.out.println(modifiedFiles);
            // System.out.println("Init classLoader end");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> c = findLoadedClass(name);
        if (c != null)
            return c;
        boolean startWith = true;
        if (FORBIDDEN_PKGs.contains(name))
                startWith = false;
        for (String FORBIDDEN_PKG_PREFIX: FORBIDDEN_PKG_PREFIXs)
            if (name.startsWith(FORBIDDEN_PKG_PREFIX))
                startWith = false;
        if (startWith) {
            c = findClass(name);
            if (c != null)
                return c;
        }
        return super.loadClass(name);
    }
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String classFile = "/"+name.replace('.', "/".charAt(0)) + ".class";
        // System.out.println(classFile);
        try (InputStream inputStream = getClass().getResource(classFile).openStream()) {
            if (inputStream == null)
                throw new ClassNotFoundException();
            byte[] bytecode = readAllBytes(inputStream);
            ArrayList<String> modsclasses = modifiedFiles.get(name+".class");
            if (modsclasses!=null) {
                ClassCombiner ccw = new ClassCombiner(bytecode, modsclasses, modToSourceNames, accesses, "bytecode"+classFile.substring(0, classFile.length()-6)+"/");
                // if (classFile.equals("com/cairn4/moonbase/ui/HudInventory")) {int v = 0; v = 1/v;}
                for (String anonymusClassID: ccw.getAnonymuses().keySet()) {
                    modifiedFiles.put(name+"$"+anonymusClassID+".class", ccw.anonymuses.get(anonymusClassID));
                    for (String modName : ccw.anonymuses.get(anonymusClassID)) {
                        modToSourceNames.put(modName.replaceAll("\\.", "/").replaceFirst("/class$", ""), name.replaceAll("\\.", "/")+"$"+anonymusClassID);
                    }
                }
                System.out.println("[ClassLoader] Loaded "+classFile);
                bytecode = ccw.out;
            }
            return defineClass(name, bytecode, 0, bytecode.length);
        } catch (IOException | NoSuchFieldException | IllegalAccessException | BytecodeInjectionException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            throw new ClassNotFoundException();
        }
    }
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        int nextValue;
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        while ((nextValue = inputStream.read()) != -1) {
            byteStream.write(nextValue);
        }
        return byteStream.toByteArray();
    }
    public static String getParentDirPath(String fileOrDirPath) {
        boolean endsWithSlash = fileOrDirPath.endsWith(File.separator);
        return fileOrDirPath.substring(0, fileOrDirPath.lastIndexOf(File.separatorChar, endsWithSlash ? fileOrDirPath.length() - 2 : fileOrDirPath.length() - 1));
    }
    private void allSourceChanged() throws IOException{
        CodeSource src = this.getClass().getProtectionDomain().getCodeSource();
        if (src != null) {
            URL jar = src.getLocation();
            try (Stream<Path> paths = Files.walk(Paths.get(jar.toURI()).getParent())) {
                for (Path filePath: paths.filter(Files::isRegularFile).collect(Collectors.toList())){
                    ZipInputStream zip = new ZipInputStream(Files.newInputStream(filePath));
                    while(true) {
                        ZipEntry e = zip.getNextEntry();
                        if (e == null)
                        break;
                        String name = e.getName();
                        if (name.matches(".+/source/.+\\.class")) {
                            if (!name.matches(".+\\$\\d+\\.class")) {
                                ArrayList<String> toHashMap = modifiedFiles.get(name.split("/source/")[1].replace("/", "."));
                                if (toHashMap==null){
                                    toHashMap = new ArrayList<String>();
                                }
                                toHashMap.add(name);
                                modifiedFiles.put(name.split("/source/")[1].replace("/", "."), toHashMap);
                            }
                        }
                    }
                }
            } catch (URISyntaxException e) {
                System.out.println(e);
            }
        }
        for (String sourceName : modifiedFiles.keySet()) {
            for (String modName : modifiedFiles.get(sourceName)) {
                modToSourceNames.put(modName.replaceFirst(".class$", ""), sourceName.replaceAll("\\.", "/").replaceFirst("/class$", ""));
            }
        }
    }
}