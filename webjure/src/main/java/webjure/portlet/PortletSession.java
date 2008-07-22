package webjure.portlet;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Enumeration;

import webjure.Session;

public class PortletSession implements Session {

    private javax.portlet.PortletSession session;

    PortletSession(javax.portlet.PortletSession actual) {
        this.session = actual;
    }

    public void setAttribute(String name, Object object) {
        session.setAttribute(name,object);
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
    
    public boolean isNew() {
        return session.isNew();
    }
    
    public void invalidate() {
        session.invalidate();
    }

}