import scala.concurrent.duration.DurationInt
import scala.io.StdIn

import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorRef
import akka.actor.ActorSelection.toScala
import akka.actor.ActorSystem
import akka.actor.Identify
import akka.actor.Props
import akka.actor.actorRef2Scala

/**
 * Illustrates how the ActorRef holds uid which changes after Actor has been
 * stopped and started again. Therefore it is safer to reference actors that
 * can be stopped and started again with ActorSelection.
 * 
 * Example 1:
 *  Terminal 1                            * Terminal 2
 *  sbt "runMain NodeRestart echo"        *
 *                                        * sbt "runMain NodeRestart actorselection"
 *                                        * // ping replies printed to terminal
 *  Ctrl-C                                *
 *  sbt "runMain NodeRestart echo"        *
 *                                        * // ping replies again printed in terminal
 *                                        * // sender UID has changed
 *                                          
 * Example 2:
 *  Terminal 1                            * Terminal 2
 *  sbt "runMain NodeRestart echo"        *
 *                                        * sbt "runMain NodeRestart actorref"
 *                                        * // ping replies printed to terminal
 *  Ctrl-C                                *
 *  sbt "runMain NodeRestart echo"        *
 *                                        * // no ping replies printed
 */
object NodeRestart extends App {
  
  val config = ConfigFactory.parseString("""
    akka {
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
      }
      remote {
        enabled-transports = ["akka.remote.netty.tcp"]
        netty.tcp {
          hostname = "127.0.0.1"
        }
      }
    }
  """)

  class EchoActor extends Actor {
    def receive = {
      case msg => sender() ! msg
    }
  }
  
  class ActorRefPinger extends Actor {
    import context.dispatcher
    
    def identifying: Receive = {
      {
        context.actorSelection("akka.tcp://system@127.0.0.1:2553/user/echoActor") ! Identify("id")
      }
      {
        case ActorIdentity("id", Some(actorRef)) => context.become(pinging(actorRef))
        case msg => println(s"received $msg")
      }: Receive
    }
    
    def pinging = (target: ActorRef) => {
      {
        context.system.scheduler.schedule(0.seconds, 1.second, self, "doPing")
      }
      {
        case "doPing" =>
          println(s"Sending ping to $target")
          target ! "ping"
        case msg =>
          println(s"Received $msg from $sender")
      }: Receive
    }
    
    def receive = identifying
  }
  
  class ActorSelectionPinger extends Actor {
    import context.dispatcher
    
    context.system.scheduler.schedule(0.seconds, 1.second, self, "doPing")
    val target = context.actorSelection("akka.tcp://system@127.0.0.1:2553/user/echoActor")
    
    def receive = {
      case "doPing" =>
        println(s"Sending ping to $target")
        target ! "ping"
        case msg =>
        println(s"Received $msg from $sender")
    }
  }
  
  val (port: Int, start) = args.head.toLowerCase match {
    case "actorref" => (2552, () => sys.actorOf(Props(new ActorRefPinger)))
    case "actorselection" => (2552, () => sys.actorOf(Props(new ActorSelectionPinger)))
    case "echo" => (2553, () => sys.actorOf(Props(new EchoActor), "echoActor"))
  }
  
  val sys = ActorSystem("system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port").withFallback(config))
  
  start()
  
  StdIn.readLine()
  sys.shutdown()
  sys.awaitTermination()
}