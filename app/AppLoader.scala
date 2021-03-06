import actors.StatsActor
import actors.StatsActor.Ping
import akka.actor.Props
import controllers.{Application, Assets}
import filters.StatsFilter
import play.api.ApplicationLoader.Context
import play.api._
import play.api.cache.EhCacheComponents
import play.api.db.{HikariCPComponents, DBComponents}
import play.api.db.evolutions.{DynamicEvolutions, EvolutionsComponents}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{Filter, EssentialFilter}
import play.api.routing.Router
import router.Routes
import com.softwaremill.macwire._
import scalikejdbc.config.DBs
import services.{UserAuthAction, AuthService, WeatherService, SunService}

import scala.concurrent.Future

class AppApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach { configurator =>
      configurator.configure(context.environment)
    }
    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents extends BuiltInComponents with AhcWSComponents
 with EvolutionsComponents with DBComponents with HikariCPComponents
 with EhCacheComponents {
  lazy val assets: Assets = wire[Assets]
  lazy val prefix: String = "/"
  lazy val router: Router = wire[Routes]
  lazy val applicationController = wire[Application]

  lazy val sunService = wire[SunService]
  lazy val weatherService = wire[WeatherService]
  lazy val statsFilter: Filter = wire[StatsFilter]
  override lazy val httpFilters = Seq(statsFilter)

  lazy val authService = new AuthService(defaultCacheApi)

  lazy val userAuthAction = wire[UserAuthAction]

  lazy val dynamicEvolutions = new DynamicEvolutions

  lazy val statsActor = actorSystem.actorOf(
    Props(wire[StatsActor]), StatsActor.name)


  applicationLifecycle.addStopHook { () =>
    Logger.info("The app is about to stop")
    DBs.closeAll()
    Future.successful(Unit)
  }

  val onStart = {
    Logger.info("The app is about to start")
    DBs.setupAll()
    applicationEvolutions
    statsActor ! Ping
  }
}
