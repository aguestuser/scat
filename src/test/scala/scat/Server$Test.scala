package scat

import java.net.InetSocketAddress
import java.util.Date

import org.specs2.specification.After
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

  "Server" should {

    lazy val sAddr: SAddr = new InetSocketAddress(2000)
    lazy val ks = Promise[Unit]()
    lazy val server = Server(getServerSock(2000), CMap[RmtClient, Boolean](), CMap[Date, String](), ks)

    lazy val (cSock1, h1, kc1) = (getClientSock(2001), "austin".getBytes, Promise[Unit]())
    lazy val c1 = CfgClient(cSock1, h1)

    lazy val (cSock2, h2, kc2) = (getClientSock(2002), "friend".getBytes, Promise[Unit]())
    lazy val c2 = CfgClient(cSock2, h2)

    "accept a client connection" in new closeSocketsAfter {

      acceptClients(server)
      Thread.sleep(5)

      connectToServer(c1,sAddr,kc1)
      Thread.sleep(20)

      getLogs(server.logs, 1).mkString("\n") ===
        "Client connection received from /0:0:0:0:0:0:0:1:2001"

    }

    "configure a client object" in new closeSocketsAfter {

      acceptClients(server)
      Thread.sleep(5)

      connectToServer(c1,sAddr,kc1) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(20)

      getLogs(server.logs, 2).mkString("\n") ===
        "Client connection received from /0:0:0:0:0:0:0:1:2001\n" +
          "Created new client: \n" +
          "austin @ /0:0:0:0:0:0:0:1:2001\n" +
          "1 total client(s): \n" +
          "austin @ /0:0:0:0:0:0:0:1:2001\n"
    }

    "configure two client objects" in new closeSocketsAfter {

      acceptClients(server)
      Thread.sleep(5)

      connectToServer(c1,sAddr,kc1) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(20)

      connectToServer(c2,sAddr,kc2) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(20)

      getLogs(server.logs, 4).mkString("\n") ===
        "Client connection received from /0:0:0:0:0:0:0:1:2001\n" +
          "Created new client: \n" +
          "austin @ /0:0:0:0:0:0:0:1:2001\n" +
          "1 total client(s): \n" +
          "austin @ /0:0:0:0:0:0:0:1:2001\n" +
          "Client connection received from /0:0:0:0:0:0:0:1:2002\n" +
          "Created new client: \n" +
          "friend @ /0:0:0:0:0:0:0:1:2002\n" +
          "2 total client(s): \n" +
          "austin @ /0:0:0:0:0:0:0:1:2001\n" +
          "friend @ /0:0:0:0:0:0:0:1:2002"
    }

    "send messages between clients" in new closeSocketsAfter {

      acceptClients(server)
      Thread.sleep(5)

      connectToServer(c1,sAddr,kc1) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(20)

      connectToServer(c2,sAddr,kc2) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(20)

      val (cc1, cc2) = (CnctClient(c1.sock,c1.handle), CnctClient(c2.sock,c2.handle))
      Client.dispatchOut(cc1, "hi friend!".getBytes, kc1)
      Thread.sleep(20)

      Client.dispatchOut(cc2, "hi austin!".getBytes, kc2)
      Thread.sleep(20)

      getLogs(server.logs, 2).mkString("\n") ===
        "Relayed message from austin @ /0:0:0:0:0:0:0:1:2001\n" +
          "austin: hi friend!\n" +
          "Relayed message from friend @ /0:0:0:0:0:0:0:1:2002\n" +
          "friend: hi austin!"
    }

    "process client exits" in new closeSocketsAfter {

      acceptClients(server)
      Thread.sleep(5)

      connectToServer(c1,sAddr,kc1) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(20)

      connectToServer(c2,sAddr,kc2) flatMap { cnctCl =>
        Client.shakeHands(cnctCl) }
      Thread.sleep(20)

      val (cc1, cc2) = (CnctClient(c1.sock,c1.handle), CnctClient(c2.sock,c2.handle))
      Client.dispatchOut(cc1, "hi friend!".getBytes, kc1)
      Thread.sleep(20)

      Client.dispatchOut(cc2, "hi austin!".getBytes, kc2)
      Thread.sleep(20)

      Client.dispatchOut(cc1, "exit".getBytes, kc1)
      Thread.sleep(20)

      Client.dispatchOut(cc2, "exit".getBytes, kc2)
      Thread.sleep(20)

      getLogs(server.logs, 2).mkString("\n") ===
        "Closed connection with austin @ /0:0:0:0:0:0:0:1:2001\n" +
          "Closed connection with friend @ /0:0:0:0:0:0:0:1:2002"
    }

    trait closeSocketsAfter extends After {
      //TODO figure out setup / teardown
      def after = {
        server.sSock.close()
        c1.sock.close()
        c2.sock.close()
      }
    }
  }
}


