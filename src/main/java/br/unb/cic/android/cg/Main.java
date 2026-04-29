package br.unb.cic.android.cg;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private static final String USAGE =
            "Usage: java -jar cg.jar -a <APK_FILE> -p <ANDROID_SDK_PLATFORMS> [-o <OUTPUT_FILE>]";

    public static void main(String[] args) throws Exception {
        String apkFile = null;
        String androidPlatforms = null;
        String outputFile = "reachable-methods.txt";

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "-a": apkFile = args[++i]; break;
                case "-p": androidPlatforms = args[++i]; break;
                case "-o": outputFile = args[++i]; break;
            }
        }

        if (apkFile == null || androidPlatforms == null) {
            System.err.println(USAGE);
            System.exit(1);
        }

        SetupApplication app = new SetupApplication(new File(androidPlatforms), new File(apkFile));
        app.getConfig().setCallgraphAlgorithm(CallgraphAlgorithm.SPARK);

        LOG.log(Level.INFO, "Building call graph with SPARK for: {0}", apkFile);
        app.constructCallgraph();

        if (!Scene.v().hasCallGraph()) {
            LOG.log(Level.SEVERE, "Failed to build the call graph.");
            System.exit(1);
        }

        CallGraph cg = Scene.v().getCallGraph();
        LOG.log(Level.INFO, "Call graph built — {0} edges", cg.size());

        Set<SootMethod> reachable = findReachableMethods(cg);
        LOG.log(Level.INFO, "Reachable methods: {0}", reachable.size());

        writeReachableMethods(reachable, outputFile);
        LOG.log(Level.INFO, "Output written to: {0}", outputFile);
    }

    // BFS from all entry points through the call graph.
    private static Set<SootMethod> findReachableMethods(CallGraph cg) {
        Set<SootMethod> visited = new HashSet<>(Scene.v().getEntryPoints());
        Deque<SootMethod> worklist = new ArrayDeque<>(visited);

        while (!worklist.isEmpty()) {
            SootMethod method = worklist.poll();
            Iterator<Edge> edges = cg.edgesOutOf(method);
            while (edges.hasNext()) {
                SootMethod target = edges.next().tgt();
                if (visited.add(target)) {
                    worklist.add(target);
                }
            }
        }
        return visited;
    }

    private static void writeReachableMethods(Set<SootMethod> methods, String outputFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile))) {
            for (SootMethod method : methods) {
                writer.write(method.getSignature());
                writer.newLine();
            }
        }
    }
}
