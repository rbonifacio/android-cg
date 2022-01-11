import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;

import soot.G;
import soot.Scene;
import soot.jimple.infoflow.cmd.MainClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import soot.SootMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.*;



public class Main {


	public static ArrayList<Node> sensitiveMethodsTotal = new ArrayList<Node>();
	
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
    	
    	
    	Path path = Path.of("sensitive-methods.txt");
    	List<String> sensitiveMethods = Files.readAllLines(path);

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

            Node src = new Node(e.src(), computeMethodType(e.src(), sensitiveMethods));
            Node tgt = new Node(e.tgt(), computeMethodType(e.tgt(), sensitiveMethods));

            g.putEdge(src, tgt);
            
        }
        
        //List<Node> sensitiveMethodsDistinct = sensitiveMethodsTotal.stream().distinct().collect(Collectors.toList());
        
        
        Logger.getLogger(MainClass.class.getName()).log(Level.INFO," done. \n" +
                " (*) Number of nodes in the CG: " + g.nodes().size() + "\n" +
                " (*) Number of edges in the CG: " + g.edges().size() + "\n"
        );
        
        
        ArrayList<Node> listEntryPoint = new ArrayList<Node>();
        ArrayList<Node> listSensitiveMethod = new ArrayList<Node>();
        
        Set<Node> myNodes = g.nodes();
        Iterator<Node> it = myNodes.iterator();
        while(it.hasNext()){
        	
        	Node n = it.next();
        	
        	if (n.type.toString().equals("EntryPointMethod")) {
        		listEntryPoint.add(n);
        		//System.out.println((n.method).toString()+"----"+n.type);
        	}
        		
        	if (n.type.toString().equals("SensitiveMethod")) {
        		listSensitiveMethod.add(n);
        		//System.out.println(n.method+"==>This is Sensitive: "+n.method.getDeclaringClass().getName()+"====="+n.method.getSubSignature());
        	}
        }
        
        //Print size lists
        System.out.println(listEntryPoint.size());
        System.out.println(listSensitiveMethod.size());
        
        
        //Here we will run lists and sum the distance from all components.
        int dtc = 0;
        for (int i=0 ; i<listEntryPoint.size() ; i++) {
        	for (int j=0 ; j<listSensitiveMethod.size() ; j++) {
        		Node src = listEntryPoint.get(i);
        		Node tgt = listSensitiveMethod.get(j);
        		//sum all the distance calling distance method
        		dtc = dtc + distance(src,tgt);
        	}
        }
        //Print the distance
        System.out.println(dtc);
        
        //here I check who is the adjacent Node of the first EntryPoint at the List
        
        Set<Node> checkAdjacent = g.adjacentNodes(listEntryPoint.get(0));
        
        Iterator<Node> ite = checkAdjacent.iterator();
        while(ite.hasNext()){
        	Node ajdNode = ite.next();
        	System.out.println(("adjacent---"+ajdNode.method).toString()+"----"+ajdNode.type);
        	
        }
          
        //Here I print all the nodes traversed from the first element of the Entry Point.
        Traverser.forGraph(g).breadthFirst(listEntryPoint.get(0))
        .forEach(x->System.out.println(x.method));
        
    }
;        
     
        
    
    
	static int distance(Node from, Node to) {
	
		return 1;
	}
	

    static MethodType computeMethodType(SootMethod method,List<String> lsensitive) throws IOException {
        if(Scene.v().getCallGraph().isEntryMethod(method)) {
            return MethodType.EntryPointMethod;
        }
        else if(sensitiveMethod(method,lsensitive)) {
            return MethodType.SensitiveMethod;
        }
        return MethodType.SimpleMethod;
    }
    //Method that check if a method is sensitive
    //check if the method belongs to existent list of sensitive methods 
    private static boolean sensitiveMethod(SootMethod method, List<String> lsensitive) throws IOException {
    	
       	int count = 0;
       	boolean sensitive = false;
    	while (lsensitive.size() > count) {
    		if (lsensitive.get(count).contains(method.getDeclaringClass().getName()) && lsensitive.get(count).contains(method.getSubSignature())) {
    			Node n = new Node(method,MethodType.SensitiveMethod );
    			//Insert method into the list of sensitive methods 
    			//sensitiveMethodsTotal.add(n);
    			
    			sensitive = true;
    		}
    		count++;
    	}
        return sensitive;   
    }
}
