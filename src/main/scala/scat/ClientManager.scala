//package scat
//
//import java.util.Date
//
//import scat.Socket._
//import scat.Server._
//import scala.concurrent.Future
//
///**
// * Created by aguestuser on 2/4/15.
// */
//trait ClientManager {
//
//  def configClient(cSock: SC, clients: Client) : Future[Client] = {
//    getHandle(cSock) flatMap { h =>
//      createClient(cSock, h) flatMap { cl =>
//        scat.Server.log(new Date(), s"Created new client: \n${cl.info}\n" +
//          s"${this.clients.size} total client(s): \n" +
//          s"${clients.keys.map{ _.info }.mkString("\n") }") map { _ =>
//          cl
//        }
//      }
//    }
//  }
//
//  def getHandle(cSock: SC): Future[Array[Byte]] = {
//    val prompt = "Welcome to scat! Please choose a handle...\n".getBytes
//    write(prompt, cSock) flatMap { _ =>
//      read(cSock) map { msg =>
//        trimByteArray(msg) }
//    }
//  }
//
//  def createClient(cSock: SC, handle: Array[Byte]) : Future[Client] =
//    Future {
//      val cl = new Client(cSock,handle)
//      clients putIfAbsent(cl, true)
//      cl
//    }
//}
