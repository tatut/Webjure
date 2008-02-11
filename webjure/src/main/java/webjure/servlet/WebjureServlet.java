package webjure.servlet;

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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import webjure.*;

import clojure.lang.DynamicClassLoader;
import clojure.lang.IFn;
import clojure.lang.LispReader;
import clojure.lang.Symbol;
import clojure.lang.Var;
import clojure.lang.RT;

public class WebjureServlet extends HttpServlet {

	private static Logger log = Logger.getLogger(WebjureServlet.class.getName());
	
	
	/* The main webjure dispatch function */
	private IFn dispatch; 
	
	private ServerSocket shellServerSocket;
	
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		webjureRequest("GET", request, response);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		webjureRequest("POST", request, response);
	}

	/* XXX:
	 Implement the other HTTP methods also.
	 I usually don't have much use for them, so they are absent for now.
	 */


    private void load(String file) {
	try {
	    Webjure.loadFromResource(file);
	} catch(Exception e) {
	    loadFailure(file, e);
	}
    }

    /**
     * React to loading failures. This can be overridden by subclasses
     * to provide specific error reporting.
     * The default implementation logs the error and rethrows it
     * wrapped in a <code>RuntimeException</code>.
     *
     * @param file path relative to context root of the file
     * @param e the exception that occured
     */
    protected void loadFailure(String file, Exception e) {
	log("Unable to load clojure script '" + file + "'.", e);
	throw new RuntimeException(e);
    }
    
    /**
     * Determine scripts needed for application initialization.
     * This method can be overridden to specify one or more
     * clojure scripts to be loaded at initialization.
     * The scripts will be loaded after clojure and webjure have
     * been initialized.
     * 
     * The default implementation loads the script specified
     * in the "startup" init parameter.
     *
     * @return an array of scripts to be loaded
     */
    protected String[] getStartupScripts() {
	String startup = getServletConfig().getInitParameter("startup");
	return startup == null ? new String[0] : new String[] { startup };
    }
    
    public void init() throws ServletException {
	
	try {
	    RT.init();
	} catch(Exception e) {
	    throw new ServletException("Unable to initialize webjure, clojure init failed: "+e.getMessage());
	}

	/* Bootstrap clojure */
	//loadFromResource("boot.clj");
	
	/* Load webjure */
	load("webjure.clj");
	dispatch = Var.find(Symbol.intern("webjure/dispatch")).fn();
	
	/* Load application startup scripts, if any */
	String[] startupScripts = getStartupScripts();
	if (startupScripts != null) {
	    for (String script : startupScripts)
		load(script);
	}
	
	
	/* Start shell listener */
	String shell = getServletContext().getInitParameter("shell");
	if(shell != null) {	
	    if(!shell.matches(".*?:\\d+")) 
		throw new ServletException("Illegal shell configuration, expected form \"bindaddr:port\".");			
	    String[] addrAndPort = shell.split(":");
	    
	    try {
		shellServerSocket = new ServerSocket();
		shellServerSocket.bind(new InetSocketAddress(Inet4Address.getByName(addrAndPort[0]), Integer.parseInt(addrAndPort[1])));
		new Thread(new Runnable() {					
			public void run() {
			    for(;;) {
				try {
				    Socket s = shellServerSocket.accept();
				    new Thread(new Shell(
							 new BufferedReader(new InputStreamReader(s.getInputStream())),
							 new BufferedWriter(new OutputStreamWriter(s.getOutputStream())),
							 WebjureServlet.class.getClassLoader())).start();
				} catch(IOException ioe) {
				    log("Unable create shell for incoming connection.", ioe);
				}
			    }}}).start();
	    } catch(IOException ioe) {
		log("Unable to start shell.", ioe);
	    }
	}
	
	/* Do application init (call clojure fn, if one was specified) */
	String init = getServletConfig().getInitParameter("init");
	try {
	    if(init != null)
		Var.find(Symbol.intern(init)).invoke(getServletConfig());
	} catch(Exception e) {
	    throw new ServletException("Calling application provided init fn '"+init+"' failed", e);
	}
	
	
    }
    
    private void webjureRequest(String method, HttpServletRequest request,
				HttpServletResponse response) throws IOException {
	try {
	    dispatch.invoke(method, request, response);
	} catch(Exception e) {
	    /* This is here for debugging, a better error handling mechanism would be good */
	    response.setContentType("text/plain");
	    PrintWriter out = response.getWriter();
	    out.append(e.getMessage());
	    e.printStackTrace(out);
	}
    }
}