package scat

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Author: Austin Guest
 * Date: 1/31/15
 * License: GPL2
 */

object RunServer extends App {
  try {
    val serverSock = Server.getServerSock(args(0).toInt)
    val clientSocks = List[AsynchronousSocketChannel]()
    Server.listenForSocks(serverSock, clientSocks)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

object Server {

  type SSC = AsynchronousServerSocketChannel
  type SC = AsynchronousSocketChannel

  def getServerSock(port: Int): AsynchronousServerSocketChannel =
    AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port))

  def listenForSocks(sSock: SSC, socks: List[SC]) : Unit = {

    println(s"Listening on port ${sSock.getLocalAddress.toString}")
    if (socks.size > 0) println(s"Clients at: ${socks map {_.getLocalAddress}}")

    val newSock = acceptSock(sSock)
    newSock onSuccess { case ns => listenToSocks(ns :: socks)}

    listenForSocks(sSock, Await.result(newSock, Duration.Inf) :: socks )
  }

  def acceptSock(sSock: SSC): Future[SC] = {
    val p = Promise[SC]()
    sSock.accept(null, new CompletionHandler[SC, Void] {
      def completed(sock: SC, att: Void) = {
        println(s"Client connection received from ${sock.getRemoteAddress}")
        p success { sock }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def listenToSocks(socks: List[SC]): List[Future[List[Unit]]] =
    socks map { s => listenToSock(s, socks) }
  
  def listenToSock(sock: SC, socks: List[SC]) : Future[List[Unit]] =
    read(sock) flatMap { msg =>
      relay(msg, sock, socks) flatMap { _ =>
        listenToSock(sock, socks) } }

  def read(sock: SC): Future[Array[Byte]] = {
    val buf = ByteBuffer.allocate(1024) // TODO what happens to this memory allocation?
    val p = Promise[Array[Byte]]()
    sock.read(buf, null, new CompletionHandler[Integer, Void] {
      def completed(numRead: Integer, att: Void) = {
        println(s"Read $numRead bytes")
        buf.flip()
        p success { buf.array() }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def relay(msg: Array[Byte], sock: SC, socks: List[SC]): Future[List[Unit]] =
    if (msg.map(_.toChar).mkString.trim == "exit") {
      sock.close()
      Future.successful(List(())) }
    else Future sequence {
      socks map { s =>
        write(msg, s) } }
  
  def write(msg: Array[Byte], sock: SC): Future[Unit] =
    writeOnce(msg, sock) flatMap { numwrit =>
      if(numwrit == msg.size) Future.successful(())
      else write(msg.drop(numwrit), sock)
    }

  def writeOnce(bs: Array[Byte], sock: SC): Future[Integer] = {
    val p = Promise[Integer]()
    sock.write(ByteBuffer.wrap(bs), null, new CompletionHandler[Integer, Void] {
      def completed(numwrit: Integer, att: Void) = { println(s"Relayed $numwrit bytes"); p success { numwrit } } 
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }
}


//  def send(msg: Array[Byte], sock: SC) : Future[Unit] =


//  def routeInput(input: Array[Byte], sock: SC, socks: List[SC]) : Future[List[Unit]] = {
//    if (input.map(_.toChar).mkString.trim == "exit"){ sock.close(); Future.successful(List(())) }
//    else writeToAllSocks(input,sock, socks)
//  }
//
//  def getMessage(sock: SC) : Future[Array[Byte]] = read(sock)

//  def relayMessage(sock: SC, socks: List[SC]): Future[List[Unit]] = {
//    { for {
//      input <- read(sock)
//      dones <- routeInput(input, sock, socks)
//    } yield dones } flatMap { _ => relayMessage(sock, socks) }
//  }

//  def relayMessage(msg: Array[Byte], socks: List[SC])


/* stack trace:
java.nio.channels.ReadPendingException
  at sun.nio.ch.AsynchronousSocketChannelImpl.read(AsynchronousSocketChannelImpl.java:250)
  at sun.nio.ch.AsynchronousSocketChannelImpl.read(Asynchrono usSocketChannelImpl.java:296)
  at java.nio.channels.AsynchronousSocketChannel.read(Asynchr onousSocketChannel.java:407)
  at scat.Server$.read(Server.scala:71)
  at scat.Server$.echoOne(Server.scala:62)
  at scat.Server$$anonfun$echoMany$1.apply(Server.scala:57)
  at scat.Server$$anonfun$echoMany$1.apply(Server.scala:57)
  at scala.collection.immutable.List.map(List.scala:272)
  at scat.Server$.echoMany(Server.scala:57)
  at scat.Server$$anonfun$echoMany$2.apply(Server.scala:57)
  at scat.Server$$anonfun$echoMany$2.apply(Server.scala:57)
  at scala.collection.immutable.List.flatMap(List.scala:327)
  at scat.Server$.echoMany(Server.scala:57)
  at scat.Server$$anonfun$listen$1.applyOrElse(Server.scala:3 8)
  at scat.Server$$anonfun$listen$1.applyOrElse(Server.scala:3 6)
  at scala.concurrent.Future$$anonfun$onSuccess$1.apply(Futur e.scala:117)
  at scala.concurrent.Future$$anonfun$onSuccess$1.apply(Futur e.scala:115)
  at scala.concurrent.impl.CallbackRunnable.run(Promise.scala :32)
  at scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJo inTask.exec(ExecutionContextImpl.scala:121)
  at scala.concurrent.forkjoin.ForkJoinTask.doExec(ForkJoinTa sk.java:260)
  at scala.concurrent.forkjoin.ForkJoinPool$WorkQueue.runTask (ForkJoinPool.java:1339)
  at scala.concurrent.forkjoin.ForkJoinPool.runWorker(ForkJoi nPool.java:1979)
  at scala.concurrent.forkjoin.ForkJoinWorkerThread.run(ForkJ oinWorkerThread.java:107)
* */