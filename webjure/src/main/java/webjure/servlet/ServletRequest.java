/*
 * Created on Feb 29, 2008
 *
 */
package webjure.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import webjure.Request;

/**
 * A servlet request wrapper.
 * 
 * @author Tatu Tarvainen (tatu.tarvainen@mac.com)
 */
public class ServletRequest implements Request {
    
    private HttpServletRequest request;

    ServletRequest(HttpServletRequest actual) {
        request = actual;
    }
    
    public Object getActualRequest() {
        return request;
    }

    public String getParameter(String name) {
        return request.getParameter(name);
    }

    public Map<String, String[]> getParameterMap() {
        return (Map<String,String[]>) request.getParameterMap();
    }

    public Collection<String> getParameterNames() {
        ArrayList<String> names = new ArrayList<String>();
        for(Enumeration en = request.getParameterNames(); en.hasMoreElements();)
            names.add((String) en.nextElement()); 
        return names;
    }
    
    

}