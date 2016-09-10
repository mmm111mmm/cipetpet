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

class TestSimple {

  var registerUrl = "http://localhost:8901/register" 

  @Test def test_400_on_duplicate_email: Unit = {
    assertEquals(
      post(registerUrl, """{"username": "a", "email": "a@b.com", "password": "blar"}""").getStatus,
      200
    )
    var resp = post(registerUrl, """{"username": "z", "email": "a@b.com", "password": "z"}""")
    var entity = resp.readEntity(classOf[JsonNode])

    assertEquals(resp.getStatus,                           400)
    assertEquals(entity.get(0).get("message").asText,      "Duplicate email")
    assertEquals(entity.get(0).get("path").asText,         "RegisterUser.email")
    assertEquals(entity.get(0).get("invalidValue").asText, "a@b.com")
  }

  @Test def test_400_on_duplicate_username: Unit = {
    assertEquals(
      post(registerUrl, """{"username": "dave", "email": "a@b.com", "password": "blar"}""").getStatus,
      200
    )
    var resp = post(registerUrl, """{"username": "dave", "email": "a@c.com", "password": "q"}""")
    var entity = resp.readEntity(classOf[JsonNode])

    assertEquals(resp.getStatus,                                       400)
    assertEquals(entity.get(0).get("message").asText,                  "Duplicate username")
    assertTrue(entity.get(0).get("path").asText.contains(".username"), "Path has username")
    assertEquals(entity.get(0).get("invalidValue").asText,             "dave")
  }

  @Test def test_400_on_null_email: Unit = {
    var resp = post(registerUrl, """{"username": "dave", "email": null, "password": "q"}""")
    var entity = resp.readEntity(classOf[JsonNode])

    assertEquals(resp.getStatus,                                       400)
    assertEquals(entity.get(0).get("message").asText,                  "may not be null")
    assertTrue(entity.get(0).get("path").asText.contains(".email"),    "Path has email")
    assertEquals(entity.get(0).get("invalidValue").asText,             "null")
  }

  @Test def test_400_on_null_username: Unit = {
    var resp = post(registerUrl, """{"username": null, "email": "a", "password": "q"}""")
    var entity = resp.readEntity(classOf[JsonNode])

    assertEquals(resp.getStatus,                                       400)
    assertEquals(entity.get(0).get("message").asText,                  "may not be null")
    assertTrue(entity.get(0).get("path").asText.contains(".username"), "Path has username")
    assertEquals(entity.get(0).get("invalidValue").asText,             "null")
  }

  @Test def test_400_on_null_password: Unit = {
    var resp = post(registerUrl, """{"username": "dave", "email": "a@b.com", "password": null}""")
    var entity = resp.readEntity(classOf[JsonNode])

    assertEquals(resp.getStatus,                                       400)
    assertEquals(entity.get(0).get("message").asText,                  "may not be null")
    assertTrue(entity.get(0).get("path").asText.contains(".password"), "Path has password")
    assertEquals(entity.get(0).get("invalidValue").asText,             "null")
  }

  /////////////////////////////////////////////

  @BeforeMethod def beforeMethod: Unit = {
    println("Before test")
    "rm db".!
    "bash db.migration.1".!
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
