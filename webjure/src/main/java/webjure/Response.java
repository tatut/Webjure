package webjure;

import java.io.Writer;
import java.io.IOException;

/**
 * Facade for common operations in servlet and portlet responses.
 */
public interface Response { 
    
    /**
     * Get the response writer.
     * This allows to write HTML or other content to the requesting
     * client (browser).
     */
    public Writer getWriter() throws IOException;

    /**
     * Set the response content type.
     */
    public void setContentType(String contentType);

    /**
     * Get the underlying response object.
     */
    public Object getActualResponse();
}