import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{Handler, Server, Request}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class HttpServer {
  val handler = new MutableHandler

  def run(port: Int) = {
    val server = new Server(port)
    server.setHandler(handler)
    server.start
  }
}

class MutableHandler extends AbstractHandler {
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

new HttpServer().run(8901)
