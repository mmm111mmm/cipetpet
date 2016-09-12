import java.util.{ArrayList, HashMap, HashSet, UUID}
import java.sql.{DriverManager, Statement, ResultSet, SQLException}
import scala.util._
import collection.JavaConversions._
import javax.validation.constraints.{NotNull}
import javax.validation.{Valid, ConstraintViolation, ConstraintViolationException}
import javax.validation.metadata.ConstraintDescriptor
import javax.ws.rs.{ApplicationPath, GET, Path, Produces, PathParam, QueryParam, POST}
import javax.ws.rs.core.{MediaType, UriBuilder, Response, Context}
import javax.ws.rs.container.{ContainerRequestContext,ContainerRequestFilter}
import org.glassfish.jersey.server.{ResourceConfig, ServerProperties}
import org.glassfish.jersey.jetty.JettyHttpContainerFactory
import org.eclipse.jetty.server.Server
import com.fasterxml.jackson.annotation.{JsonAutoDetect}
import com.newfivefour.jerseycustomvalidationerror.CustomValidationError._
import org.mindrot.jbcrypt.BCrypt

// TODO
// * Logout of session
// * Logout of all sessions
// * Insert into companies if logged in
// * View all companies
// * Delete company if you are the owner 

object rest { 

  var server:Server = null
  var sqlA: Sqlite = new Sqlite("jdbc:sqlite:db")

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class RegisterUser extends Object() {
    @NotNull var email: String = null
    @NotNull var username: String = null
    @NotNull var password: String = null 
  }

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class LoginUser() extends Object() {
    @NotNull var email: String = null
    @NotNull var username: String = null
    @NotNull var password: String = null
  }

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  case class UserSession(@NotNull var session: String) {}

  trait ResultSetToMap {
    def convertResultSetToMap(rs: ResultSet) : List[Map[String, Object]] = {
      var md = rs.getMetaData 
      var list = List[Map[String, Object]]()
      while (rs.next) list :+= (1 to md.getColumnCount).map(i => md.getColumnName(i) -> rs.getString(i)).toMap
      list
    }
  }

  case class SqlUniqueConstraintException(var column:String, var attempt:String) extends Exception(column)
  case class SqlExceptedFewerRows() extends Throwable
  case class SqlNoRowFound() extends Throwable

  class Sqlite(dbstr: String) extends ResultSetToMap {
    var UNIQUE_EXP_REGEX= ".*failed: .*\\.(.*)"
    var UNIQUE_CONSTRAINT_CONTAINS = "UNIQUE constraint"
    var conn = DriverManager.getConnection(dbstr)
    var stmt:Statement = null
    var rs: ResultSet = null

    def insert(table: String, inputMap: Map[String, String]): Integer = {
      var insert = Try ({
        stmt = conn.createStatement
        var qCs = inputMap.keys.map(x => "'"+x+"'").mkString(",")
        var qVs = inputMap.values.map(x => "'"+x+"'").mkString(",")
        stmt.executeUpdate("insert into "+ table +" (" + qCs + ") values("+ qVs + ")")
      })
      if(stmt!=null) stmt.close
      insert match {
        case Success(s) => s
        case Failure(e) => convertSqlException(e, inputMap)
      }
    }

    def query(sql: String): List[Map[String, Object]] = {
      var query = Try ({
        stmt = conn.createStatement
        rs = stmt.executeQuery(sql)
        convertResultSetToMap(rs)
      })
      if(stmt!=null) stmt.close
      if(rs!=null) rs.close
      query match {
        case Success(success) => success
        case Failure(e) => convertSqlException(e)
      }
    }

    def retrieveOne(table: String, cols: Map[String, String]): Map[String, Object] = {
      var results = retrieve(table, cols)
      if(results.length>1) throw new SqlExceptedFewerRows
      else if(results.length==1) results(0)
      else throw new SqlNoRowFound
    }

    def retrieve(table: String, cols: Map[String, String]): List[Map[String, Object]] = {
      var query = Try ({
        stmt = conn.createStatement
        var sql = "select * from " + table + " where " + cols.map(x => x._1 + "='" + x._2+"'").mkString(" and ");
        rs = stmt.executeQuery(sql)
        convertResultSetToMap(rs)
      })
      if(stmt!=null) stmt.close
      if(rs!=null) rs.close
      query match {
        case Success(success) => success
        case Failure(e) => convertSqlException(e)
      }
    }

    def convertSqlException(e: Throwable, inputMap: Map[String, String] = null) = e.getMessage match {
      case msg if msg.contains(UNIQUE_CONSTRAINT_CONTAINS) => {
        var col = UNIQUE_EXP_REGEX.r("1").findFirstMatchIn(msg).get.group("1")
        throw SqlUniqueConstraintException(col, inputMap.get(col).get)
      } 
      case _ => throw e
    }

  }

  class SessionFilter(var sqlAccess: Sqlite) extends ContainerRequestFilter {
    override def filter(requestContext: ContainerRequestContext):Unit = {
      try {
        var header = requestContext.getHeaderString("SESSION")
        var sess = sqlAccess.retrieveOne("user_sessions", Map("token" -> header))
        var id   = sess.get("user_id").get.asInstanceOf[String]
        var user = sqlAccess.retrieveOne("users", Map("id" -> id))
        requestContext.setProperty("SESSION", user)
      } catch { 
        case _ : Throwable => 
      }
    }
  }

  object UserUtils {
    def get(request: ContainerRequestContext, prop: String) =
      Try (
        request.getProperty("SESSION").asInstanceOf[Map[String,String]].get(prop).get
      ) match {
        case Failure(e) => null
        case Success(s) => s
      }

    def loggedIn(op: => Any)(implicit request: ContainerRequestContext) =
      Try ({
        var user = UserUtils.get(request, "username")
        if(user==null || user.trim.length==0) throw new IllegalArgumentException()
        op
      }) match {
        case Success(s) => s
        case Failure(e: IllegalArgumentException) => Response.status(403).build 
        case Failure(e) => Response.status(500).build 
      }
  }

  @Path("/") class Users(var sqlAccess: Sqlite) {

    def throwSqlToJerseyException(inputOb: Object, e: Throwable) = e match {
      case SqlUniqueConstraintException(col, input) => throwCustomValidationException(inputOb, col, "Duplicate " + col, input)
      case SqlNoRowFound()                          => throwCustomValidationException(inputOb, "", "Not found", "")
      case _ => throw e
    }

    @Path("login") @POST @Produces(Array(MediaType.APPLICATION_JSON))
    def login(@Valid r: LoginUser) = 
      Try ({
        var resp = sqlAccess.retrieveOne("users", Map("username" -> r.username))
        if(!BCrypt.checkpw(r.password, resp.get("password").get.asInstanceOf[String])) throw new SqlNoRowFound
        var uuid = UserSession(UUID.randomUUID.toString)
        sqlAccess.insert("user_sessions", 
                         Map("user_id" -> resp.get("id").get.asInstanceOf[String], 
                             "token"   -> uuid.session))
        uuid
      }) match {
        case Failure(SqlNoRowFound()) => throwCustomValidationException(r, "general", "Bad username or pw", "") 
        case Failure(e)               => throwSqlToJerseyException(r, e)
        case Success(s)               => Response.ok(s).build
      }

    @Path("profile/{session}") @GET @Produces(Array(MediaType.APPLICATION_JSON))
    def profile(@PathParam("session") session: String) = 
      Try ({
        var sess = sqlAccess.retrieveOne("user_sessions", Map("token" -> session))
        var id   = sess.get("user_id").get.asInstanceOf[String]
        var user = sqlAccess.retrieveOne("users", Map("id" -> id))
        var filt = user.filter(m => !m._1.equals("password"))
        mapAsJavaMap(filt)
      }) match {
        case Failure(SqlNoRowFound()) => throwCustomValidationException(new Object(), "general", "Not found", "") 
        case Failure(e)               => throwSqlToJerseyException(new Object(), e)
        case Success(s)               => Response.ok(s).build
      }

    @Path("register") @POST @Produces(Array(MediaType.APPLICATION_JSON))
    def register(@Valid r: RegisterUser) = 
      Try (
        sqlAccess.insert("users", Map("username" -> r.username, 
                                      "email"    -> r.email,  
                                      "password" -> BCrypt.hashpw(r.password, BCrypt.gensalt(12))))
      ) match {
        case Failure(e) => throwSqlToJerseyException(r, e)
        case Success(s) => Response.ok().build
      }

  }

  @Path("/a") class Other {

    @Context implicit var request:ContainerRequestContext = null

    @Path("/thing/{hi}") @GET @Produces(Array(MediaType.APPLICATION_JSON))
    def login(@PathParam("hi") s: String) = 
      UerUtils.loggedIn (
        mapAsJavaMap(Map("thing"->UserUtils.get(request, "email")))
      )
  }

  def main(args: Array[String]): Unit =
    server = JettyHttpContainerFactory.createServer(
      UriBuilder.fromUri("http://localhost/").port(8901).build(),
      new ResourceConfig() {
        classOf[ResourceConfig].getMethod("register", classOf[Object]).invoke(this, new Users(sqlA))
        classOf[ResourceConfig].getMethod("register", classOf[Object]).invoke(this, new SessionFilter(sqlA))
        register(classOf[Other])
        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true)
      }
    ) 
}
