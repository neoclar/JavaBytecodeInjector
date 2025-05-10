package com.neoclar.jbi.java;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.neoclar.jbi.java.exceptions.BytecodeInjectionException;
import com.neoclar.jbi.java.exceptions.LineIndexNotFoundException;
import com.neoclar.jbi.java.exceptions.OpcodeNotFoundException;
import com.neoclar.jbi.java.exceptions.VariableIndexNotFoundException;
import com.neoclar.jbi.java.utils.DefaultHashMap;
import com.neoclar.jbi.java.utils.Pair;

import java.util.ArrayList;
import java.util.Arrays;
public class ClassCombiner {
    public byte[] out;
    public PrintWriter resultLog;
    public PrintWriter sourceLog;
    DefaultHashMap<String, ArrayList<String>> anonymuses = new DefaultHashMap<String, ArrayList<String>>(ArrayList.class, null, null);
    private HashMap<String, String> modToSourceAccesses = new HashMap<String, String>();
    public ClassCombiner(byte[] bytecode, ArrayList<String> modsclasses, HashMap<String, String> modToSourceNames, HashMap<String, HashMap<String, String>> allAccesses, String logFilePath) throws BytecodeInjectionException, IOException, NoSuchFieldException, IllegalAccessException {
        try {
            File resultLogFile = new File(logFilePath+"result.txt");
            resultLogFile.getParentFile().mkdirs();
            resultLogFile.createNewFile();
            resultLog = new PrintWriter(resultLogFile, "UTF-8");
            // Очень много переменных
            ArrayList<ModClassReader> addReaders = new ArrayList<ModClassReader>();
            DefaultHashMap<ArrayList<String>, ArrayList<String>> delLines = new DefaultHashMap<ArrayList<String>, ArrayList<String>>(ArrayList.class, null, null); //addReader.getDel()
            int modID = 0;
            HashMap<String, Pair<String, ArrayList<String>>> modAccesses = new HashMap<String, Pair<String, ArrayList<String>>>();
            for (String addingClass: modsclasses) {
                File addLogFile = new File(logFilePath+addingClass.replaceFirst("/source/.*", "").replaceAll("/", ";")+".txt");
                addLogFile.createNewFile();
                PrintWriter addLog = new PrintWriter(addLogFile, "UTF-8");
                byte[] addbytecode = readAllBytes(getClass().getResource("/"+addingClass).openStream());
                ModClassReader addReader = new ModClassReader(addbytecode, modID, addingClass, modToSourceNames, addLog);
                addLog.close();
                modID++;
                addReaders.add(addReader);
                modAccesses.putAll(addReader.getAccessMethods());
                for (String anonymusClassID: addReader.getAnonymuses().keySet()) {
                    anonymuses.get(anonymusClassID).add(addReader.getAnonymuses().get(anonymusClassID)+".class");
                }
                HashMap<ArrayList<String>,ArrayList<String>> addDel = addReader.getDel();
                for (ArrayList<String> methodInfo: addDel.keySet()) {
                    for (String stringNumber: addDel.get(methodInfo)) {
                        delLines.get(methodInfo).add(stringNumber);
                    }
                }
            }
            ClassReader classReader = new ClassReader(bytecode);
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode,0);
            HashMap<ArrayList<String>, ArrayList<ArrayList<String>>> resultHashMap = new HashMap<ArrayList<String>, ArrayList<ArrayList<String>>>();
            HashMap<ArrayList<String>, HashMap<String, HashMap<Integer, Integer>>> offsets = new HashMap<ArrayList<String>, HashMap<String, HashMap<Integer, Integer>>>();
            ArrayList<ArrayList<String>> methods = new ArrayList<ArrayList<String>>();
            ArrayList<DefaultHashMap<ArrayList<String>,DefaultHashMap<String,ArrayList<ArrayList<String>>>>> addsLines = new ArrayList<DefaultHashMap<ArrayList<String>,DefaultHashMap<String,ArrayList<ArrayList<String>>>>>();
            for (ModClassReader addReader: addReaders) {
                for (ArrayList<String> methodInfoFromAdd: addReader.getAdd().keySet()) {
                    if (!methods.contains(methodInfoFromAdd)) {
                        methods.add(methodInfoFromAdd);
                    }
                }
                addsLines.add(addReader.getAdd());
            }
            File sourceLogFile = new File(logFilePath+"source.txt");
            sourceLogFile.createNewFile();
            sourceLog = new PrintWriter(sourceLogFile, "UTF-8");
            SourceClassReader reader = new SourceClassReader(bytecode, methods, sourceLog);
            sourceLog.close();
            DefaultHashMap<ArrayList<String>,ArrayList<ArrayList<String>>> originalbytes = reader.getByteCodeLines();
            HashMap<ArrayList<String>,MethodNode> methodNode = reader.getMethodNode();
            HashMap<String, String> sourceAccesses = reader.getAccessMethods();
            int newAccessIndex = 0;
            for (String modAccess : modAccesses.keySet()) {
                if (sourceAccesses.containsKey(modAccesses.get(modAccess).k)) {
                    modToSourceAccesses.put(modAccess, sourceAccesses.get(modAccesses.get(modAccess).k));
                }
                else {
                    while (sourceAccesses.containsValue("access$"+newAccessIndex)) {
                        newAccessIndex++;
                    }
                    ArrayList<String> newAccessMethodInfo = new ArrayList<String>(){};
                    newAccessMethodInfo.add("access$"+newAccessIndex);
                    newAccessMethodInfo.add(modAccesses.get(modAccess).v.get(1));
                    newAccessMethodInfo.add(modAccesses.get(modAccess).v.get(2));
                    newAccessMethodInfo.add(modAccesses.get(modAccess).v.get(3));
                    ArrayList<String> instructions = new ArrayList<String>();
                    instructions.add("LINENUMBER 0 L0");
                    instructions.addAll(modAccesses.get(modAccess).v.subList(4, modAccesses.get(modAccess).v.size()));
                    ArrayList<ArrayList<String>> instructionsArray = new ArrayList<ArrayList<String>>();
                    resultHashMap.put(newAccessMethodInfo, instructionsArray);
                    modToSourceAccesses.put(modAccess, "access$"+newAccessIndex);
                    newAccessIndex++;
                }
            }
            allAccesses.put(classNode.name, modToSourceAccesses);
            ClassNode readerClassNode = reader.getClassNode();
            // Создание методов
            for (ArrayList<String> methodInfo: methods) {
                // кучка переменных
                ArrayList<ArrayList<String>> mainLines = new ArrayList<ArrayList<String>>();
                DefaultHashMap<String, HashMap<String, String>> lineNumbers = new DefaultHashMap<String, HashMap<String, String>>(HashMap.class, new Class[]{}, new Object[]{});
                resultHashMap.put(methodInfo, new ArrayList<ArrayList<String>>());
                int lineInd = 0;
                for (int addLineInd = 0; addLineInd<addsLines.size(); addLineInd++) {
                    DefaultHashMap<String,ArrayList<ArrayList<String>>> addMethod = addsLines.get(addLineInd).getWithoutDefault(methodInfo);
                    offsets.put(methodInfo, new HashMap<String, HashMap<Integer,Integer>>());
                    // Добавление строк до оригинальных
                    if (addMethod!=null) {
                        ArrayList<ArrayList<String>> addLinesBefore = addMethod.getWithoutDefault("before");
                        if (addLinesBefore!=null) {
                            for(int addLineBeforeInd = 0; addLineBeforeInd<addLinesBefore.size(); addLineBeforeInd++) {
                                ArrayList<String> addLineBefore = addLinesBefore.get(addLineBeforeInd);
                                ArrayList<String> filteredAddLineBefore = addLineBefore;
                                lineNumbers.get("add"+addLineInd).put(addLineBefore.get(0).replaceAll(".+(?=L\\d+$).", ""), ""+lineInd);
                                filteredAddLineBefore.set(0, "LINENUMBER 0 L"+(lineInd));
                                for(int instructionInd = 1; instructionInd<addLineBefore.size(); instructionInd++) {
                                    String instruction = addLineBefore.get(instructionInd);
                                    if (instruction.matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                                        filteredAddLineBefore.set(instructionInd, instruction.replaceAll("(?=L\\d+)", "add"+addLineInd+";"));
                                    }
                                    else {
                                        filteredAddLineBefore.set(instructionInd, instruction.replaceFirst("(?=L\\d+$)", "add"+addLineInd+";"));
                                    }
                                }
                                if (offsets.get(methodInfo).get("before")==null) {offsets.get(methodInfo).put("before", new HashMap<Integer, Integer>());}
                                offsets.get(methodInfo).get("before").put(addLineBeforeInd, lineInd);
                                mainLines.add(filteredAddLineBefore);
                                lineInd++;
                            }
                        }
                    }
                }
                for (ArrayList<String> lineToResult: mainLines) {
                    for(int instructionInd = 0; instructionInd<lineToResult.size(); instructionInd++) {
                        if (lineToResult.get(instructionInd).matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                            for (int i=0; i<lineToResult.get(instructionInd).replaceAll("[^L]", "").length()-1; i++) {
                                String oldLineNumber = lineToResult.get(instructionInd).replaceAll("(?:LOOKUP|TABLE)SWITCH(?: (?:-?\\d+|default):L\\d+){"+i+"}(?: (?:-?\\d+|default):)?|(?: (?:-?\\d+|default):add\\d+;L\\d+){"+(lineToResult.get(instructionInd).replaceAll("[^L]", "").length()-2-i)+"}$", "");
                                lineToResult.set(instructionInd, lineToResult.get(instructionInd).replaceFirst("add\\d+;L\\d+", "L"+lineNumbers.get(oldLineNumber.split(";L")[0]).get(oldLineNumber.split(";L")[1])));
                            }
                        }
                        else {
                            String oldLineNumber = lineToResult.get(instructionInd).replaceAll(".+(?= .+;L\\d+$).", "");
                            if (oldLineNumber.matches(".+;L\\d+$")) {
                                lineToResult.set(instructionInd, lineToResult.get(instructionInd).replaceFirst("add\\d+;L\\d+$", "L"+lineNumbers.get(oldLineNumber.split(";L")[0]).get(oldLineNumber.split(";L")[1])));
                            }
                        }
                    }
                    resultHashMap.get(methodInfo).add(lineToResult);
                }
                // Добавление строк к оригинальным
                mainLines.clear();
                lineNumbers.clear();
                int originalOffset=0;
                for(int origInd = 0; origInd<originalbytes.get(methodInfo).size(); origInd++) {
                    boolean isInDelLines = false;
                    if (delLines.containsKey(methodInfo)) {
                        if (delLines.get(methodInfo).contains(""+origInd)) {
                            isInDelLines=true;
                        }
                    }
                    if (!isInDelLines) {
                        ArrayList<String> origLine = originalbytes.get(methodInfo).get(origInd);
                        if (origLine.size()!=0) {
                            ArrayList<String> filteredAddLine = origLine;
                            int oldLineNumber0 = Integer.parseInt(origLine.get(0).replaceAll(".+(?=L\\d+$).", ""));
                            lineNumbers.get("orig").put(origLine.get(0).replaceAll(".+(?=L\\d+$).", ""), ""+(lineInd+oldLineNumber0-originalOffset));
                            filteredAddLine.set(0, origLine.get(0).replaceFirst("\\d+$", ""+(lineInd+oldLineNumber0-originalOffset)));
                            for(int instructionInd = 1; instructionInd<origLine.size(); instructionInd++) {
                                if (origLine.get(instructionInd).matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                                    filteredAddLine.set(instructionInd, origLine.get(instructionInd).replaceAll("(?=L\\d+)", "orig;"));
                                }
                                else {
                                    filteredAddLine.set(instructionInd, origLine.get(instructionInd).replaceFirst("(?=L\\d+$)", "orig;"));
                                }
                            }
                            mainLines.add(filteredAddLine);
                            originalOffset++;
                            lineInd++;
                        }
                    }
                    else {
                        ArrayList<String> origLine = originalbytes.get(methodInfo).get(origInd);
                        if (origLine.size()!=0) {
                            ArrayList<String> filteredAddLine = new ArrayList<String>();
                            int oldLineNumber0 = Integer.parseInt(origLine.get(0).replaceAll(".+(?=L\\d+$).", ""));
                            filteredAddLine.add(origLine.get(0).replaceFirst("\\d+$", ""+(lineInd+oldLineNumber0-originalOffset)));
                            if (origLine.get(1).length()>5) {
                                if (origLine.get(1).substring(0, 5).equals("FRAME ")) {
                                    filteredAddLine.add(origLine.get(1));
                                }
                            }
                            mainLines.add(filteredAddLine);
                            originalOffset++;
                            lineInd++;
                        }
                    }
                    for (int addLineInd = 0; addLineInd<addsLines.size(); addLineInd++) {
                        int addOffset=0;
                        DefaultHashMap<String,ArrayList<ArrayList<String>>> addMethod = addsLines.get(addLineInd).getWithoutDefault(methodInfo);
                        if (addMethod!=null) {
                            if (addMethod.containsKey(""+origInd)) {
                                for (ArrayList<String> addLine: addMethod.get(""+origInd)) {
                                    ArrayList<String> filteredAddLine = addLine;
                                    int oldLineNumber0 = Integer.parseInt(addLine.get(0).replaceAll(".+(?=L\\d+$).", ""));
                                    lineNumbers.get("add"+addLineInd).put(addLine.get(0).replaceAll(".+(?=L\\d+$).", ""), ""+(lineInd+oldLineNumber0-addOffset));
                                    filteredAddLine.set(0, addLine.get(0).replaceFirst("\\d+$", ""+(lineInd+oldLineNumber0-addOffset)));
                                    for(int instructionInd = 1; instructionInd<addLine.size(); instructionInd++) {
                                        if (!addLine.get(instructionInd).matches("GOTO orig;L\\d+")) {
                                            if (addLine.get(instructionInd).matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                                                filteredAddLine.set(instructionInd, addLine.get(instructionInd).replaceAll("(?=L\\d+)", "add"+addLineInd+";"));
                                            }
                                            else {
                                                filteredAddLine.set(instructionInd, addLine.get(instructionInd).replaceFirst("(?=L\\d+$)", "add"+addLineInd+";"));
                                            }
                                        }
                                    }
                                    mainLines.add(filteredAddLine);
                                    lineInd++;
                                    addOffset++;
                                }
                            }
                        }
                    }
                }
                for (ArrayList<String> lineToResult: mainLines) {
                    for(int instructionInd = 0; instructionInd<lineToResult.size(); instructionInd++) {
                        if (lineToResult.get(instructionInd).matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                            for (int i=0; i<lineToResult.get(instructionInd).replaceAll("[^L]", "").length()-1; i++) {
                                String oldLineNumber = lineToResult.get(instructionInd).replaceAll("(?:LOOKUP|TABLE)SWITCH(?: (?:-?\\d+|default):L\\d+){"+i+"}(?: (?:-?\\d+|default):)?|(?: (?:-?\\d+|default):(?:add\\d+|orig);L\\d+){"+(lineToResult.get(instructionInd).replaceAll("[^L]", "").length()-2-i)+"}$", "");
                                lineToResult.set(instructionInd, lineToResult.get(instructionInd).replaceFirst("(?:add\\d+|orig);L\\d+", "L"+lineNumbers.get(oldLineNumber.split(";L")[0]).get(oldLineNumber.split(";L")[1])));
                            }
                        }
                        else {
                            String oldLineNumber = lineToResult.get(instructionInd).replaceAll(".+(?= .+;L\\d+$).", "");
                            if (oldLineNumber.matches(".+;L\\d+$")) {
                                lineToResult.set(instructionInd, lineToResult.get(instructionInd).replaceFirst("(?:add\\d+|orig);L\\d+$", "L"+lineNumbers.get(oldLineNumber.split(";L")[0]).get(oldLineNumber.split(";L")[1])));
                            }
                        }
                    }
                    resultHashMap.get(methodInfo).add(lineToResult);
                }
                mainLines.clear();
                lineNumbers.clear();
                // Добавление строк после оригинальных
                for (int addModInd = 0; addModInd<addsLines.size(); addModInd++) {
                    DefaultHashMap<String,ArrayList<ArrayList<String>>> addMethod = addsLines.get(addModInd).getWithoutDefault(methodInfo);
                    if (addMethod!=null) {



                        ArrayList<ArrayList<String>> addLinesAfter = addMethod.getWithoutDefault("after");
                        if (addLinesAfter!=null) {
                            for(int addLineAfterInd = 0; addLineAfterInd<addLinesAfter.size(); addLineAfterInd++) {
                                ArrayList<String> addLineAfter = addLinesAfter.get(addLineAfterInd);
                                ArrayList<String> filteredAddLineAfter = addLineAfter;
                                lineNumbers.get("add"+addModInd).put(addLineAfter.get(0).replaceAll(".+(?=L\\d+$).", ""), ""+lineInd);
                                filteredAddLineAfter.set(0, "LINENUMBER 0 L"+(lineInd));
                                for(int instructionInd = 1; instructionInd<addLineAfter.size(); instructionInd++) {
                                    String instruction = addLineAfter.get(instructionInd);
                                    if (instruction.matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                                        filteredAddLineAfter.set(instructionInd, instruction.replaceAll("(?=L\\d+)", "add"+addModInd+";"));
                                    }
                                    else {
                                        filteredAddLineAfter.set(instructionInd, instruction.replaceFirst("(?=L\\d+$)", "add"+addModInd+";"));
                                    }
                                }
                                if (offsets.get(methodInfo).get("after")==null) {offsets.get(methodInfo).put("after", new HashMap<Integer, Integer>());}
                                offsets.get(methodInfo).get("after").put(addLineAfterInd, lineInd);
                                mainLines.add(filteredAddLineAfter);
                                lineInd++;
                            }
                        }
                    }
                }
                for (ArrayList<String> lineToResult: mainLines) {
                    for(int instructionInd = 0; instructionInd<lineToResult.size(); instructionInd++) {
                        if (lineToResult.get(instructionInd).matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                            for (int i=0; i<lineToResult.get(instructionInd).replaceAll("[^L]", "").length()-1; i++) {
                                String oldLineNumber = lineToResult.get(instructionInd).replaceAll("(?:LOOKUP|TABLE)SWITCH(?: (?:-?\\d+|default):L\\d+){"+i+"}(?: (?:-?\\d+|default):)?|(?: (?:-?\\d+|default):add\\d+;L\\d+){"+(lineToResult.get(instructionInd).replaceAll("[^L]", "").length()-2-i)+"}$", "");
                                lineToResult.set(instructionInd, lineToResult.get(instructionInd).replaceFirst("add\\d+;L\\d+", "L"+lineNumbers.get(oldLineNumber.split(";L")[0]).get(oldLineNumber.split(";L")[1])));
                            }
                        }
                        else {
                            String oldLineNumber = lineToResult.get(instructionInd).replaceAll(".+(?= .+;L\\d+$).", "");
                            if (oldLineNumber.matches(".+;L\\d+$")) {
                                lineToResult.set(instructionInd, lineToResult.get(instructionInd).replaceFirst("add\\d+;L\\d+$", "L"+lineNumbers.get(oldLineNumber.split(";L")[0]).get(oldLineNumber.split(";L")[1])));
                            }
                        }
                    }
                    resultHashMap.get(methodInfo).add(lineToResult);
                }

            }
            String superClass = readerClassNode.superName;
            Set<String> interfaces = new HashSet<String>();
            for (String origInterface: readerClassNode.interfaces)
                interfaces.add(origInterface);
            HashMap<String, FieldNode> fields = new HashMap<String, FieldNode>();
            ArrayList<String> origFields = new ArrayList<String>();
            for (FieldNode field: readerClassNode.fields) {
                origFields.add(field.name);
            }
            ArrayList<String> addNames = new ArrayList<String>();
            ArrayList<ArrayList<String>> delMethods = new ArrayList<ArrayList<String>>();
            for (ModClassReader addReader: addReaders) {
                delMethods.addAll(addReader.getDelMethods());
                ClassNode addClassNode = addReader.getClassNode();
                addNames.add(addClassNode.name);
                for (String addInterface: addClassNode.interfaces)
                    interfaces.add(addInterface);
                for (FieldNode field: addClassNode.fields) {
                    if (!fields.containsKey(field.name)) {
                        if (!origFields.contains(field.name)) {
                            fields.put(field.name, field);
                        }
                    }
                }
            }
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
            classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC+Opcodes.ACC_SUPER, classNode.name, classNode.signature, superClass, (interfaces).toArray(new String[]{}));
            for (FieldNode field: fields.values()) {
                classWriter.visitField(field.access, field.name, field.desc, field.signature, field.value);
            }
            HashMap<ArrayList<String>, MethodVisitor> modifiedMethodVisitors = new HashMap<ArrayList<String>, MethodVisitor>();
            for (ArrayList<String> methodInfo: resultHashMap.keySet()) {
                resultLog.println(methodInfo);
                int i = 0;
                DefaultHashMap<String, Label> labels = new DefaultHashMap<String, Label>(Label.class, new Class[]{}, new Object[]{});
                MethodVisitor methodVisitor;
                if (!methodNode.containsKey(methodInfo)) {
                    methodVisitor = classWriter.visitMethod(Integer.parseInt(methodInfo.get(1)) /* access */, methodInfo.get(0) /* name */, methodInfo.get(2) /* desc */, methodInfo.get(3) /* signature */, null);
                }
                else {
                    methodVisitor = classWriter.visitMethod(Integer.parseInt(methodInfo.get(1)) /* access */, methodInfo.get(0) /* name */, methodInfo.get(2) /* desc */, methodInfo.get(3) /* signature */, (methodNode.get(methodInfo).exceptions).toArray(new String[]{}));
                }
                labels.put("start", new Label());
                int attrsSize = methodInfo.get(2).replaceAll("\\(|\\).+|L[^;]+|\\[", "").length();
                HashMap<String, Integer> variables = new HashMap<String, Integer>();
                HashMap<Integer, Integer> maxVariableInd = new HashMap<Integer, Integer>();
                int newVariableInt = attrsSize;
                for (int startVariableInd = 0; startVariableInd<=attrsSize; startVariableInd++) {
                    variables.put(""+startVariableInd, startVariableInd);
                }
                for(int methodLineInd = 0; methodLineInd<resultHashMap.get(methodInfo).size(); methodLineInd++) {
                    if (maxVariableInd.containsKey(methodLineInd)) {
                        newVariableInt = maxVariableInd.get(methodLineInd);
                    }
                    for (String instruction: resultHashMap.get(methodInfo).get(methodLineInd)) {
                        String[] codes;
                        if (instruction.startsWith("LDC"))
                            codes = instruction.split(" (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                        else
                            codes = instruction.split("(?: |\\.)(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                        if (codes.length==1) {
                            methodVisitor.visitInsn(Opcodes.class.getField(codes[0]).getInt(null));
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE
                        else if (Arrays.asList(new String[]{"INVOKEVIRTUAL", "INVOKESPECIAL", "INVOKESTATIC", "INVOKEINTERFACE"}).contains(codes[0])) {
                            if (modToSourceNames.keySet().contains(codes[1]))
                                codes[1]=modToSourceNames.get(codes[1]);
                            if (codes[2].matches("mod\\d+:access\\$\\d+")) {
                                HashMap<String, String> neededModToSourceAccesses = allAccesses.get(codes[1].replaceAll("\\.", "/"));
                                for (String modAccessName : neededModToSourceAccesses.keySet()) {
                                    if (codes[2].equals(modAccessName)) {
                                        codes[2] = neededModToSourceAccesses.get(modAccessName);
                                        break;
                                    }
                                }
                            }
                            if (codes[3].length()>2) {
                                for (String modToSourceName : modToSourceNames.keySet()) {
                                    codes[3]=codes[3].replaceFirst(modToSourceName, modToSourceNames.get(modToSourceName));
                                }
                            }
                            boolean isInterface = false;
                            if (codes.length==5) if (codes[4].equals("(itf)")) isInterface=true;
                            methodVisitor.visitMethodInsn(Opcodes.class.getField(codes[0]).getInt(null), codes[1], codes[2], codes[3], isInterface);
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // BIPUSH, SIPUSH, NEWARRAY
                        else if (Arrays.asList(new String[]{"BIPUSH", "SIPUSH", "NEWARRAY"}).contains(codes[0])) {
                            methodVisitor.visitIntInsn(Opcodes.class.getField(codes[0]).getInt(null), Integer.parseInt(codes[1]));
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // ILOAD, LLOAD, FLOAD, DLOAD, ALOAD or RET.
                        else if (Arrays.asList(new String[]{"ILOAD", "LLOAD", "FLOAD", "DLOAD", "ALOAD", "RET"}).contains(codes[0])) {
                            if (!variables.containsKey(codes[1])) {
                                // System.out.println(methodInfo);
                                throw new VariableIndexNotFoundException("Not found '"+codes[1]+"' in method "+methodInfo);
                            }
                            methodVisitor.visitIntInsn(Opcodes.class.getField(codes[0]).getInt(null), variables.get(codes[1]));
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // ISTORE, LSTORE, FSTORE, DSTORE, ASTORE.
                        else if (Arrays.asList(new String[]{"ISTORE", "LSTORE", "FSTORE", "DSTORE", "ASTORE"}).contains(codes[0])) {
                            if (variables.containsKey(codes[1])) {
                                codes[1]=""+variables.get(codes[1]);
                            }
                            else {
                                newVariableInt++;
                                variables.put(codes[1], newVariableInt);
                                codes[1]=""+newVariableInt;
                                maxVariableInd.put(methodLineInd, newVariableInt);
                            }
                            methodVisitor.visitIntInsn(Opcodes.class.getField(codes[0]).getInt(null), Integer.parseInt(codes[1]));
                            methodVisitor.visitLocalVariable("var"+methodLineInd, "Ljava/lang/String;", null, labels.get("start"), new Label(), Integer.parseInt(codes[1]));
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // NEW, ANEWARRAY, CHECKCAST or INSTANCEOF
                        else if (Arrays.asList(new String[]{"NEW", "ANEWARRAY", "CHECKCAST", "INSTANCEOF"}).contains(codes[0])) {
                            methodVisitor.visitTypeInsn(Opcodes.class.getField(codes[0]).getInt(null), codes[1]);
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // GETSTATIC, PUTSTATIC, GETFIELD or PUTFIELD.
                        else if (Arrays.asList(new String[]{"GETSTATIC", "PUTSTATIC", "GETFIELD", "PUTFIELD"}).contains(codes[0])) {
                            if (modToSourceNames.keySet().contains(codes[1])) {
                                codes[1]=modToSourceNames.get(codes[1]);
                            }
                            if (codes[3].length()>2) {
                                if (modToSourceNames.keySet().contains(codes[3].substring(1, codes[3].length()-1))) {
                                    codes[3]="L"+modToSourceNames.get(codes[3].substring(1, codes[3].length()-1))+";";
                                }
                            }
                            methodVisitor.visitFieldInsn(Opcodes.class.getField(codes[0]).getInt(null), codes[1], codes[2], codes[3]);
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        //  IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, GOTO, JSR, IFNULL or IFNONNULL.
                        else if (Arrays.asList(new String[]{"IFEQ", "IFNE", "IFLT", "IFGE", "IFGT", "IFLE", "IF_ICMPEQ", "IF_ICMPNE", "IF_ICMPLT", "IF_ICMPGE", "IF_ICMPGT", "IF_ICMPLE", "IF_ACMPEQ", "IF_ACMPNE", "GOTO", "JSR", "IFNULL", "IFNONNULL"}).contains(codes[0])) {
                            try {
                                int toLine = Integer.parseInt(instruction.replaceAll("(?!\\d+$).", ""));
                                if (toLine>methodLineInd) {
                                    maxVariableInd.put(toLine, newVariableInt);
                                }
                                methodVisitor.visitJumpInsn(Opcodes.class.getField(codes[0]).getInt(null), labels.get(codes[1]));
                                resultLog.println("    "+i+": "+String.join(" ", codes));
                                i++;
                                continue;
                            } catch (NumberFormatException e) {
                                throw new LineIndexNotFoundException("Unknown line index found in instruction '"+instruction+"' in method "+methodInfo);
                            }
                        }
                        // LDC
                        else if (codes[0].equals("LDC")) {
                            if ((""+codes[1].charAt(0)+codes[1].charAt(codes[1].length()-1)).equals("\"\""))
                                methodVisitor.visitLdcInsn(codes[1].substring(1, codes[1].length()-1).replaceAll("\\\\n", "\n"));
                            else if (codes[1].matches("-?\\d+\\.\\d*F$"))
                                methodVisitor.visitLdcInsn(Float.parseFloat(codes[1]));
                            else if (codes[1].matches("-?\\d+\\.\\d*D$"))
                                methodVisitor.visitLdcInsn(Double.parseDouble(codes[1]));
                            else if (codes[1].endsWith(".class"))
                                methodVisitor.visitLdcInsn(Type.getObjectType(codes[1].substring(1, codes[1].length()-7)));
                            else
                                methodVisitor.visitLdcInsn(codes[1]);
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // IINC
                        else if (codes[0].equals("IINC")) {
                            methodVisitor.visitIincInsn(Integer.parseInt(codes[1]), Integer.parseInt(codes[2]));
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // TABLESWITCH
                        else if (codes[0].equals("TABLESWITCH")) {
                            // resultLog.println("TABLESWITCH!!!");
                            int[] SwitchKeys = new int[codes.length-2];
                            Label[] SwitchLabels = new Label[codes.length-2];
                            for (int codeInd=1; codeInd<codes.length-1; codeInd++) {
                                SwitchKeys[codeInd-1] = Integer.parseInt(codes[codeInd].split(":")[0]);
                                SwitchLabels[codeInd-1] = labels.get(codes[codeInd].split(":")[1]);
                            }
                            i++;
                            methodVisitor.visitTableSwitchInsn(SwitchKeys[0], SwitchKeys[SwitchKeys.length-1], labels.get(codes[codes.length-1].split(":")[1]), SwitchLabels);
                            continue;
                        }
                        // LOOKUPSWITCH
                        else if (codes[0].equals("LOOKUPSWITCH")) {
                            // resultLog.println("LOOKUPSWITCH!!!");
                            int[] SwitchKeys = new int[codes.length-2];
                            Label[] SwitchLabels = new Label[codes.length-2];
                            for (int codeInd=1; codeInd<codes.length-1; codeInd++) {
                                SwitchKeys[codeInd-1] = Integer.parseInt(codes[codeInd].split(":")[0]);
                                SwitchLabels[codeInd-1] = labels.get(codes[codeInd].split(":")[1]);
                            }
                            methodVisitor.visitLookupSwitchInsn(labels.get(codes[codes.length-1].split(":")[1]), SwitchKeys, SwitchLabels);
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // MULTIANEWARRAY
                        else if (codes[0].equals("MULTIANEWARRAY")) {
                            methodVisitor.visitMultiANewArrayInsn(codes[1], Integer.parseInt(codes[2]));
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        // LINENUMBER
                        else if (codes[0].equals("LINENUMBER")) {
                            methodVisitor.visitLabel(labels.get(codes[2]));
                            methodVisitor.visitLineNumber(Integer.parseInt(codes[1]), labels.get(codes[2]));
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;
                            continue;
                        }
                        else if (codes[0].equals("FRAME")) {
                            int frameType=0;
                            methodVisitor.visitFrame(frameType, 0, null, 0, null);
                            resultLog.println("    "+i+": "+String.join(" ", codes));
                            i++;                        }
                        else {
                            throw new OpcodeNotFoundException("Unknown instruction '"+instruction+"' in method "+methodInfo);
                        }
                    }
                }
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
                modifiedMethodVisitors.put(methodInfo, methodVisitor);
                resultLog.println("\n");
            }
            classReader.accept(new ClassCreator(classWriter, modifiedMethodVisitors, interfaces, delMethods), ClassReader.EXPAND_FRAMES);
            out = classWriter.toByteArray();
            resultLog.close();
        } catch (Exception e) {
            e.printStackTrace();
            resultLog.close();
            throw e;
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
    public DefaultHashMap<String, ArrayList<String>> getAnonymuses() {
        return anonymuses;
    }
}