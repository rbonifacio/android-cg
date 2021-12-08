### Android Call Graph Constructor

This simple program computes a call graph from an Android app. The main idea here is to compute the distances 
between the entry points and the calls to sensitive APIs. It takes advantage of [FlowDroid](https://github.com/secure-software-engineering/FlowDroid) 
to generate an entry point for the app and a Soot call graph; and then it 
computes a [Google Guava graph](https://github.com/google/guava/wiki/GraphsExplained) that is easier to traverse. 

#### Build 

To generate an executable `jar` file, just execute the following 
maven command. 

```{shell}
$ mvn clean compile assembly:single
```

This will output a file named: 

   * `cg.jar`

in the `target` directory (as usual). In order to run the program, which currently only outputs the number of nodes and edges of the graph, 
you should execute: 

````{shell}
$ java -jar cg.jar -a <APK_FILE> -p <ANDROID_SDK_PLATFORM> -s <FILE_SOURCE_SINKS>
````

### TODO Notes

   * Handrick must implement the method that verifies if a given `SootMethod` is an _Android sensitive_ method.
   * Handrick must implement the logic to compute the distances between entry point nodes and sensitive nodes in the graph. 