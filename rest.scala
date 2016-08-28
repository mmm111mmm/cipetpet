import java.util.{ArrayList, HashMap, HashSet}
import scala.util._
import javax.validation.constraints.{NotNull}
import javax.validation.{Valid, ConstraintViolation, ConstraintViolationException}
import javax.validation.metadata.ConstraintDescriptor
import javax.ws.rs.core.{MediaType, UriBuilder, Response}
import javax.ws.rs.{ApplicationPath, GET, Path, Produces, PathParam, QueryParam, POST}
import org.glassfish.jersey.server.{ResourceConfig, ServerProperties}
import org.glassfish.jersey.jetty.JettyHttpContainerFactory
import com.fasterxml.jackson.annotation.{JsonAutoDetect}
import com.newfivefour.jerseycustomvalidationerror.CustomValidationError._
import java.sql.{DriverManager, Statement, ResultSet, SQLException}

object rest { 

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class RegisterUser extends Object() {
    @NotNull var email: String = null
    @NotNull var username: String = ""
    @NotNull var password: String = ""
  }

  trait ResultSetToMap {
    def convertResultSetToMap(rs: ResultSet) : List[Map[String, Object]] = {
      var md = rs.getMetaData 
      var list = List[Map[String, Object]]()
      while (rs.next) list :+= (1 to md.getColumnCount).map(i => md.getColumnName(i) -> rs.getString(i)).toMap
      list
    }
  }

  case class SqlUniqueConstraintException(var column:String, var attempt:String) extends Exception(column)

  class Sqlite(dbstr: String) extends ResultSetToMap {
    var UNIQUE_EXP_REGEX= ".*failed: .*\\.(.*)"
    var conn = DriverManager.getConnection(dbstr)
    var stmt:Statement = null
    var rs: ResultSet = null

    def convertSqlException(e: Throwable, inputMap: Map[String, String] = null) = 
      e.getMessage match {
        case s if s.contains("UNIQUE constraint") => {
          var col = UNIQUE_EXP_REGEX.r("1").findFirstMatchIn(s).get.group("1")
          throw SqlUniqueConstraintException(col, inputMap.get(col).get)
        } 
        case _ => throw e
      }

    def insert(table: String, inputMap: Map[String, String]): Integer = {
      var insert = Try({
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
      var query = Try({
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

  }

  @Path("/") class Hello {

    var sqlAccess: Sqlite = new Sqlite("jdbc:sqlite:db")

    println(sqlAccess.query("select * from users"))

    def throwSqlToJerseyException(inputOb: Object, e: Throwable) = 
      e match {
        case SqlUniqueConstraintException(col, input) => throwCustomValidationException(inputOb, col, "Duplicate " + col, input)
        case e: Throwable => throw e
      }

    @Path("register") @POST @Produces(Array(MediaType.APPLICATION_JSON))
    def register(@Valid r: RegisterUser) = 
      Try(
        sqlAccess.insert("users", Map("username" -> r.username, "email" -> r.email, "password" -> r.password))
      ) match {
        case Failure(e) => throwSqlToJerseyException(r, e)
        case Success(s) => Response.ok().build
      }
  }

  def main(args: Array[String]): Unit =
    JettyHttpContainerFactory.createServer(
      UriBuilder.fromUri("http://localhost/").port(8901).build(),
      new ResourceConfig() {
        register(classOf[Hello])
        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true)
      }
    )

}
