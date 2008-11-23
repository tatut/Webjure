/*
 * Created on Feb 29, 2008
 *
 */
package webjure;

import java.util.Collection;
import java.util.Map;


/**
 * A facade interface for allowing access to common operations on different requests types. 
 * Also returns information as {@link Collection}s (no old style {@link java.util.Enumeration}s).
 * 
 * @author Tatu Tarvainen (tatu.tarvainen@mac.com)
 */
public interface Request {

    /**
     * Get a request parameter by name.
     * 
     * @param name the parameter name
     * @return
     */
    public String getParameter(String name);
    
    /**
     * Get all values of a reqeust parameter.
     *
     * @param name the parameter name
     * @return the values as array
     */
    public String[] getParameterValues(String name);

    /**
     * Return the parameter names in this request.
     * 
     */
    public Collection<String> getParameterNames();
    
    /**
     * Return a map of all parameters.
     * 
     */
    public Map<String, String[]> getParameterMap();
    
    /**
     * Return the actual request object of the current request.
     * This is for using methods that are not common to the 
     * different request types. The return value can be cast to
     * the correct type.
     * 
     * @return the actual request object
     */
    public Object getActualRequest();
    
    /**
     * Return the session facade.
     */
    public Session getSession();
    
}
