import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{Server, Request}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class Handler extends AbstractHandler {
  var html = <h1>brlling brrrling cak cakc cak!</h1>

  override def handle(target: String, 
                      req: Request,
                      httpReq: HttpServletRequest, 
                      httpRes: HttpServletResponse) = {
    httpRes.setContentType("text/html")
    httpRes.setStatus(HttpServletResponse.SC_OK)
    httpRes.getWriter().println(html.toString)
    req.setHandled(true)
  }
}

val server = new Server(8901)
server.setHandler(new Handler)
server.start
