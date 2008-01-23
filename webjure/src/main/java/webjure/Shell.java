package webjure;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.util.logging.Logger;

import clojure.lang.LispReader;

public class Shell implements Runnable {

	private static Logger log = Logger.getLogger(Shell.class.getName());
	
	private BufferedReader in;
	private BufferedWriter out;
	private ClassLoader classLoader;
	
	private String currentNamespace = "webjure";
	private boolean active;
	
	public Shell(BufferedReader in, BufferedWriter out, ClassLoader classLoader) {
		this.in = in;
		this.out = out;
		this.classLoader = classLoader;
		
	}
	
	public void run() {
		active = true;
		
		try {
			out.write("Welcome to webjure shell. Happy hacking!\n");

			PushbackReader read = new PushbackReader(in);
			Object EOF = new Object();
			
			while(active) {

				out.write(currentNamespace+"=> ");
				out.flush();

				try {
					Object form = LispReader.read(read, false, EOF, false);
					if(form == EOF) active = false;
					else {					
						Object result = clojure.lang.Compiler.eval(form);
						out.write(result == null ? "null" : result.toString());
					}
				} catch(Exception e) {
					out.write("Exception: "+e);
					
				}			
			}
		} catch(IOException ioe) {
			log.warning("IO exception in shell: "+ioe.getMessage());
		}
		
		try {
			in.close();
			out.close();
		} catch(IOException ioe2) {
			log.warning("Unable to close shell IO: "+ioe2.getMessage());
		}
	}

	
}
