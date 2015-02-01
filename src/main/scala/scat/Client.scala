//package scecho
//
//import java.net.InetSocketAddress
//import java.nio.ByteBuffer
//import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler}
//
//import scala.concurrent.duration.Duration
//import scala.concurrent.{Await, Future, Promise}
//
///**
// * Created by aguestuser on 1/31/15.
// */
//
//object RunClient extends App {
//  val server = new InetSocketAddress("localhost", 2000)
//  val client = AsynchronousSocketChannel.open()
//  Client.run(client,server)
//}
//
//object Client {
//
//  def run(client: AsynchronousSocketChannel, server: InetSocketAddress) : Unit = {
//    val cnxn = connect(client, server)
//    Await.result(cnxn, Duration.Inf)
//    cnxn onSuccess { case c => talk(c) }
//  }
//
//  def connect(client: AsynchronousSocketChannel, server: InetSocketAddress ) : Future[AsynchronousSocketChannel] = {
//    val p = Promise[AsynchronousSocketChannel]()
//    client.connect(server, null, new CompletionHandler[Void, AsynchronousSocketChannel] {
//      def completed(att: Void, cnxn: AsynchronousSocketChannel) = {
//        println(s"Connected to scat server on ${cnxn.getLocalAddress}")
//        p success { cnxn }
//      }
//      def failed(e: Throwable, att: AsynchronousSocketChannel) = { p failure { e } }
//    })
//    p.future
//  }
//
//  def talk(cnxn: AsynchronousSocketChannel) : (Future[Unit],Future[Unit]) =
//    (getMessages(cnxn), getUserInput(cnxn))
//
//  def getMessages(cnxn: AsynchronousSocketChannel) : Future[Unit] = {
//    for {
//      msg <- readWire(cnxn)
//      done <- writeToConsole(msg)
//    } yield getMessages(cnxn)
//  }
//
//  def readWire(cnxn): Future[Array[Byte]] = {
//    val buf = ByteBuffer.allocate(1024) // TODO what happens to this memory allocation?
//    val p = Promise[Array[Byte]]()
//    cnxn.read(buf, null, new CompletionHandler[Integer, Void] {
//      def completed(numRead: Integer, att: Void) = {
//        println(s"Read ${numRead.toString} bytes")
//        buf.flip()
//        p success { buf.array() }
//      }
//      def failed(e: Throwable, att: Void) = p failure { e }
//    })
//    p.future
//  }
//
//  def getUserInput(cnxn: AsynchronousSocketChannel) : Future[Unit] =
//    { for {
//      msg <- readConsole
//      done <- writeToWire(msg, cnxn)
//    } yield done } flatMap { _ => getUserInput(cnxn) }
//
//  def readConsole: Future[Array[Byte]] = Future{ scala.io.StdIn.readLine().getBytes }
//
//  def writeToWire(msg: Array[Byte], cnxn: AsynchronousSocketChannel): Future[Unit] = {
//    for {
//      numWritten <- writeToWireOnce(bs, cnxn)
//      res <- dispatchWriteToWire(numWritten, bs, cnxn)
//    } yield res
//  }
//
//  def writeToWireOnce(bs: Array[Byte], chn: AsynchronousSocketChannel): Future[Integer] = {
//    val p = Promise[Integer]()
//    chn.write(ByteBuffer.wrap(bs), null, new CompletionHandler[Integer, Void] {
//      def completed(numWritten: Integer, att: Void) = {
//        println(s"Echoed ${numWritten.toString} bytes")
//        p success { numWritten }
//      }
//      def failed(e: Throwable, att: Void) = p failure { e }
//    })
//    p.future
//  }
//
//  def dispatchWriteToWire(numWritten: Int, bs: Array[Byte], cnxn: AsynchronousSocketChannel): Future[Unit] = {
//    if(numWritten == bs.size) Future.successful(())
//    else writeToWire(bs.drop(numWritten), cnxn)
//  }
//
//}
