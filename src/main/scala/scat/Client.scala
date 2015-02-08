package scat

import java.net.InetSocketAddress

import scat.Socket._
import scat.Client._


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Author: aguestuser
 * Date: 2/2/15.
 * License: GPLv2
 */

object RunClient extends App {
  val (sAddr, cSock) = (new InetSocketAddress(args(0).toInt), getClientSock(args(1).toInt))
  val kill = Promise[Unit]()
  run(sAddr, cSock, kill)
  Await.result(kill.future, Duration.Inf)
}

trait Client
case class PreClient(sock: SC) extends Client
case class CfgClient(sock: SC, handle: Array[Byte]) extends Client
case class CnctClient(sock: SC, handle: Array[Byte]) extends Client
case class RmtClient(sock: SC, handle: Array[Byte]) extends Client

object Client {

  def run(sAddr: SAddr, cSock: SC, kill: Promise[Unit]): Future[Unit] =
    config(PreClient(cSock)) flatMap { initCl =>
      connectToServer(initCl, sAddr, kill) flatMap { cnctCl =>
        shakeHands(cnctCl) flatMap { _ =>
          listenToWire(cnctCl, kill)
          listenToUser(cnctCl, kill) } } }

  def config(cl: PreClient): Future[CfgClient] =
    getHandle(cl) flatMap { h =>
      Future.successful(
        CfgClient(cl.sock, h)) } //}

  def getHandle(cl: PreClient): Future[Array[Byte]] =
    Future successful {
        scala.io.StdIn.readLine("Welcome to scat! Please choose a handle...\n")
          .getBytes }

  def connectToServer(cl: CfgClient, sAddr: SAddr, kill: Promise[Unit]): Future[CnctClient] =
    connect(cl.sock, sAddr) flatMap { _ =>
      Future successful {
        CnctClient(cl.sock, cl.handle) } }

  def shakeHands(cl: CnctClient): Future[Unit] =
    write(cl.sock,cl.handle)
    //TODO add actual handshake exchange w/ possibility of failure

  def listenToWire(cl: CnctClient, kill: Promise[Unit]): Future[Unit] =
    read(cl.sock) flatMap { msg =>
      dispatchIn(cl, msg, kill) flatMap { cont =>
        if (cont) listenToWire(cl, kill)
        else Future successful { () } } }

  def dispatchIn(cl: CnctClient, msg: Array[Byte], kill: Promise[Unit]): Future[Boolean] =
    if (strFromWire(msg) == "exit") {
      doKill(cl,kill)
      Future successful { false }
    }
    else {
      println(s"${strFromWire(msg)}")
      Future successful { true }
    }

  def listenToUser(cl: CnctClient, kill: Promise[Unit]): Future[Unit] =
    Future successful {
      scala.io.StdIn.readLine().getBytes
    } flatMap { msg =>
      dispatchOut(cl,msg,kill) flatMap { cont =>
        if (cont) {
          listenToUser(cl, kill) }
        else {
          kill success {()}
          Future.successful(()) } } }

  def dispatchOut(cl: CnctClient, msg: Array[Byte], kill: Promise[Unit]): Future[Boolean] =
    if (strFromWire(msg) == "exit") {
      write(cl.sock, msg) flatMap { _ =>
        doKill(cl, kill)
        Future successful { false } } }
    else {
      write(cl.sock, format(cl, msg)) flatMap { _ =>
        Future successful { true } } }

  def doKill(cl: CnctClient, kill: Promise[Unit]): Future[Unit] =
    Future {
      cl.sock.close()
      println(s"Closed connection with scat server")
      kill success { () }
      () }

  def info(cl: Client): String = {
    val (handle, addr) = cl match {
      case CnctClient(s,h) => (h, s.getLocalAddress.toString)
      case RmtClient(s,h) => (h, s.getRemoteAddress.toString)
    }
    handle.map(_.toChar).mkString + " @ " + addr }

  private def format(cl: CnctClient, msg: Array[Byte]) : Array[Byte] =
      cl.handle ++ ": ".getBytes ++ msg  // ++ "\n".getBytes

}

//object ClientAccessor {
//  val humanHandle:String = handle.map{ _.toChar }.mkString
//  val info = humanHandle + " @ " + sock.getRemoteAddress.toString
//
//}