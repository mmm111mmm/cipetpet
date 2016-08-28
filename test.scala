import collection.JavaConversions._
import java.lang.reflect.InvocationTargetException
import java.util.{Map => JavaMap}
import javax.ws.rs._
import javax.ws.rs.core._
import javax.ws.rs.client._
import org.testng.annotations._
import org.testng.Assert._
import com.fasterxml.jackson.databind.node._
import com.fasterxml.jackson.databind._

class TestSimple {

  @BeforeSuite def beforeSuite: Unit = {
    rest.main(null)
  } 

  @Test def test_400_on_repeat_request: Unit =
    assertEquals(
      post(
        "http://localhost:8901/register",
        """{"username": "", "email": "blar", "password": "blar"}"""
      ).getStatus,
      400
    )


  /////////////////////////////////////////////


  def post(url: String, s: String) : Response =
    try {
      var client = ClientBuilder.newClient().target(url).request("application/json")
      var post = classOf[Invocation.Builder].getMethod("post", classOf[Entity[JsonNode]])
      post.invoke(client, Entity.entity(new ObjectMapper().readTree(s), "application/json")).asInstanceOf[Response]
    } catch {
      case e:InvocationTargetException => {
        e.getTargetException.asInstanceOf[BadRequestException].getResponse()
      }
    }

}
