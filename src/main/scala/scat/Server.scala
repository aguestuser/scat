package scat

import java.nio.channels.{AsynchronousServerSocketChannel => SSC, AsynchronousSocketChannel => SC}
import java.util.Date

import scat.Socket._

import scala.collection.concurrent.{TrieMap => CMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Author: @aguestuser
 * Date: 1/31/15 - 2/6/15
 * License: GPLv2
 */

object RunServer extends App {
  import scat.Server._
  try {
    val kill = Promise[Unit]()
    val server = Server(getServerSock(args(0).toInt), CMap[RmtClient,Boolean](), CMap[Date,String](), kill)
    run(server)
    Await.result(kill.future, Duration.Inf)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

case class Server(sSock: SSC,
                  clients: CMap[RmtClient,Boolean],
                  logs: CMap[Date,String],
                  kill: Promise[Unit])

object Server extends Logger with ClientManager {

//  val clients: CMap[Client,Boolean] = CMap[Client,Boolean]()
//  val logs: CMap[Date,String] = CMap[Date,String]()
//  val kill: Promise[Future[Unit]]  = Promise[Future[Unit]]()

  def run(server: Server): Future[Unit] = {
    acceptClients(server)
    listenForKill(server) }

  def acceptClients(server: Server): Future[Unit] = server match {
    case Server(sSock, clients,logs,k) =>
      accept(sSock) flatMap { sock =>
        logConnection(server.logs, sock)
        shakeHands(sock) flatMap { client =>
          addClient(clients, client) flatMap { c =>
            listen(server, client)
            logNewClient(server.logs, server.clients, c)} }
        acceptClients(server) } }

  def listen(server: Server, cl: RmtClient): Future[Unit] =
    read(cl.sock) flatMap { msg =>
      dispatch(server, cl, msg) flatMap { cont =>
        if (cont) listen(server, cl)
        else Future.successful(()) } }

  def dispatch(server: Server, cl: RmtClient, msg: Array[Byte]): Future[Boolean] =
    if (strFromWire(msg) == "exit") close(server, cl)
    else relay(server, cl, msg)

  def close(server: Server, cl: RmtClient): Future[Boolean] = {
    server.clients remove (cl,true)
    logClose(server.logs, cl)
    cl.sock.close()
    Future successful { false }
  }
//    Future successful {
//      cl.sock.close()
//      server.clients remove (cl,true)
//      logClose(server.logs, cl)
//      false }

  def relay(server: Server, cl: RmtClient, msg: Array[Byte]): Future[Boolean] =
    Future sequence {
      (server.clients.keySet - cl) map { c => write(c.sock,msg) }
    } flatMap { _ =>
      logRelay(server.logs, cl, msg)
      Future successful { true } }

  def listenForKill(server: Server): Future[Unit] =
    Future {
      scala.io.StdIn.readLine()
    } flatMap { msg =>
      if (msg == "exit") doKill(server)
      else listenForKill(server) }

  def doKill(server: Server): Future[Unit] =
    Future sequence {
      server.clients.keySet.map { c => write(c.sock, "exit".getBytes) }
    } flatMap { _ =>
      server.clients.keySet map { cl => cl.sock.close() }
      server.sSock.close()
      server.kill success { Future successful { () } }
      Future successful { () } }

}