package webjure;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.lang.reflect.Field;
import java.util.logging.Logger;

import clojure.lang.Compiler;
import clojure.lang.LispReader;
import clojure.lang.RT;
import clojure.lang.Var;
import clojure.lang.IFn;
import clojure.lang.Symbol;

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
	
	// reflectively get RT fields (because we are in a different package and have no access to them)
	private <T> T getRTField(String name) {
		try {
			Field field = null;
			for(Field f : RT.class.getDeclaredFields()) {
				if(f.getName().equals(name)) {
					field = f;
					break;
				}
			}
			field.setAccessible(true);
			return (T) field.get(null);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void run() {
		active = true;
		
		try {
			out.write("Welcome to webjure shell. Happy hacking!\n");

			PushbackReader read = new PushbackReader(in);
			Object EOF = new Object();
			
			Var NS_REFERS = getRTField("NS_REFERS"),
				NS_IMPORTS = getRTField("NS_IMPORTS"),
				CURRENT_NS_SYM = getRTField("CURRENT_NS_SYM"),
				WARN_ON_REFLECTION = getRTField("WARN_ON_REFLECTION");
			
			Var.pushThreadBindings(
					RT.map(NS_REFERS, NS_REFERS.get(),
					       NS_IMPORTS, NS_IMPORTS.get(),
					       CURRENT_NS_SYM, CURRENT_NS_SYM.get(),
					       WARN_ON_REFLECTION, WARN_ON_REFLECTION.get(),
					       Compiler.SOURCE, "REPL"
					));
			IFn inNamespace = getRTField("inNamespace");
			try {
				inNamespace.invoke(Symbol.create("webjure-user"));
			} catch(Exception e) {
				throw new RuntimeException("Unable to set namespace.");
			}
			
			while(active) {

				out.write(CURRENT_NS_SYM.get()+"=> ");
				out.flush();

				try {
					Object form = LispReader.read(read, false, EOF, false);
					if(form == EOF) active = false;
					else {					
						Object result = clojure.lang.Compiler.eval(form);
						RT.print(result, out);
						out.write("\n");
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
