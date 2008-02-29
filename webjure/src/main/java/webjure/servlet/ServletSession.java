/*
 * Created on Feb 29, 2008
 *
 */
package webjure.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.http.HttpSession;

import webjure.Session;

public class ServletSession implements Session {

    private HttpSession session;
    
    ServletSession(HttpSession actual) {
        session = actual;
    }
    
    public Object getAttribute(String name) {    
        return session.getAttribute(name);
    }

    public Collection<String> getAttributeNames() {
        ArrayList<String> names = new ArrayList<String>();
        for(Enumeration en = session.getAttributeNames(); en.hasMoreElements();)
            names.add((String) en.nextElement());
        return names;
    }

    public void invalidate() {
        session.invalidate();
    }

    public boolean isNew() {
        return session.isNew();
    }

    public void setAttribute(String name, Object object) {
        session.setAttribute(name, object);
    }

}
