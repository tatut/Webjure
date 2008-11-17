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
import clojure.lang.RT;

/**
 * Common glue and interfacing code to Clojure.
 * No other class in webjure should directly touch
 * clojure internals.
 */
public class Webjure {

    private static final String INIT_SCRIPT = "webjure.clj";

    private static Logger log = Logger.getLogger(Webjure.class.getName());
    static {
	try {
	    log.info("Initializing Webjure.");
	    RT.loadResourceScript(INIT_SCRIPT);
	} catch(Exception e) {
	    log.severe("Unable to load \""+INIT_SCRIPT+"\": "+e.getMessage());
	    throw new WebjureException("Webjure initialization failed: "+e.getMessage(),
				       e);
	}
    }
   

    /**
     * Load a .clj file from context.
     * A good place for scripts is under the WEB-INF directory so that
     * containers don't give download access to it.
     *
     * @param file path relative to context root     
     */
    public static void loadFromResource(String file) throws Exception {
	log.info("Loading script: "+file);
	//clojure.lang.Compiler.load(new InputStreamReader(Webjure.class.getClassLoader().getResourceAsStream(file)));	
	RT.loadResourceScript(file);
    }
    
    /**
     * Evaluate string with the proper classloader.
     */
    public static Object eval(String in) throws Exception {
    	return clojure.lang.Compiler.eval(LispReader.read(new PushbackReader(new StringReader(in)), true, null, false));
    }

    /**
     * Retrieve a Clojure function. The name is a namespace
     * qualified var name (eg. "myns/my-function").
     */
    public static IFn getFunction(String qualifiedName) {
	try {
	    return Var.find(Symbol.intern("webjure/dispatch")).fn();
	} catch(Exception e) {
	    throw new WebjureException("getFunction(\""+qualifiedName+"\") failed: "+
				       e.getMessage(), e);
	}
    }
}