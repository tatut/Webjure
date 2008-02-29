/*
 * Created on Feb 29, 2008
 *
 */
package webjure;

import java.util.Collection;

/**
 * Facade interface for common methods in different session types.
 * 
 * @author Tatu Tarvainen (tatu.tarvainen@mac.com)
 */
public interface Session {

    /**
     * Store a session attribute.
     * 
     * @param name
     * @param object
     */
    public void setAttribute(String name, Object object);
    
    /**
     * Retrieve a session attribute value.
     * 
     */
    public Object getAttribute(String name);
    
    /**
     * Return the names of all attributes bound in this session.
     * 
     * @return
     */       
    public Collection<String> getAttributeNames();
    
    /**
     * Check if this session is new (the client doesn't yet know about it).
     * 
     * @return
     */
    public boolean isNew();
    
    /**
     * Invalidate this session.
     */
    public void invalidate();
    
}
