/*
 * Created on Feb 29, 2008
 *
 */
package webjure.portlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;


import webjure.Request;
import webjure.Session;

/**
 * A portlet request wrapper.
 * 
 * @author Tatu Tarvainen (tatu.tarvainen@mac.com)
 */
public class PortletRequest implements Request {

    private javax.portlet.PortletRequest request;
    
    PortletRequest(javax.portlet.PortletRequest actual) {
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

    public Session getSession() {
        return new PortletSession(request.getPortletSession());
    }
}
