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

import java.util.ArrayList;
public class SourceClassReader {
    private static Printer printer = new Textifier();
    private static TraceMethodVisitor mp = new TraceMethodVisitor(printer);
    private DefaultHashMap<ArrayList<String>,ArrayList<ArrayList<String>>> byteCodeLines = new DefaultHashMap<ArrayList<String>,ArrayList<ArrayList<String>>>(ArrayList.class, new Class[]{}, new Object[]{});
    private HashMap<ArrayList<String>,MethodNode> methodNode = new HashMap<ArrayList<String>,MethodNode>();
    private ClassNode classNode = new ClassNode();
    private HashMap<String, String> accessMethods = new HashMap<String, String>();
    public SourceClassReader(byte[] bytes, ArrayList<ArrayList<String>> needMethods, PrintWriter sourceLog) {
        // Переменные
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode,0);
        final List<MethodNode> methods = classNode.methods;
        // сбор методов
        for(MethodNode m: methods){
            ArrayList<String> methodInfo = new ArrayList<String>(){};
            methodInfo.add(m.name);
            methodInfo.add(""+m.access);
            methodInfo.add(m.desc);
            methodInfo.add(m.signature);
            if (needMethods.contains(methodInfo)) {
                sourceLog.println(methodInfo);
                ArrayList<String> lineInstructions = new ArrayList<String>();
                HashMap<String, String> lineNumbers = new HashMap<String, String>();
                methodNode.put(methodInfo, m);
                InsnList inList = m.instructions;
                inList.get(0).accept(mp);
                StringWriter sw = new StringWriter();
                printer.print(new PrintWriter(sw));
                printer.getText().clear();
                int lineInd = -1;
                String lineNumberInd = "0";
                for(int i = 0; i<inList.size(); i++) {
                    inList.get(i).accept(mp);
                    sw = new StringWriter();
                    printer.print(new PrintWriter(sw));
                    printer.getText().clear();
                    String instructions = sw.toString();
                    instructions=instructions.replaceFirst("^ +", "");
                    if (instructions.matches("L\\d+\n")){
                        if (lineInd!=-1) {
                            byteCodeLines.get(methodInfo).add(lineInstructions);
                            lineInstructions = new ArrayList<String>();
                        }
                        lineNumberInd=instructions.replaceAll("L|\\n", "");
                        lineInd++;
                        continue;
                    }
                    if (lineInstructions.size()==0) {
                        if (!instructions.matches("LINENUMBER \\d+ L\\d+\\n")) {
                            lineInstructions.add("LINENUMBER 0 L"+lineInd);
                            lineNumbers.put(lineNumberInd, ""+lineInd);
                        }
                        else {
                            lineNumbers.put(instructions.replaceAll(".+(?=L\\d+$).|\\n", ""), ""+lineInd);
                            instructions=instructions.replaceFirst("L\\d+", "L"+lineInd);
                        }
                    }
                    instructions=instructions.replaceAll(": (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)|\\n$", "");
                    if (!instructions.startsWith("LDC"))
                    instructions = instructions.replaceAll("\\.(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", " ");
                    if (instructions.matches("^(?:LOOKUP|TABLE)SWITCH(?:.|\\n)*$")) {
                        instructions=instructions.replaceAll("\\n      ", " ").replaceAll("(?=L\\d+)", ":");
                    }
                    if (instructions.replaceAll(".+(?=L\\d+$).", "").matches("\\d+")) {
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
                        sourceLog.println("    "+byteCodeLines.get(methodInfo).get(filteredLineInd).get(instructionInd));
                    }
                }
                sourceLog.print("\n");
            }
            else if (m.name.startsWith("access$")) {
                sourceLog.println(methodInfo);
                InsnList inList = m.instructions;
                inList.get(0).accept(mp);
                StringWriter sw = new StringWriter();
                printer.print(new PrintWriter(sw));
                printer.getText().clear();
                inList.get(inList.size()-3).accept(mp);
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
                accessMethods.put(instructions, m.name);
                sourceLog.println("    "+instructions);
                sourceLog.print("\n");
            }
        }
    }
    public DefaultHashMap<ArrayList<String>,ArrayList<ArrayList<String>>> getByteCodeLines() {
        return byteCodeLines;
    }
    public ClassNode getClassNode() {
        return classNode;
    }
    public HashMap<ArrayList<String>,MethodNode> getMethodNode() {
        return methodNode;
    }
    public HashMap<String, String> getAccessMethods() {
        return accessMethods;
    }
}