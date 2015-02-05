package scat

import java.net.InetSocketAddress

import scat.Socket._
import scat.Client._

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Await, Promise}

/**
* Author: @aguestuser
* Date: 2/4/15.
* License: GPLv2
*/
class Server$Test extends org.specs2.mutable.Specification {

  "#acceptClients" should {

    lazy val server = new Server(getServerSock(2000))
    lazy val sAddr: SAddr = new InetSocketAddress(2000)

    lazy val c1 = new Client(getClientSock(2001))
    lazy val c2 = new Client(getClientSock(2002))

    "accept a client connection" in {

      server.acceptClients
      Await.ready(c1.connectTo(sAddr), Duration.Inf)
//      server.exit

      server.getLogs(server, 1).mkString ===
        "Client connection received from /0:0:0:0:0:0:0:1:2001"

    }

    "configure a client" in {

      server.acceptClients
      Await.ready(c1.connectTo(sAddr), Duration.Inf)
      Await.ready(c1.dispatch("austin".getBytes), Duration.Inf)
      Await.ready(Future(()), Duration(1500,"millis"))
      server.exit

      server.getLogs(server, 1).mkString ===
        "Created new client: \n" +
          "austin @ /0:0:0:0:0:0:0:1:2001\n" +
          "1 total client(s): \n" +
          "austin @ /0:0:0:0:0:0:0:1:2001"
      }
    }

//  trait server extends Before with After {
//    val kill = Promise[Unit]()
//    def before = {
//      Server.acceptClients(Socket.getServerSock(2000))
//      Await.result(kill.future, Duration.Inf)
//    }
//    def after = {
//      kill success { Future.successful(()) }
//    }
//  }
}
