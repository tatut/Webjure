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

import webjure.Shell;
import clojure.lang.DynamicClassLoader;
import clojure.lang.IFn;
import clojure.lang.LispReader;
import clojure.lang.Symbol;
import clojure.lang.Var;


public class WebjureServlet extends HttpServlet {

	private static Logger log = Logger.getLogger(WebjureServlet.class.getName());
	
	// Store required modules, so they are not loaded again
	private static HashSet<String> requiredModules = new HashSet<String>();
	
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

	/**
	 * Load a .clj file from context.
	 * A good place for scripts is under the WEB-INF directory so that
	 * containers don't give download access to it.
	 *
	 * On failure calls <code>loadFailure(Exception)</code>.
	 *
	 * @param file path relative to context root
	 * @return true on success, false otherwise
	 */
	private boolean loadFromResource(String file) {
		try {
			staticLoadFromResource(file);
			return true;
		} catch (Exception e) {
			loadFailure(file, e);
			return false;
		}
	}

	private static void staticLoadFromResource(String file) throws Exception {
		log.info("Loading script: "+file);
		clojure.lang.Compiler.load(new InputStreamReader(
				WebjureServlet.class.getClassLoader().getResourceAsStream(
						file))/*, new DynamicClassLoader(WebjureServlet.class.getClassLoader())*/);
	}
	
	/**
	 * This is called from webjure code to require new modules.
	 * Currently a module name is simply the file name without .clj suffix.
	 * 
	 * @param module the module to require
	 */
	public static void require(String module) throws Exception {
		if(!requiredModules.contains(module)) {
			staticLoadFromResource(module+".clj");
			requiredModules.add(module);
		}
	}
	
    /**
     * Evaluate string with the proper classloader.
     */
    public static Object eval(String in) throws Exception {
    	return clojure.lang.Compiler.eval(
    			LispReader.read(new PushbackReader(new StringReader(in)), true, null, false)/*,
    			new DynamicClassLoader(WebjureServlet.class.getClassLoader())*/);
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

		/* Bootstrap clojure */
		loadFromResource("boot.clj");

		/* Load webjure */
		loadFromResource("webjure.clj");
		dispatch = Var.find(Symbol.intern("webjure/dispatch")).fn();
		
		/* Load application startup scripts, if any */
		String[] startupScripts = getStartupScripts();
		if (startupScripts != null) {
			for (String script : startupScripts)
				loadFromResource(script);
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
			new Thread(
					new Runnable() {					
						public void run() {
							try {
							Socket s = shellServerSocket.accept();
							new Thread(
									new Shell(
											new BufferedReader(new InputStreamReader(s.getInputStream())),
											new BufferedWriter(new OutputStreamWriter(s.getOutputStream())),
											WebjureServlet.class.getClassLoader())
									).start();
							} catch(IOException ioe) {
								log("Unable create shell for incoming connection.", ioe);
							}
						}					
					}).start();
			} catch(IOException ioe) {
				log("Unable to start shell.", ioe);
			}
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