package com.kalulu.sgs.swtor.towersolver

import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.util.Properties
import scala.annotation.serializable
import com.kalulu.sgs.swtor.towersolver.tasks.ServerList
import com.kalulu.sgs.swtor.towersolver.tasks.UpdateServerTask
import com.kalulu.sgs.swtor.towersolver.traits.PersistenceManager
import com.kalulu.sgs.swtor.towersolver.traits.Player
import com.sun.sgs.app.util.ScalableDeque
import com.sun.sgs.app.AppContext
import com.sun.sgs.app.AppListener
import com.sun.sgs.app.Channel
import com.sun.sgs.app.ChannelListener
import com.sun.sgs.app.ClientSession
import com.sun.sgs.app.ClientSessionListener
import com.sun.sgs.app.Delivery
import com.sun.sgs.app.ManagedReference
import com.sun.sgs.app.Task
import com.weiglewilczek.slf4s.Logging
import TowerSolver.CHANNEL_1_NAME
import TowerSolver.CHANNEL_2_NAME
import com.kalulu.sgs.swtor.towersolver.tasks.LoginUserTask
import com.kalulu.sgs.swtor.towersolver.impl.TowerSolverLobby

@serializable
class TowerSolver extends AppListener with Task with Logging {
  private var loginCount = 0
  private var channel1 : ManagedReference[Channel] = null
  private var servers : ManagedReference[List[String]] = null
  private var loginQueueRef : ManagedReference[ScalableDeque[ClientSession]] = null
  private var lobbyRef : ManagedReference[TowerSolverLobby] = null

  private var persistence : PersistenceManager = null
  import TowerSolver._
  
  override def initialize(props:Properties) {
     
    val taskManager = AppContext.getTaskManager
    val chanManager = AppContext.getChannelManager
    val dataManager = AppContext.getDataManager
    
    val c1 = chanManager.createChannel(CHANNEL_1_NAME, null, Delivery.RELIABLE)
    channel1 = dataManager.createReference(c1)
    chanManager.createChannel(CHANNEL_2_NAME, new TowerSolverChannelListener, Delivery.RELIABLE)
  
    val servers = ServerList()
    val serversRef = dataManager.createReference(servers)

    // TODO - Use an array of queues if you see contention here
    val loginQueue = new ScalableDeque[ClientSession]()
    loginQueueRef = dataManager.createReference(loginQueue)
    
    taskManager.schedulePeriodicTask(new UpdateServerTask(serversRef),5000,5000)
    
    val lobby = new TowerSolverLobby(serversRef)
    lobbyRef = AppContext.getDataManager.createReference(lobby)
    
    taskManager.scheduleTask(new LoginUserTask(loginQueueRef,lobbyRef))
  }

  override def loggedIn(session:ClientSession) : ClientSessionListener = {
    logger.info("User " + session.getName + " has almost logged in")
    loginQueueRef.getForUpdate.add(session)
    new TowerUserSessionListener(session)
  }

  override def run : Unit = {

  }

}

object TowerSolver {
  final val CHANNEL_1_NAME = "Foo"
  final val CHANNEL_2_NAME = "Bar"
    
  private final val DELAY_MS  = 5000
  private final val PERIOD_MS = 5000
}

class TowerUserSessionListener(session:ManagedReference[ClientSession],private val name:String) extends ClientSessionListener with Logging with Serializable {
  def this(session:ClientSession) = this(AppContext.getDataManager().createReference(session),session.getName)
  final val MESSAGE_CHARSET = "UTF-8"
    
  override def receivedMessage(message:ByteBuffer) = {
    val s = decode(message)
    logger.info(s)
    session.get.send(encode(s))
  }
  
  override def disconnected(graceful:Boolean) = {
    val g = if(graceful) "gracefully" else "forced"
    logger.info("User " + name + " has logged out " + g)
  }

  def decode(b:ByteBuffer) = {
    try {
      val bytes = new Array[Byte](b.remaining)
      b.get(bytes)
      new String(bytes, MESSAGE_CHARSET)
    } catch {
      case e: UnsupportedEncodingException => throw new Error("Required character set " + MESSAGE_CHARSET + " not found", e)
    }
  }
  def encode(s:String) = {
    try {
      ByteBuffer.wrap(s.getBytes())
    } catch {
      case e: UnsupportedEncodingException => throw new Error("Required character set " + MESSAGE_CHARSET + " not found", e)
    }
  }
  
}

@serializable
class TowerSolverChannelListener extends ChannelListener with Logging {
  override def receivedMessage(channel:Channel,session:ClientSession,message:ByteBuffer) = {
   logger.info("Channel message from " + session.getName + " on " + channel.getName)
   channel.send(session,message)
  }
}
