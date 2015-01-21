import akka.actor.{ Actor, ActorSystem }
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import akka.actor.Props
import akka.event.Logging._

object LogMessages extends App {
  
  val config = ConfigFactory.parseString("""
    akka {
      loglevel = DEBUG
      loggers = ["LogMessages$MyEventListener"]
    
      remote {
        enabled-transports = ["akka.remote.netty.tcp"]
        netty.tcp {
          hostname = "127.0.0.1"
        }
      
        log-sent-messages = on
        log-received-messages = on
        log-frame-size-exceeding = 1000b
    
        log-remote-lifecycle-events = off
    
        watch-failure-detector {
          # Our timeouts can be so high, the PHI model basically doesn't
          # know what to do, so just use a single timeout. 
          # http://doc.akka.io/docs/akka/snapshot/scala/remoting.html
          acceptable-heartbeat-pause = 60 s
        }
      }
    
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
        debug {
          receive = on
          #autoreceive = on
          #lifecycle = on
          #fsm = on
          #event-stream = on
          unhandled = on
        }
      }
    }
    """)
    
  class Pinger extends Actor {
    
    import context.dispatcher
    context.system.scheduler.schedule(0.seconds, 1.second, self, "send")
    
    def receive = {
      case "send" => context.actorSelection("akka.tcp://system-2553@127.0.0.1:2553/user/ponger") ! "ping"
      case "pong" =>
    }
  }
  
  class Ponger extends Actor {
    
    def receive = {
      case "ping" => sender ! "pong"
    }
  }
  
  class MyEventListener extends Actor {
    
    val SentMessage = "received .*: \\[(.*)\\] to .*<\\+\\[(.*)\\] from .*Actor\\[(.*)\\]\\(\\)\\]".r
    
    def receive = {
      case InitializeLogger(_)                        ⇒ sender ! LoggerInitialized
      case Error(cause, logSource, logClass, message) =>
      case Warning(logSource, logClass, message)      =>
      case Info(logSource, logClass, message)         =>
      case Debug(logSource, logClass, message)        => message match {
        case SentMessage(msg, to, from) =>
          println(s"$msg from $from to $to")
        case _ =>
      }
    }
}
  
  val List(sys1, sys2) = List("2552", "2553") map { port =>
    ActorSystem(s"system-$port", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port").withFallback(config))
  } 
   
  sys2.actorOf(Props(new Ponger), "ponger")
  sys1.actorOf(Props(new Pinger), "pinger")
  
}