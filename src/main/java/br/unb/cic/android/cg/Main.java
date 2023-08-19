package br.unb.cic.android.cg;

import soot.*;
import soot.jimple.infoflow.cmd.MainClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.options.Options;
import soot.util.queue.QueueReader;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final String ERROR_MESSAGE =
            "Unexpected command line arguments. Please try: \n" +
                    "java -jar cg.jar -a <APK_FILE>";

    public static void main(String args[]) throws Exception {

        int i, j, k, l, m, n;
        i = j = k = l = m = n = 0;
        String ccOutputFile = null;

        for (i = 0; i < args.length; i++) {
            if (args[i].equals("-t")) {
                ccOutputFile = args[i + 1];
            }
        }

        if (ccOutputFile == null) {
            System.out.println("CryptoOutput file not found");
            return;
        }

        Options.v().set_full_resolver(true);
        MainClass.main(args);
        CallGraph cg = Scene.v().getCallGraph();
        Scene.v().loadNecessaryClasses();

        if (!Scene.v().hasCallGraph()) {
            Logger.getLogger(MainClass.class.getName()).log(Level.SEVERE, "Failed while building " +
                    "the call graph.\nPlease, check if you have given a valid " +
                    "command line arguments.");

            System.exit(1);
        }

        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> methods = reachableMethods.listener();

        File out1 = new File("reachable-methods.csv");
        PrintStream stream1 = new PrintStream(out1);
        System.setOut(stream1);

        i=0;
        System.out.println("ReachableMethods");
        while(methods.hasNext()){
            System.out.println(methods.next().method().getSignature());
            i++;
        }

        File out2 = new File("crypto-reachable.csv");
        PrintStream stream2 = new PrintStream(out2);
        System.setOut(stream2);

        List<String> allWarnings = readLinesFromFile(ccOutputFile);

        System.out.println("Signature;reachable;found");
        for (String warning : allWarnings) {
            try {
                SootMethod method = Scene.v().getMethod(warning);
                if(reachableMethods.contains(method)) {
                    System.out.println("\""+ warning+"\"" + ";1;1");
                    j++;
                }
                else {
                    System.out.println("\""+ warning+"\"" + ";0;1");
                    k++;
                }
            }catch (Throwable e){
                System.out.println("\""+ warning+"\"" + ";0;0");
                l++;
            }
            m++;
        }
        Set<String> signatures = new HashSet<>(allWarnings);
        n = signatures.size();
        File out3 = new File("summary-reachable.csv");
        PrintStream stream3 = new PrintStream(out3);
        System.setOut(stream3);

        System.out.println("reachable-methods;crypto-methods;crypto-warnings;reachable-warnings;not-reachable-warnings;not-found-methods");
        System.out.println(i +";"+ n +";"+ m +";"+ j +";"+ k +";"+ l);

    }

    public static List<String> readLinesFromFile(String fileName) {
        List<String> lines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lines;
    }

}
