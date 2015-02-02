package scat

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Author: @aguestuser
 * Date: 1/31/15
 * License: GPLv2
 */

object RunServer extends App {
  try {
    val sSock = Server.getServerSock(args(0).toInt)
    println(s"Listening on port ${sSock.getLocalAddress}")
    Server.accept(sSock)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

object Server {

  type SSC = AsynchronousServerSocketChannel
  type SC = AsynchronousSocketChannel

  var clients = Set[Client]()

  def getServerSock(port: Int): AsynchronousServerSocketChannel =
    AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port))

  def accept(sSock: SSC) : Unit = {
    val cSock = acceptOne(sSock)
    config(cSock)
    
    Await.result(cSock,  Duration.Inf)
    accept(sSock)
  }

  def acceptOne(sSock: SSC): Future[SC] = {
    val p = Promise[SC]()
    sSock.accept(null, new CompletionHandler[SC, Void] {
      def completed(client: SC, att: Void) = {
        println(s"Client connection received from ${client.getRemoteAddress}")
        p success { client }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def config(cSock: Future[SC]) =
    cSock flatMap { cs =>
      getHandle(cs) flatMap { h =>
        val cl = new Client(cs,h)
        println(s"Created new client: \n${cl.info}")
        synchronized {
          clients = clients + cl
          println(s"${clients.size} total client(s): \n${clients.map{ _.info }.mkString("\n") }")
        }
        listen(cl)
      }
    }

  def getHandle(cSock: SC): Future[Array[Byte]] = {
    val prompt = "Welcome to scat! Please choose a handle...\n".getBytes
    write(prompt, cSock) flatMap { _ =>
       read(cSock) map { msg =>
         trimCarriageReturn(msg)
       }
    }
  }

  def listen(client: Client) : Future[Set[Unit]] =
    read(client.sock) flatMap { msg =>
      relay(msg, client)} flatMap { _ =>
      println(s"Relayed message from ${client.humanHandle}")
      listen(client)
    }

  def read(cSock: SC): Future[Array[Byte]] = {
    val buf = ByteBuffer.allocate(1024) // TODO what happens to this memory allocation?
    val p = Promise[Array[Byte]]()
    cSock.read(buf, null, new CompletionHandler[Integer, Void] {
      def completed(numRead: Integer, att: Void) = {
        println(s"Read $numRead bytes")
        buf.flip()
        p success { buf.array() }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def relay(msg: Array[Byte], sender: Client): Future[Set[Unit]] =
    if (msg.map(_.toChar).mkString.trim == "exit") {
      sender.sock.close()
      synchronized { clients = clients - sender }
      Future.successful(Set(()))
    }
    else Future sequence {
      (clients - sender) map { cl => write(appendHandle(msg, sender), cl.sock) }
    }


  def write(msg: Array[Byte], receiver: SC): Future[Unit] =
    writeOnce(msg, receiver) flatMap { numwrit =>
      if (numwrit == msg.size) Future.successful(())
      else write(msg.drop(numwrit), receiver)
    }
  // TODO replace drop with slices of the array? (drop will run in O(n^2) where n is bytes being dropped)


  def writeOnce(bs: Array[Byte], receiver: SC): Future[Integer] = {
    val p = Promise[Integer]()
    receiver.write(ByteBuffer.wrap(bs), null, new CompletionHandler[Integer, Void] {
      def completed(numwrit: Integer, att: Void) = { println(s"Wrote $numwrit bytes"); p success { numwrit } }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }


  private def appendHandle(msg: Array[Byte], cl: Client) : Array[Byte] = cl.handle ++ ": ".getBytes ++ msg
  private def trimCarriageReturn(msg: Array[Byte]): Array[Byte] = msg.takeWhile( _ != (10:Byte))

}