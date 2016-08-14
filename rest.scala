import java.util.ArrayList
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import javax.ws.rs.{ApplicationPath, GET, Path, Produces}
import javax.ws.rs.core.{MediaType, UriBuilder}
import javax.ws.rs.{ApplicationPath, GET, Path, Produces}
import javax.ws.rs.core.{MediaType}
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{Server, Request}
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.jetty.JettyHttpContainerFactory

object rest {

  @Path("/") class Hello {

    @Path("hello") @GET @Produces(Array(MediaType.APPLICATION_JSON))
    def example : ArrayList[String] = {
      var al = new ArrayList[String]
      al.add("cipetpet")
      return al
    }

    @Path("") @GET @Produces(Array(MediaType.TEXT_HTML))
    def home : String = {
      return """<h1>THIS IS NOW A WEB SERVICE</h1>
                |<p>We should really have automatically generated documentation!</p>
                |<p>Check the code in github instead.</p>
                |""".stripMargin
    }
  }


  def main(args: Array[String]) : Unit = {
    JettyHttpContainerFactory.createServer(
      UriBuilder.fromUri("http://localhost/").port(8901).build(),
      new ResourceConfig(classOf[Hello])
    )
  }
}
