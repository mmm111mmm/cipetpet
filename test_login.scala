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

class LoginTests {

  var registerUrl = "http://localhost:8901/register" 
  var loginUrl = "http://localhost:8901/login" 

  @Test def test_200_on_good_login : Unit = {
    assertEquals(
      post(registerUrl, """{"username": "a", "email": "a@b.com", "password": "blar"}""").getStatus,
      200
    )

    var resp = post(loginUrl, """{"username": "a", "email": "a@b.com", "password": "blar"}""")
    assertEquals(resp.getStatus, 200)

    var entity = resp.readEntity(classOf[JsonNode])
    assertTrue(entity.get("session").asText.length>1)

  }

  @Test def test_400_on_bad_password: Unit = {
    assertEquals(
      post(registerUrl, """{"username": "a", "email": "a@b.com", "password": "blar"}""").getStatus,
      200
    )
    assertEquals(
      post(loginUrl, """{"username": "a", "email": "a@b.com", "password": "wrong"}""").getStatus,
      400
    )
  }

  @Test def test_400_on_bad_username: Unit = {
    assertEquals(
      post(registerUrl, """{"username": "a", "email": "a@b.com", "password": "blar"}""").getStatus,
      200
    )
    assertEquals(
      post(loginUrl, """{"username": "az", "email": "a@bz.com", "password": "blar"}""").getStatus,
      400
    )
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
