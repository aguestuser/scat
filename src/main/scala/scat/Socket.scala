package scat

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousSocketChannel, AsynchronousServerSocketChannel, CompletionHandler}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future}

/**
 * Author: @aguestuser
 * Date: 2/2/15
 * License: GPLv2
 */

object Socket {

  type SSC = AsynchronousServerSocketChannel
  type SC = AsynchronousSocketChannel
  type SAddr = InetSocketAddress

  def getServerSock(port: Int): SSC =
    AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port))

  def getClientSock(port: Int): SC =
    AsynchronousSocketChannel.open().bind(new InetSocketAddress(port))

  def accept(sSock: SSC): Future[SC] = {
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

  def connect(sock: SC, addr: InetSocketAddress): Future[Unit] = {
    val p = Promise[Unit]()
    sock.connect(addr, null, new CompletionHandler[Void,Void] {
      def completed(done: Void, att: Void) = {
        println(s"Connected to remote socket at $addr")
        p success { () }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
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

  def write(msg: Array[Byte], receiver: SC): Future[Unit] =
    writeOnce(msg, receiver) flatMap { numwrit =>
      if (numwrit == msg.size) Future.successful(())
      else write(msg.drop(numwrit), receiver)
    }
  // TODO replace drop with slices of the array? (drop will run in O(n^2) where n is bytes being dropped)


  private def writeOnce(bs: Array[Byte], receiver: SC): Future[Integer] = {
    val p = Promise[Integer]()
    receiver.write(ByteBuffer.wrap(bs), null, new CompletionHandler[Integer, Void] {
      def completed(numwrit: Integer, att: Void) = { println(s"Wrote $numwrit bytes"); p success { numwrit } }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }
}
