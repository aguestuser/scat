package scat

import java.net.InetSocketAddress

import scat.Socket._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Author: aguestuser
 * Date: 2/2/15.
 * License: GPLv2
 */

object RunClient extends App {

  import scat.Client._

  val (sAddr, cSock) = (new InetSocketAddress(args(0).toInt), getClientSock(args(1).toInt))
  val kill = Promise[Unit]()

  config(PreClient(cSock)) flatMap { ic =>
    connect(ic, sAddr, kill) }

  Await.result(kill.future, Duration.Inf)
}

trait Client
case class PreClient(sock: SC) extends Client
case class InitClient(sock: SC, handle: Array[Byte]) extends Client

object Client {

  def config(cl: Client): Future[Client] =
    cl match { case PreClient(sock) =>
      getHandle(cl) flatMap { h =>
        Future.successful(InitClient(sock, h)) } }

  def getHandle(cl: Client): Future[Array[Byte]] =
    cl match { case PreClient(_) =>
      Future {
        scala.io.StdIn.readLine("Welcome to scat! Please choose a handle...\n").getBytes } }

  def connect(cl: Client, sAddr: SAddr, kill: Promise[Unit]): Future[Unit] =
    cl match { case InitClient(sock, _) =>
      Socket.connect(sock, sAddr) flatMap { _ =>
        listenToWire(cl)
        listenToUser(cl, kill)
        Future.successful(()) } }

  def listenToWire(cl: Client): Future[Unit] =
    cl match { case InitClient(sock, handle) =>
      read(sock) flatMap { msg =>
        print(s"${strFromWire(msg)}\n")
        listenToWire(cl) } }

  def listenToUser(cl: Client, kill: Promise[Unit]): Future[Unit] =
    cl match { case InitClient(sock, handle) =>
      Future { scala.io.StdIn.readLine().getBytes } flatMap { msg =>
        dispatch(cl, msg) flatMap { cont =>
          if (cont) {
            listenToUser(cl, kill)
            Future.successful(()) }
          else {
            kill success {()}
            Future.successful(()) } } } }


  def dispatch(cl: Client, msg: Array[Byte]): Future[Boolean] =
    cl match { case InitClient(sock, handle) =>
      if (strFromWire(msg) == "exit") {
        Future {
          sock.close()
          println(s"Closed connection with scat server") } flatMap { _ =>
            Future.successful(false) } }
      else {
        write(format(cl, msg), sock)
        Future.successful(true) } }

  def humanHandle(cl: Client): String =
    cl match { case InitClient(sock, handle) => handle.map{ _.toChar }.mkString }

  def info(cl: Client): String =
    cl match { case InitClient(sock, handle) =>
      humanHandle(cl) + " @ " + sock.getRemoteAddress.toString }

  private def format(cl: Client, msg: Array[Byte]) : Array[Byte] =
    cl match { case InitClient(sock, handle) =>
      handle ++ ": ".getBytes ++ msg } // ++ "\n".getBytes

}

//object ClientAccessor {
//  val humanHandle:String = handle.map{ _.toChar }.mkString
//  val info = humanHandle + " @ " + sock.getRemoteAddress.toString
//
//}