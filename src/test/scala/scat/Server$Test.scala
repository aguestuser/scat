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

    lazy val sSock: SSC = Socket.getServerSock(2000)
    lazy val sAddr: SAddr = new InetSocketAddress(2000)
    lazy val cSock1: SC = Socket.getClientSock(2001)
    lazy val cSock2: SC = Socket.getClientSock(2001)
    lazy val kill: Promise[Unit] = Promise[Unit]()

    "accept a client" in {

      val server = new Server

      server.acceptClients(sSock)
      Await.ready(Future{()}, Duration(200,"millis"))

      initialize(cSock1, sAddr, kill)
      write("austin".getBytes, cSock1)
      Await.ready(Future{()}, Duration(200,"millis"))

      server.getLogs(1).mkString ===
        "Created new client: \n" +
        "austin @ /0:0:0:0:0:0:0:1:2001\n" +
        "1 total client(s): \n" +
        "austin @ /0:0:0:0:0:0:0:1:2001"

      //TODO add setup/teardown to open and close sockets
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
