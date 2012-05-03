package com.kalulu.sgs.swtor.towersolver.impl

import scala.collection.JavaConversions._
import com.weiglewilczek.slf4s.Logging
import com.sun.sgs.app.ManagedReference
import com.sun.sgs.app.ManagedObject
import com.sun.sgs.app.Task
import com.kalulu.sgs.swtor.towersolver.traits.Player
import com.sun.sgs.app.AppContext
import com.sun.sgs.app.ChannelListener
import com.sun.sgs.app.Delivery
import com.sun.sgs.app.util.ScalableHashSet
import java.nio.ByteBuffer
import com.sun.sgs.app.ClientSession
import com.sun.sgs.app.Channel
import org.apache.avro.ipc.specific.SpecificResponder
import com.kalulu.sgs.swtor.towersolver.protocol.MainLobby
import com.kalulu.sgs.swtor.towersolver.protocol.server.SingleServer
import com.kalulu.sgs.swtor.towersolver.traits.ServerTrait
import com.kalulu.sgs.swtor.towersolver.tasks.ServerList
import org.apache.avro.ipc.specific.SpecificContextResponder
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerStatus

class TowerSolverLobby(val serversRef : ManagedReference[ServerList]) extends ManagedObject with ChannelListener with Logging with Serializable {

  val chanRef = AppContext.getDataManager.createReference(
      AppContext.getChannelManager.createChannel("LOBBY_CHAN", this, Delivery.UNORDERED_RELIABLE)
  )
  private val usersRef = AppContext.getDataManager.createReference(new ScalableHashSet[ManagedReference[Player]])
    
  def addUser(player:ManagedReference[Player]) {
    usersRef.get.add(player)
    //chanRef.get.join(player.get.session.get)
  }
  
  def receivedMessage(chan:Channel, session:ClientSession, msg:ByteBuffer) {
    logger.debug("Received message from " + session.getName + " starting responder")
    LobbyResponder.respond(this,msg) match {
      case resp : List[ByteBuffer] => {
        resp.map({
          case(x)=>Math.max(
            x.position,
            x.limit)
          }).reduce(_+_) match {
          case 0 => logger.debug("Message from " + session.getName + " processed. (no response sent)")
          case size : Int => {
            logger.debug("Message from " + session.getName + " processed, sending response of " + size + " bytes")
            val bb = ByteBuffer.allocate(size+1)
            resp.foreach(bb.put(_))
            bb.flip()
            session.send(bb)
          }
        }
      }
      case e => logger.debug("Message from " + session.getName + " processed. Returned " + e + " (no response sent)")
    }
  }
}

class LobbyResponder extends MainLobby with Logging {
  
  override def GetServerList(ctx:Any) : java.util.List[SingleServer] = {
    logger.debug("Message to get servers processed, sending response")
    ctx.asInstanceOf[TowerSolverLobby].serversRef.get.servers.map(_.singleServer)
  }

  override def GetServerStatus(ctx:Any,server:CharSequence) = ctx.asInstanceOf[TowerSolverLobby].serversRef.get.servers.filter(_.name == server).firstOption match {
    case Some(server) => server.status
    case _ => null.asInstanceOf[ServerStatus]
  }
  
  override def SelectServer(ctx:Any,server:CharSequence) = null
  
}

object LobbyResponder {
  val RESPONDER = new SpecificContextResponder(MainLobby.PROTOCOL,new LobbyResponder())
  
  def respond(lobby:TowerSolverLobby,bb:ByteBuffer) = RESPONDER.respond(lobby,List(bb)).toList

}
