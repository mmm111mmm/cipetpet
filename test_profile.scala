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
import sys.process._

class ProfileTests {

  var registerUrl = "http://localhost:8901/register" 
  var loginUrl = "http://localhost:8901/login" 
  var profileUrl = "http://localhost:8901/profile" 

  @Test def test_200_on_good_profile : Unit = {
    assertEquals(
      post(registerUrl, """{"username": "a", "email": "a@b.com", "password": "blar"}""").getStatus,
      200
    )
    var token = post(loginUrl, """{"username": "a", "email": "a@b.com", "password": "blar"}""")
                       .readEntity(classOf[JsonNode]).get("session").asText
    var resp  = get(profileUrl+"/"+token);
    var body  = resp.readEntity(classOf[JsonNode])

    assertEquals(resp.getStatus, 200)
    assertEquals(body.get("username").asText, "a")
    assertEquals(body.get("email").asText, "a@b.com")
    assertTrue(body.get("password")==null)
  }

  @Test def test_400_on_bad_profile : Unit = {
    var resp  = get(profileUrl+"/blarblarblar");
    assertEquals(resp.getStatus, 400)
  }

  /////////////////////////////////////////////

  @BeforeMethod def beforeMethod: Unit = {
    println("Before test")
    "rm db".!
    "bash db.migration.1".!
    "bash db.migration.2".!
    rest.main(null)
  } 

  @AfterMethod def afterMethod: Unit = {
    rest.server.stop
  } 

  /////////////////////////////////////////////

  def get(url: String) : Response =
    try {
      ClientBuilder.newClient().target(url).request("application/json").get().asInstanceOf[Response]
    } catch {
      case e:BadRequestException => e.getResponse()
    }

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
