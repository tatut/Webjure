package webjure.servlet;

import webjure.Response;
import java.io.Writer;
import java.io.IOException;

public class ServletResponse implements Response {

    private javax.servlet.http.HttpServletResponse response;

    ServletResponse(javax.servlet.http.HttpServletResponse actual) {
        this.response = actual;
    }

    public Writer getWriter() throws IOException {
        return response.getWriter();
    }
    
    public void setContentType(String contentType) {
        response.setContentType(contentType);
    }

    public Object getActualResponse() {
        return response;
    }

}