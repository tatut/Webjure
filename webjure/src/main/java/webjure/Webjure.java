package webjure;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PushbackReader;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.logging.Logger;

import clojure.lang.DynamicClassLoader;
import clojure.lang.IFn;
import clojure.lang.LispReader;
import clojure.lang.Symbol;
import clojure.lang.Var;

public class Webjure {

    private static Logger log = Logger.getLogger(Webjure.class.getName());

    // Store required modules, so they are not loaded again
    private static HashSet<String> requiredModules = new HashSet<String>();

    /**
     * Load a .clj file from context.
     * A good place for scripts is under the WEB-INF directory so that
     * containers don't give download access to it.
     *
     * @param file path relative to context root     
     */
    public static void loadFromResource(String file) throws Exception {
	log.info("Loading script: "+file);
	clojure.lang.Compiler.load(new InputStreamReader(Webjure.class.getClassLoader().getResourceAsStream(file)));	
    }
    
    /**
     * This is called from webjure code to require new modules.
     * Currently a module name is simply the file name without .clj suffix.
     * 
     * @param module the module to require
     * @deprecated Use clojure module functionalities instead
     */
    public static void require(String module) throws Exception {
	if(!requiredModules.contains(module)) {
	    loadFromResource(module+".clj");
	    requiredModules.add(module);
	}
    }

    /**
     * Evaluate string with the proper classloader.
     */
    public static Object eval(String in) throws Exception {
    	return clojure.lang.Compiler.eval(LispReader.read(new PushbackReader(new StringReader(in)), true, null, false));
    }
	
}