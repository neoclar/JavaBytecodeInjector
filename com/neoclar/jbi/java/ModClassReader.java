package com.neoclar.jbi.java;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import com.neoclar.jbi.java.utils.DefaultHashMap;
import com.neoclar.jbi.java.utils.Pair;

import java.util.ArrayList;
import java.util.Arrays;
public class ModClassReader {
    private static Printer printer = new Textifier();
    private static TraceMethodVisitor mp = new TraceMethodVisitor(printer);
    ClassNode classNode = new ClassNode();
    HashMap<ArrayList<String>,ArrayList<String>> modDel = new HashMap<ArrayList<String>,ArrayList<String>>();
    DefaultHashMap<ArrayList<String>,DefaultHashMap<String,ArrayList<ArrayList<String>>>> modAdd = new DefaultHashMap<ArrayList<String>,DefaultHashMap<String,ArrayList<ArrayList<String>>>>(DefaultHashMap.class, new Class[]{Class[].class.getClass(), Class[].class, Object[].class}, new Object[]{ArrayList.class, new Class[]{}, new Object[]{}});
    ArrayList<ArrayList<String>> delMethods = new ArrayList<ArrayList<String>>();
    private HashMap<String, Pair<String, ArrayList<String>>> accessMethods = new HashMap<String, Pair<String, ArrayList<String>>>();
    HashMap<String, String> anonymuses = new HashMap<String, String>();
    String modID;
    String className;
    HashMap<String, String> modToSourceNames;
    PrintWriter addLog;
    public ModClassReader(byte[] bytes, Integer modIDint, String name, HashMap<String, String> modToSourceNames, PrintWriter addLog) {
        modID = "mod"+modIDint+":";
        className = name.split("/")[name.split("/").length-1];
        DefaultHashMap<ArrayList<String>,ArrayList<ArrayList<String>>> byteCodeLines = new DefaultHashMap<ArrayList<String>,ArrayList<ArrayList<String>>>(ArrayList.class, new Class[]{}, new Object[]{});
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode,0);
        final List<MethodNode> methods = classNode.methods;
        int lineInd;
        for(MethodNode m: methods){
            ArrayList<String> lineInstructions = new ArrayList<String>();
            HashMap<String, String> lineNumbers = new HashMap<String, String>();
            ArrayList<String> methodInfo = new ArrayList<String>(){};
            methodInfo.add(m.name);
            methodInfo.add(""+m.access);
            methodInfo.add(m.desc);
            methodInfo.add(m.signature);
            InsnList inList = m.instructions;
            addLog.println(methodInfo);
            if (m.name.startsWith("access$")) {
                StringWriter sw = new StringWriter();
                printer.print(new PrintWriter(sw));
                printer.getText().clear();                
                inList.get(inList.size()-2).accept(mp);
                sw = new StringWriter();
                printer.print(new PrintWriter(sw)); 
                printer.getText().clear();
                String privateInstructions = sw.toString();
                if (" ".equals(privateInstructions.substring(0, 1))) {privateInstructions=privateInstructions.replaceFirst(" +", "");}
                privateInstructions=privateInstructions.replaceAll(": (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)|\\n$", "").replaceAll("\\.(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", " ");
                if (privateInstructions.replaceAll(".+(?=L\\d+$).", "").matches("\\d+")) {
                    privateInstructions=privateInstructions.replaceFirst("(?<=(?=L\\d+$).).+", ""+privateInstructions.replaceAll(".+(?=L\\d+$).", ""));
                }
                for (String modName : modToSourceNames.keySet()) { //replacing modNames to source Names
                    privateInstructions=privateInstructions.replaceAll(modName, modToSourceNames.get(modName));
                }
                accessMethods.put(modID+m.name, new Pair<String, ArrayList<String>>(privateInstructions, methodInfo));
                for(int i = 1; i<inList.size(); i++) {
                    inList.get(i).accept(mp);
                    sw = new StringWriter();
                    printer.print(new PrintWriter(sw)); 
                    printer.getText().clear();
                    String instructions = sw.toString();
                    if (" ".equals(instructions.substring(0, 1))) {instructions=instructions.replaceFirst(" +", "");}
                    instructions=instructions.replaceAll(": (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)|\\n$", "");
                    if (!instructions.startsWith("LDC"))
                    instructions = instructions.replaceAll("\\.(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", " ");
                    if (instructions.replaceAll(".+(?=L\\d+$).", "").matches("\\d+")) {
                        instructions=instructions.replaceFirst("(?<=(?=L\\d+$).).+", ""+instructions.replaceAll(".+(?=L\\d+$).", ""));
                    }
                    accessMethods.get(modID+m.name).v.add(instructions);
                    addLog.println("    "+instructions);
                }
                // System.out.println(methodInfo+" - mod of "+classNode.name+"\n"+privateInstructions+"\n\n");
            }
            else {
                inList.get(0).accept(mp);
                StringWriter sw = new StringWriter();
                printer.print(new PrintWriter(sw));
                printer.getText().clear();
                int lineIndFirst = -1;
                String lineNumberInd = "0";
                for(int i = 0; i<inList.size(); i++) {
                    inList.get(i).accept(mp);
                    sw = new StringWriter();
                    printer.print(new PrintWriter(sw));
                    printer.getText().clear();
                    String instructions = sw.toString();
                    instructions=instructions.replaceFirst("^ +", "");
                    if (instructions.matches("L\\d+\n")){
                        if (lineIndFirst!=-1) {
                            byteCodeLines.get(methodInfo).add(lineInstructions);
                            lineInstructions = new ArrayList<String>();
                        }
                        lineNumberInd=instructions.replaceAll("L|\\n", "");
                        lineIndFirst++;
                        continue;
                    }
                    if (lineInstructions.size()==0) {
                        if (!instructions.matches("LINENUMBER \\d+ L\\d+\\n")) {
                            lineInstructions.add("LINENUMBER 0 L"+lineIndFirst);
                            lineNumbers.put(lineNumberInd, ""+lineIndFirst);
                        }
                        else {
                            lineNumbers.put(instructions.replaceAll(".+(?=L\\d+$).|\\n", ""), ""+lineIndFirst);
                            instructions=instructions.replaceFirst("L\\d+", "L"+lineIndFirst);
                        }
                    }
                    instructions=instructions.replaceAll(": (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)|\\n$", "").replaceAll("access\\$(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", modID+"access\\$");
                    if (!instructions.startsWith("LDC"))
                    instructions = instructions.replaceAll("\\.(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", " ");
                    if (instructions.matches("^(?:LOOKUP|TABLE)SWITCH(?:.|\\n)*$")) {
                        instructions=instructions.replaceAll("\\n      ", " ").replaceAll("(?=L\\d+)", ":");
                    }
                    else if (instructions.replaceAll(".+(?=L\\d+$).", "").matches("\\d+")) {
                        instructions=instructions.replaceFirst("(?<=(?=L\\d+$).).+", ""+instructions.replaceAll(".+(?=L\\d+$).", ""));
                    }
                    lineInstructions.add(instructions);
                }
                byteCodeLines.get(methodInfo).add(lineInstructions);
                for (int filteredLineInd = 0; filteredLineInd<byteCodeLines.get(methodInfo).size(); filteredLineInd++) {
                    ArrayList<String> line = byteCodeLines.get(methodInfo).get(filteredLineInd);
                    for(int instructionInd = 1; instructionInd<line.size(); instructionInd++) {
                        if (line.get(instructionInd).matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                            for (int i=0; i<line.get(instructionInd).replaceAll("[^L]", "").length()-1; i++) {
                                String oldLineNumber = line.get(instructionInd).replaceAll("(?:LOOKUP|TABLE)SWITCH(?: (?:-?\\d+|default):L\\d+){"+i+"}(?: (?:-?\\d+|default):)?L|(?: (?:-?\\d+|default):L\\d+){"+(line.get(instructionInd).replaceAll("[^L]", "").length()-2-i)+"}$", "");
                                line.set(instructionInd, line.get(instructionInd).replaceFirst("-?\\d+(?=(?:(?: (?:-?\\d+|default):(?:L\\d+)){" + (line.get(instructionInd).replaceAll("[^L]", "").length()-2-i) + "})$)", lineNumbers.get(oldLineNumber)));
                            }
                            byteCodeLines.get(methodInfo).get(filteredLineInd).set(instructionInd, line.get(instructionInd));
                        }
                        else {
                            String oldLineNumber = line.get(instructionInd).replaceAll(".+(?=L\\d+$).", "");
                            byteCodeLines.get(methodInfo).get(filteredLineInd).set(instructionInd, line.get(instructionInd).replaceFirst("L\\d+$", "L"+lineNumbers.get(oldLineNumber)));
                        }
                    }
                }
                String currentState = null;
                String variableDeclaration = null;
                int oneAdding = 0;
                HashMap<String, String> newVariablesInd = new HashMap<String, String>();
                lineInd = 0;
                HashMap<String,Integer> linesPositions = new HashMap<String,Integer>();
                int attrsSize = m.desc.replaceAll("\\(|\\).+|L[^;]+|\\[", "").length();
                HashMap<Integer, Integer> maxVariableInd = new HashMap<Integer, Integer>();
                maxVariableInd.put(0, 0);
                int newVariableInt = 0;
                for(int lineNumberi = 0; lineNumberi<byteCodeLines.get(methodInfo).size(); lineNumberi++) {
                    ArrayList<String> lineList = byteCodeLines.get(methodInfo).get(lineNumberi);
                    ArrayList<String> instructionList = new ArrayList<String>();
                    // localStackVariables
                    if (maxVariableInd.containsKey(lineNumberi)) {
                        newVariableInt = maxVariableInd.get(lineNumberi);
                    }
                    for (String lineInstruction: lineList) {
                        if (lineInstruction.matches("^[^\"]+L\\d+$")) {
                            if (lineInstruction.matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                                for (int i=0; i<lineInstruction.replaceAll("[^L]", "").length()-1; i++) {
                                    int toLine = Integer.parseInt(lineInstruction.replaceAll("(?:LOOKUP|TABLE)SWITCH(?: (?:-?\\d+|default):L\\d+){"+i+"}(?: (?:-?\\d+|default):)?L|(?: (?:-?\\d+|default):L\\d+){"+(lineInstruction.replaceAll("[^L]", "").length()-2-i)+"}$", ""));
                                    if (toLine>lineNumberi) {
                                        maxVariableInd.put(toLine, newVariableInt);
                                    }
                                }
                            }
                            else {
                                int toLine = Integer.parseInt(lineInstruction.replaceAll("^.+(?=L\\d+$)L", ""));
                                if (toLine>lineNumberi) {
                                    maxVariableInd.put(toLine, newVariableInt);
                                }
                            }
                        }
                        if (Arrays.asList(new String[]{"ILOAD", "LLOAD", "FLOAD", "DLOAD", "ALOAD", "ISTORE", "LSTORE", "FSTORE", "DSTORE", "ASTORE", "RET"}).contains(lineInstruction.split(" ")[0])) {
                            if (variableDeclaration!=null) { // объявление переменной из исходного кода
                                instructionList.add(lineInstruction.replaceFirst("\\d+", variableDeclaration));
                                newVariablesInd.put(lineInstruction.split(" ")[1], variableDeclaration);
                            } // "новая" переменная уже встречалась
                            else if (newVariablesInd.containsKey(lineInstruction.split(" ")[1])) {
                                instructionList.add(lineInstruction.replaceFirst("\\d+", newVariablesInd.get(lineInstruction.split(" ")[1])));
                            } // аргумент функции или this
                            else if (Integer.parseInt(lineInstruction.split(" ")[1])<=attrsSize) {
                                instructionList.add(lineInstruction);
                            } // новая переменная
                            else {
                                newVariablesInd.put(lineInstruction.split(" ")[1], modID+newVariableInt);
                                instructionList.add(lineInstruction.replaceFirst("\\d+", modID+newVariableInt));
                                newVariableInt++;
                                maxVariableInd.put(lineNumberi, newVariableInt);
                            }
                        }
                        else {
                            instructionList.add(lineInstruction);
                        }
                    }
                    lineList = instructionList;
                    if (variableDeclaration!=null) {variableDeclaration=null;}
                    else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker anonymusClassMarker (Ljava/lang/Integer;Ljava/lang/Object;)V")) {
                        String newClassID = lineList.get(1).replaceAll("[^\\d]+", "");
                        String oldClassName = lineList.get(3).replaceFirst("^NEW ", "");
                        anonymuses.put(newClassID, oldClassName);
                    }
                    else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker variableMarker (Ljava/lang/Integer;)V")) {
                        variableDeclaration=lineList.get(1).replaceAll("(?!\\d+).", "");
                        continue;
                    }
                    if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker plug ()V")) {
                        for (int instructionInd=0; instructionInd<lineList.size(); instructionInd++) {
                            if (lineList.get(instructionInd).equals("INVOKESTATIC com/neoclar/jbi/java/Marker plug ()V")) {
                                lineList.remove(instructionInd);
                                break;
                            }
                        }
                    }
                    if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker GoToMarker (Ljava/lang/Integer;)V")) {
                        ArrayList<String> newLineList = new ArrayList<String>();
                        newLineList.add(lineList.get(0));
                        newLineList.add("GOTO orig;L"+lineList.get(1).replaceAll("(?!\\d+).", ""));
                        lineList=newLineList;
                    }
                    if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker stopMarker ()V")|oneAdding==1) {
                        oneAdding=0;
                        lineInd=-1;
                        ArrayList<ArrayList<String>> thisLines = new ArrayList<ArrayList<String>>();
                        addLog.println("    "+currentState);
                        for (lineInd++; lineInd<modAdd.get(methodInfo).get(currentState).size(); lineInd++) {
                            thisLines.add(new ArrayList<String>());
                            for(int lineInstructionInd = 0; lineInstructionInd<modAdd.get(methodInfo).get(currentState).get(lineInd).size(); lineInstructionInd++) {
                                String lineListNew = modAdd.get(methodInfo).get(currentState).get(lineInd).get(lineInstructionInd);
                                String lineNumberNew = lineListNew.replaceFirst(".+(?=L\\d+$).", "");
                                if (lineNumberNew.matches("\\d+")) {
                                    if (lineListNew.matches("GOTO orig;L\\d+")) {
                                        thisLines.get(lineInd).add(lineListNew);
                                    }
                                    else if (lineListNew.matches("^(?:LOOKUP|TABLE)SWITCH.*$")) {
                                        for (int i=0; i<lineListNew.replaceAll("[^L]", "").length()-1; i++) {
                                            String oldLineNumber = lineListNew.replaceAll("(?:LOOKUP|TABLE)SWITCH(?: (?:-?\\d+|default):L\\d+){"+i+"}(?: (?:-?\\d+|default):)?L|(?: (?:-?\\d+|default):L\\d+){"+(lineListNew.replaceAll("[^L]", "").length()-2-i)+"}$", "");
                                            lineListNew = lineListNew.replaceFirst("-?\\d+(?=(?:(?: (?:-?\\d+|default):(?:L\\d+)){" + (lineListNew.replaceAll("[^L]", "").length()-2-i) + "})$)", ""+linesPositions.get(oldLineNumber));
                                        }
                                        thisLines.get(lineInd).add(lineListNew.replaceFirst("(?<=(?=L\\d+$).).+", ""+linesPositions.get(lineNumberNew)));
                                    }
                                    else {
                                        thisLines.get(lineInd).add(lineListNew.replaceFirst("(?<=(?=L\\d+$).).+", ""+linesPositions.get(lineNumberNew)));
                                    }
                                }
                                else {thisLines.get(lineInd).add(lineListNew);}
                                addLog.println("        "+thisLines.get(lineInd).get(lineInstructionInd));
                            }
                        }
                        modAdd.get(methodInfo).put(currentState, thisLines);
                        linesPositions = new HashMap<String,Integer>();
                        currentState=null;
                        lineInd=0;
                        continue;
                    }
                    if (currentState==null){
                        if (modDel.get(methodInfo)==null) {
                            modDel.put(methodInfo, new ArrayList<String>());
                        }
                        if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker deleteMethodMarker ()V")) {
                            delMethods.add(methodInfo);
                            break;
                        }
                        if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker deleteMarker (II)V")) {
                            for (int delLineInd = Integer.parseInt(lineList.get(1).replaceAll("(?!\\d+).", "")); delLineInd<=Integer.parseInt(lineList.get(2).replaceAll("(?!\\d+).", "")); delLineInd++)
                            modDel.get(methodInfo).add(""+delLineInd);
                            continue;
                        }
                        else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker deleteMarker (I)V")) {
                            modDel.get(methodInfo).add(lineList.get(1).replaceAll("(?!\\d+).", ""));
                            continue;
                        }
                        else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker beforeMarker ()V")) {
                            currentState="before";
                            continue;
                        }
                        else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker lineMarker (Ljava/lang/Integer;)V")) {
                            currentState=lineList.get(1).replaceAll("(?!\\d+).", "");
                            continue;
                        }
                        else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker afterMarker ()V")) {
                            currentState="after";
                            continue;
                        }
                        else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker beforeOneMarker ()V")) {
                            currentState="before";
                            oneAdding=2;
                            continue;
                        }
                        else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker lineOneMarker (Ljava/lang/Integer;)V")) {
                            currentState=lineList.get(1).replaceAll("(?!\\d+).", "");
                            oneAdding=2;
                            continue;
                        }
                        else if (lineList.contains("INVOKESTATIC com/neoclar/jbi/java/Marker afterOneMarker ()V")) {
                            currentState="after";
                            oneAdding=2;
                            continue;
                        }
                    }
                    else{
                        if (oneAdding==2) {
                            oneAdding=1;
                        }
                        if (currentState.equals("before")) {
                            modAdd.get(methodInfo).get("before").add(lineList);
                            linesPositions.put(""+lineNumberi, lineInd++);
                            continue;
                        }
                        if (currentState.equals("after")) {
                            modAdd.get(methodInfo).get("after").add(lineList);
                            linesPositions.put(""+lineNumberi, lineInd++);
                            continue;
                        }
                        else {
                            modAdd.get(methodInfo).get(currentState).add(lineList);
                            linesPositions.put(""+lineNumberi, lineInd++);
                            continue;
                        }
                    }
                }
            }
            addLog.print("\n");
        }
    }
    public HashMap<ArrayList<String>,ArrayList<String>> getDel() {
        return modDel;
    }
    public ClassNode getClassNode() {
        return classNode;
    }
    public DefaultHashMap<ArrayList<String>,DefaultHashMap<String,ArrayList<ArrayList<String>>>> getAdd() {
        return modAdd;
    }
    public ArrayList<ArrayList<String>> getDelMethods() {
        return delMethods;
    }
    public HashMap<String, String> getAnonymuses() {
        return anonymuses;
    }
    public HashMap<String, Pair<String, ArrayList<String>>> getAccessMethods() {
        return accessMethods;
    }
}