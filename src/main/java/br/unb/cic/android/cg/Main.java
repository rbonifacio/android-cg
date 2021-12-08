package br.unb.cic.android.cg;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import soot.Scene;
import soot.jimple.infoflow.cmd.MainClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import soot.SootMethod;

import java.io.File;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Main {

    private static final String ERROR_MESSAGE =
            "Unexpected command line arguments. Please try: \n" +
            "java -jar cg.jar -a <APK_FILE> -p <ANDROID_SDK_PLATFORM> -s <FILE_SOURCE_SINKS>";
    enum MethodType {
        EntryPointMethod,
        SensitiveMethod,
        SimpleMethod
    }

    static class Node {
        SootMethod method;
        MethodType type;

        public Node(SootMethod method, MethodType type) {
            this.method = method;
            this.type = type;
        }
    }

    public static void main(String args[]) throws Exception {
        MainClass.main(args);
        if(!Scene.v().hasCallGraph()) {
            Logger.getLogger(MainClass.class.getName()).log(Level.SEVERE, "Failed while building " +
                    "the call graph.\nPlease, check if you have given a valid " +
                    "command line arguments.");

            System.exit(1);
        }
        CallGraph cg = Scene.v().getCallGraph();

        MutableGraph<Node> g = GraphBuilder.directed().build();

        Iterator<Edge> iterator = cg.iterator();

        Logger.getLogger(MainClass.class.getName()).log(Level.INFO,"computing the new call graph.... ");
        while(iterator.hasNext()) {
            Edge e = iterator.next();

            Node src = new Node(e.src(), computeMethodType(e.src()));
            Node tgt = new Node(e.tgt(), computeMethodType(e.tgt()));

            g.putEdge(src, tgt);
        }
        Logger.getLogger(MainClass.class.getName()).log(Level.INFO," done. \n" +
                " (*) Number of nodes in the CG: " + g.nodes().size() + "\n" +
                " (*) Number of edges in the CG: " + g.edges().size()
        );
        // TODO: so, here we can traverse the graph `g` and
        //   compute the distances.
    }

    static MethodType computeMethodType(SootMethod method) {
        if(Scene.v().getCallGraph().isEntryMethod(method)) {
            return MethodType.EntryPointMethod;
        }
        else if(sensitiveMethod(method)) {
            return MethodType.SensitiveMethod;
        }
        return MethodType.SimpleMethod;
    }

    private static boolean sensitiveMethod(SootMethod method) {
        return false; // TODO: Handrick, rewrite this method to
                      //   check if `method` is a sensitive method.
    }

}
