package scat

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Created by aguestuser on 1/31/15.
 */
object RunServer extends App {
  try {
    val chn = Server.getChannel(args(0).toInt)
    val socks = List[AsynchronousSocketChannel]()
    Server.listen(chn, socks)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

object Server {

  def getChannel(port: Int): AsynchronousServerSocketChannel =
    AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port))

  def listen(chn: AsynchronousServerSocketChannel, socks: List[AsynchronousSocketChannel]) : Unit = {

    println(s"Listening on port ${chn.getLocalAddress.toString}")
    if (socks.size > 0) println(s"Clients at: ${socks map {_.getLocalAddress}}")

    val newSock = accept(chn)
    Await.result(newSock, Duration.Inf)
    newSock onSuccess { case ns => echoMany(ns :: socks)}

    listen(chn, socks)
  }

  def accept(chn: AsynchronousServerSocketChannel): Future[AsynchronousSocketChannel] = {
    val p = Promise[AsynchronousSocketChannel]()
    chn.accept(null, new CompletionHandler[AsynchronousSocketChannel, Void] {
      def completed(sock: AsynchronousSocketChannel, att: Void) = {
        println(s"Client connection received from ${sock.getLocalAddress.toString}")
        p success { sock }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def echoMany(socks: List[AsynchronousSocketChannel]): List[Future[Unit]] =
    socks map { echoOne } flatMap { _ => echoMany(socks)}


  def echoOne(sock: AsynchronousSocketChannel): Future[Unit] =
    for {
      input <- read(sock)
      done <- dispatchInput(input, sock)
    } yield done

  def read(sock: AsynchronousSocketChannel): Future[Array[Byte]] = {
    val buf = ByteBuffer.allocate(1024) // TODO what happens to this memory allocation?
    val p = Promise[Array[Byte]]()
    sock.read(buf, null, new CompletionHandler[Integer, Void] {
      def completed(numRead: Integer, att: Void) = {
        println(s"Read ${numRead.toString} bytes")
        buf.flip()
        p success { buf.array() }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def dispatchInput(input: Array[Byte], sock: AsynchronousSocketChannel) : Future[Unit] = {
    if (input.map(_.toChar).mkString.trim == "exit") Future.successful(())
    else write(input,sock)
  }

  def write(bs: Array[Byte], sock: AsynchronousSocketChannel): Future[Unit] = {
    for {
      numWritten <- writeOnce(bs, sock)
      res <- dispatchWrite(numWritten, bs, sock)
    } yield res
  }

  def writeOnce(bs: Array[Byte], chn: AsynchronousSocketChannel): Future[Integer] = {
    val p = Promise[Integer]()
    chn.write(ByteBuffer.wrap(bs), null, new CompletionHandler[Integer, Void] {
      def completed(numWritten: Integer, att: Void) = {
        println(s"Echoed ${numWritten.toString} bytes")
        p success { numWritten }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def dispatchWrite(numWritten: Int, bs: Array[Byte], sock: AsynchronousSocketChannel): Future[Unit] = {
    if(numWritten == bs.size) Future.successful(())
    else write(bs.drop(numWritten), sock)
  }
}


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