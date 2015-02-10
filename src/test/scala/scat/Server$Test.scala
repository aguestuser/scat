package scat

import java.net.InetSocketAddress
import java.util.Date

import scat.Client._
import scat.Server._
import scat.Socket._

import scala.collection.concurrent.{TrieMap => CMap}
import scala.concurrent.Promise

/**
* Author: @aguestuser
* Date: 2/4/15.
* License: GPLv2
*/


class Server$Test extends org.specs2.mutable.Specification {

  import scat.Helpers._
  val sWait = 20
  val cWait = 60

  "Server" should {

    "accept a client connection" in {

      val (sPort, cPort) = (2000,2001)
      val (server, sAddr) = getServer(sPort)
      val (c, kc) = getClient(cPort, "austin")

      acceptClients(server)
      Thread.sleep(sWait)

      connectToServer(c,sAddr,kc)
      Thread.sleep(cWait)

      getLogs(server.logs, 1).mkString("\n") ===
        s"Client connection received from /0:0:0:0:0:0:0:1:$cPort"

    }

    "configure a client object" in {

      val (sPort, cPort) = (2002,2003)
      val (server, sAddr) = getServer(sPort)
      val (c, kc) = getClient(cPort, "austin")

      acceptClients(server)
      Thread.sleep(sWait)

      connectToServer(c,sAddr,kc) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(cWait)

      getLogs(server.logs, 2).mkString("\n") ===
        s"Client connection received from /0:0:0:0:0:0:0:1:$cPort\n" +
          "Created new client: \n" +
          s"austin @ /0:0:0:0:0:0:0:1:$cPort\n" +
          "1 total client(s): \n" +
          s"austin @ /0:0:0:0:0:0:0:1:$cPort"
    }

    "configure two client objects" in  {

      val (sPort, c1Port, c2Port) = (2004,2005,2006)
      val (server, sAddr) = getServer(sPort)
      val (c1, kc1) = getClient(c1Port, "austin")
      val (c2, kc2) = getClient(c2Port, "friend")

      acceptClients(server)
      Thread.sleep(sWait)

      connectToServer(c1,sAddr,kc1) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(cWait)

      connectToServer(c2,sAddr,kc2) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(cWait)

      getLogs(server.logs, 4).mkString("\n") ===
        s"Client connection received from /0:0:0:0:0:0:0:1:$c1Port\n" +
          "Created new client: \n" +
          s"austin @ /0:0:0:0:0:0:0:1:$c1Port\n" +
          "1 total client(s): \n" +
          s"austin @ /0:0:0:0:0:0:0:1:$c1Port\n" +
          s"Client connection received from /0:0:0:0:0:0:0:1:$c2Port\n" +
          "Created new client: \n" +
          s"friend @ /0:0:0:0:0:0:0:1:$c2Port\n" +
          "2 total client(s): \n" +
          s"austin @ /0:0:0:0:0:0:0:1:$c1Port\n" +
          s"friend @ /0:0:0:0:0:0:0:1:$c2Port"
    }

    "send messages between clients" in {

      val (sPort, c1Port, c2Port) = (2007,2008,2009)
      val (server, sAddr) = getServer(sPort)
      val (c1, kc1) = getClient(c1Port, "austin")
      val (c2, kc2) = getClient(c2Port, "friend")

      acceptClients(server)
      Thread.sleep(sWait)

      connectToServer(c1,sAddr,kc1) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(cWait)

      connectToServer(c2,sAddr,kc2) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(cWait)

      val (cc1, cc2) = (CnctClient(c1.sock,c1.handle), CnctClient(c2.sock,c2.handle))
      Client.dispatchOut(cc1, "hi friend!".getBytes, kc1)
      Thread.sleep(cWait)

      Client.dispatchOut(cc2, "hi austin!".getBytes, kc2)
      Thread.sleep(cWait)

      getLogs(server.logs, 2).mkString("\n") ===
        "Relayed message from austin @ /0:0:0:0:0:0:0:1:2008\n" +
          "austin: hi friend!\n" +
          "Relayed message from friend @ /0:0:0:0:0:0:0:1:2009\n" +
          "friend: hi austin!"
    }

    "process client exits" in {

      val (sPort, c1Port, c2Port) = (2010,2011,2012)
      val (server, sAddr) = getServer(sPort)
      val (c1, kc1) = getClient(c1Port, "austin")
      val (c2, kc2) = getClient(c2Port, "friend")

      acceptClients(server)
      Thread.sleep(sWait)

      connectToServer(c1,sAddr,kc1) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(cWait)

      connectToServer(c2,sAddr,kc2) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(cWait)

      val (cc1, cc2) = (CnctClient(c1.sock,c1.handle), CnctClient(c2.sock,c2.handle))
      Client.dispatchOut(cc1, "hi friend!".getBytes, kc1)
      Thread.sleep(cWait)

      Client.dispatchOut(cc2, "hi austin!".getBytes, kc2)
      Thread.sleep(cWait)

      Client.dispatchOut(cc1, "exit".getBytes, kc1)
      Thread.sleep(cWait)

      Client.dispatchOut(cc2, "exit".getBytes, kc2)
      Thread.sleep(cWait)

      getLogs(server.logs, 2).mkString("\n") ===
        "Closed connection with austin @ /0:0:0:0:0:0:0:1:2011\n" +
          "Closed connection with friend @ /0:0:0:0:0:0:0:1:2012"
    }
  }
}


object Helpers {

  def getPorts(ports: Stream[Int], num: Int): List[Int] =
  ports.take(num).toList

  def getServer(sPort: Int): (Server, InetSocketAddress) = {
    val sAddr: SAddr = new InetSocketAddress(sPort)
    val ks = Promise[Unit]()
    val server = Server(getServerSock(sPort), CMap[RmtClient, Boolean](), CMap[Date, String](), ks)
    (server, sAddr) }

  def getClient(cPort: Int, handle: String): (CfgClient, Promise[Unit]) =
  (CfgClient(getClientSock(cPort), handle.getBytes), Promise[Unit]())

}

//class serverWithOneClient(_sPort: Int, _cPort: Int) extends org.specs2.mutable.After {
//  import scat.Helpers._
//
//val ports = Stream from 2000
//  val sWait = 100
//  val cWait = 100
//
//  val (sPort, cPort) = (_sPort, _cPort)
//  val (server, sAddr) = getServer(sPort)
//  val (c, kc) = getClient(cPort, "austin")
//
//  def after: Future[Unit] = {
//    Server.doKill(server) flatMap { _ =>
//      Future successful { c.sock.close() }
//    }
//  }
//}
